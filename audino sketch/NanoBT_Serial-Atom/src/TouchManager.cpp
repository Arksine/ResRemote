/**
 * TouchManager.cpp
 *
 * TouchManager implementation
 */
#include "TouchManager.h"

TouchManager::TouchManager() : ts(XP, YP, XM, YM, XPLATE), mySerial(),
  storageManager() {
  storage      = storageManager.getStorage();
  loopDelay    = 0;
  touchDelay   = 0;
  isTouching   = false;
  start        = false;
  serialBuffer = "";
}

TouchManager::~TouchManager() {}

void TouchManager::startSerial(long speed) {
  mySerial.begin(speed);
  digitalWrite(LEDPIN, HIGH);
}

void TouchManager::loopFunction() {
  checkSerial();

  if (start && ((millis() - loopDelay) >= READLOOPDELAY)) {
    // If we aren't accepting serial data, process touch data
    loopDelay = millis();
    TSPoint p = ts.getPoint();

    if ((p.z > MINPRESSURE) && (p.z < MAXPRESSURE)) {
      sendSerialCoordinate(p.x, p.y, p.z);
      isTouching = true;
      touchDelay = millis();
    } else if (isTouching && ((millis() - touchDelay) >= TOUCHUPDELAY)) {
      // I might need to adjust the touch up delay, 50 - 100ms should work
      mySerial.print("<UP:0:0:0>");
      isTouching = false;
    }
  }
}

void TouchManager::checkSerial() {
  if (mySerial.available() > 0)
  {
    char ch = mySerial.read();

    if (ch == '<') {
      // packet is beginning, clear buffer
      serialBuffer = "";
    }
    else if (ch == '>') {
      // end of packet, parse it
      processCommand(serialBuffer);
    }
    else {
      // part of the stream, add it to the buffer
      serialBuffer += ch;
    }
  }
}

void TouchManager::sendSerialCoordinate(int tX, int tY, int tZ) {
  int dX;
  int dY;
  int dZ;

  dX = ((storage->A * tX) + (storage->B * tY) + storage->C + 5000l) / 10000l;
  dY = ((storage->D * tX) + (storage->E * tY) + storage->F + 5000l) / 10000l;
  dZ = MAXPRESSURE - (tZ - storage->minResistance);

  mySerial.print("<DOWN:");
  mySerial.print(dX);
  mySerial.print(":");
  mySerial.print(dY);
  mySerial.print(":");
  mySerial.print(dZ);
  mySerial.print(">");
}

void TouchManager::processCommand(String command) {
  if (command == "") {
    // no command received
    return;
  } else if (serialBuffer == "START") {
    // start main loop
    start = true;
    digitalWrite(LEDPIN, LOW);
  } else if (serialBuffer == "STOP") {
    start = false;
    digitalWrite(LEDPIN, HIGH);
  } else if (command == "CAL_POINT") {
    // Get a single point from the touch screen and send it

    // mySerial.print("<LOG:GET_POINT_OK>");
    getPoint();
  } else if (command == "CAL_PRESSURE") {
    // Get points until the user lifts their finger

    // mySerial.print("<LOG:GET_PRESSURE_OK>");
    delay(500);
    getPressure();
  } else if (command == "TOGGLE_TS") {
    // Toggles the touch screen switcher
    // TODO: Bring whichever pin is connected to touchscreen to high here
  } else if (command == "CAL_FAIL") {
    // break calibration loop
    return;
  } else if (command == "CAL_SUCCESS") {
    storageManager.updateConfiguration();
  } else if (command == "ERROR") {
    return;
  } else if (command.startsWith("$")) {
    // this is a command to store a calibration variable, we don't need to
    // send it the control character
    setStorageVariables(command.substring(1));
  } else if (command.startsWith("SET_ROTATION")) {
    storage->rotation = atoi(command.substring(13).c_str());
    mySerial.print("<LOG:OK>");
    mySerial.print("<LOG:Rotation=");
    mySerial.print(storage->rotation);
    mySerial.print(">");
  } else {
    // unknown command
    String logString = command;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:UKNOWN COMMAND " + logString + ">";
    mySerial.print(logString);
  }
}

void TouchManager::setStorageVariables(String data) {
  String varType;
  String value;

  varType = data.substring(0, 1);
  value   = data.substring(2);

  if (varType == "A") {
    storage->A = atol(value.c_str());
    mySerial.print("<LOG:OK>");
    mySerial.print("<LOG:A=");
    mySerial.print(storage->A);
    mySerial.print(">");
  } else if (varType == "B") {
    storage->B = atol(value.c_str());
    mySerial.print("<LOG:OK>");
    mySerial.print("<LOG:B=");
    mySerial.print(storage->B);
    mySerial.print(">");
  } else if (varType == "C") {
    storage->C = atol(value.c_str());
    mySerial.print("<LOG:OK>");
    mySerial.print("<LOG:C=");
    mySerial.print(storage->C);
    mySerial.print(">");
  } else if (varType == "D") {
    storage->D = atol(value.c_str());
    mySerial.print("<LOG:OK>");
    mySerial.print("<LOG:D=");
    mySerial.print(storage->D);
    mySerial.print(">");
  } else if (varType == "E") {
    storage->E = atol(value.c_str());
    mySerial.print("<LOG:OK>");
    mySerial.print("<LOG:E=");
    mySerial.print(storage->E);
    mySerial.print(">");
  } else if (varType == "F") {
    storage->F = atol(value.c_str());
    mySerial.print("<LOG:OK>");
    mySerial.print("<LOG:F=");
    mySerial.print(storage->F);
    mySerial.print(">");
  } else if (varType == "M") {
    storage->minResistance = atoi(value.c_str());
    mySerial.print("<LOG:OK>");
    mySerial.print("<LOG:minResistance=");
    mySerial.print(storage->minResistance);
    mySerial.print(">");
  } else {
    // invalid command
    String logString = data;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:PACKET " + logString + " invalid>";
    mySerial.print(logString);
  }
}

void TouchManager::getPoint() {
  bool pointRecd = false;

  while (!pointRecd) {
    TSPoint p = ts.getPoint();

    if ((p.z > MINPRESSURE) && (p.z < MAXPRESSURE)) {
      mySerial.print("<CAL:");
      mySerial.print(p.x);
      mySerial.print(":");
      mySerial.print(p.y);
      mySerial.print(":");
      mySerial.print(p.z);
      mySerial.print(">");
      pointRecd = true;
    }
    delay(10);
  }
}

void TouchManager::getPressure() {
  bool touchUp = false;

  isTouching = false;

  while (!touchUp) {
    TSPoint p = ts.getPoint();

    if ((p.z > MINPRESSURE) && (p.z < MAXPRESSURE)) {
      mySerial.print("<CAL:");
      mySerial.print(p.x);
      mySerial.print(":");
      mySerial.print(p.y);
      mySerial.print(":");
      mySerial.print(p.z);
      mySerial.print(">");
      isTouching = true;
      touchDelay = millis();
    } else if (isTouching && ((millis() - touchDelay) >= TOUCHUPDELAY)) {
      touchUp    = true;
      isTouching = false;
      mySerial.print("<STOP:0:0:0>");
    }

    delay(10);
  }
}
