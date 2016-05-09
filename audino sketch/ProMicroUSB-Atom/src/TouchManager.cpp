/**
 * TouchManager.cpp
 *
 * TouchManager implementation
 */
#include "TouchManager.h"
#include "HID-Project.h"
#include "HID-Settings.h"

TouchManager::TouchManager() : ts(XP, YP, XM, YM, XPLATE), serialManager(),
  storageManager() {
  storage    = storageManager.getStorage();
  loopDelay  = 0;
  touchDelay = 0;
  isTouching = false;

  // If the configuration isnt valid, the device hasn't been calibrated. Start
  // in serial mode.
  if (!storageManager.isConfigValid()) {
    serialManager.startSerial();
  }
}

TouchManager::~TouchManager() {}

void TouchManager::loopFunction() {
  // Toggle Serial On/Off when CALPIN is brought high
  if (digitalRead(CALPIN) == HIGH) {
    // The switch is momentary, so wait for the pin to go low before
    // continuing
    while (digitalRead(CALPIN) == HIGH) {
      delay(10);
    }

    if (serialManager.isConnected()) {
      serialManager.endSerial();
      SingleMultiTouch.begin();
    } else {
      SingleMultiTouch.end();
      serialManager.startSerial();
    }
  }

  if (serialManager.isConnected()) {
    // Check serial for a command.  Process command ignores null strings
    processCommand(serialManager.checkSerial());
  } else if ((millis() - loopDelay) >= READLOOPDELAY) {
    // If we aren't accepting serial data, process touch data
    loopDelay = millis();
    TSPoint p = ts.getPoint();

    if ((p.z > MINPRESSURE) && (p.z < MAXPRESSURE)) {
      sendHidCoordinate(p.x, p.y, p.z);
      isTouching = true;
      touchDelay = millis();
    } else if (isTouching && ((millis() - touchDelay) >= TOUCHUPDELAY)) {
      // I might need to adjust the touch up delay, 50 - 100ms should work
      isTouching = false;
      SingleMultiTouch.release();
    }
  }
}

void TouchManager::sendHidCoordinate(int tX, int tY, int tZ) {
  int dX;
  int dY;
  int dZ;

  switch (storage->rotation) {
  case ROTATION_0:
    dX = ((storage->A * tX) + (storage->B * tY) + storage->C + 5000l) / 10000l;
    dY = ((storage->D * tX) + (storage->E * tY) + storage->F + 5000l) / 10000l;
    break;

  case ROTATION_90:
    dY = ((storage->A * tX) + (storage->B * tY) + storage->C + 5000l) / 10000l;
    dX = 10000 -
         (((storage->D * tX) + (storage->E * tY) + storage->F + 5000l) / 10000l);
    break;

  case ROTATION_180:
    dX = 10000 - ((storage->A * tX) + (storage->B * tY) + storage->C + 5000l) /
         10000l;
    dY = 10000 - ((storage->D * tX) + (storage->E * tY) + storage->F + 5000l) /
         10000l;
    break;

  case ROTATION_270:
    dY = 10000 -
         (((storage->A * tX) + (storage->B * tY) + storage->C + 5000l) / 10000l);
    dX = ((storage->D * tX) + (storage->E * tY) + storage->F + 5000l) / 10000l;
    break;

  default:
    dX = ((storage->A * tX) + (storage->B * tY) + storage->C + 5000l) / 10000l;
    dY = ((storage->D * tX) + (storage->E * tY) + storage->F + 5000l) / 10000l;
  }

  dZ = MAXPRESSURE - (tZ - storage->minResistance);

  SingleMultiTouch.moveTo(dX, dY);
}

void TouchManager::processCommand(String command) {
  if (command == "") {
    // no command received
    return;
  } else if (command == "CAL_POINT") {
    // Get a single point from the touch screen and send it

    // Serial.print("<LOG:GET_POINT_OK>");
    getPoint();
  } else if (command == "CAL_PRESSURE") {
    // Get points until the user lifts their finger

    // Serial.print("<LOG:GET_PRESSURE_OK>");
    delay(500);
    getPressure();
  } else if (command == "TOGGLE_TS") {
    // Toggles the touch screen switcher
    // TODO: Bring whichever pin is connected to touchscreen to high here
  } else if (command == "CAL_FAIL") {
    // break calibration loop
    serialManager.endSerial();
  } else if (command == "CAL_SUCCESS") {
    storageManager.updateConfiguration();
    serialManager.endSerial();
  } else if (command == "ERROR") {
    serialManager.endSerial();
  } else if (command.startsWith("$")) {
    // this is a command to store a calibration variable, we don't need to
    // send it the control character
    setStorageVariables(command.substring(1));
  } else if (command.startsWith("SET_ROTATION")) {
    storage->rotation = atoi(command.substring(13).c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:Rotation=");
    Serial.print(storage->rotation);
    Serial.print(">");
  } else {
    // unknown command
    String logString = command;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:UKNOWN COMMAND " + logString + ">";
    Serial.print(logString);
  }
}

void TouchManager::setStorageVariables(String data) {
  String varType;
  String value;

  varType = data.substring(0, 1);
  value   = data.substring(2);

  if (varType == "A") {
    storage->A = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:A=");
    Serial.print(storage->A);
    Serial.print(">");
  } else if (varType == "B") {
    storage->B = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:B=");
    Serial.print(storage->B);
    Serial.print(">");
  } else if (varType == "C") {
    storage->C = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:C=");
    Serial.print(storage->C);
    Serial.print(">");
  } else if (varType == "D") {
    storage->D = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:D=");
    Serial.print(storage->D);
    Serial.print(">");
  } else if (varType == "E") {
    storage->E = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:E=");
    Serial.print(storage->E);
    Serial.print(">");
  } else if (varType == "F") {
    storage->F = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:F=");
    Serial.print(storage->F);
    Serial.print(">");
  } else if (varType == "M") {
    storage->minResistance = atoi(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:minResistance=");
    Serial.print(storage->minResistance);
    Serial.print(">");
  } else {
    // invalid command
    String logString = data;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:PACKET " + logString + " invalid>";
    Serial.print(logString);
  }
}

void TouchManager::getPoint() {
  bool pointRecd = false;

  while (!pointRecd) {
    TSPoint p = ts.getPoint();

    if ((p.z > MINPRESSURE) && (p.z < MAXPRESSURE)) {
      Serial.print("<CAL:");
      Serial.print(p.x);
      Serial.print(":");
      Serial.print(p.y);
      Serial.print(":");
      Serial.print(p.z);
      Serial.print(">");
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
      Serial.print("<CAL:");
      Serial.print(p.x);
      Serial.print(":");
      Serial.print(p.y);
      Serial.print(":");
      Serial.print(p.z);
      Serial.print(">");
      isTouching = true;
      touchDelay = millis();
    } else if (isTouching && ((millis() - touchDelay) >= TOUCHUPDELAY)) {
      touchUp    = true;
      isTouching = false;
      Serial.print("<STOP:0:0:0>");
    }

    delay(10);
  }
}

void TouchManager::blinkLED() {
  if (!serialManager.isConnected()) {
    // don't blink if the serial manager is not connected
    return;
  }

  for (int i = 0; i < 5; i++) {
    delay(200);
    digitalWrite(LEDPIN, LOW);
    delay(200);
    digitalWrite(LEDPIN, HIGH);
  }
}
