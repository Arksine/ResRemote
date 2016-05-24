/**
 * TouchManager.cpp
 *
 * TouchManager implementation
 */
#include "TouchManager.h"


// *************END IWRAP HELPER PROTOTYPES **********************

TouchManager::TouchManager() : ts(XP, YP, XM, YM, XPLATE), touchDevice(),
  storageManager()
{
  storage      = storageManager.getStorage();
  loopDelay    = 0;
  touchDelay   = 0;
  isTouching   = false;
  isStarted    = storageManager.isConfigValid();
  iwrapMode    = IWRAP_MODE_MUX;
  rfCommLinkId = 0xFE;

  // Turn on LED to notify user that device needs calibration / is in
  // calibration mode if necessary
  if (!isStarted) {
    digitalWrite(LED_PIN, HIGH);
  } else {
    digitalWrite(LED_PIN, LOW);
  }
}

TouchManager::~TouchManager() {}

void TouchManager::setHidLinkId(uint8_t linkId) {
  touchDevice.setHidLinkId(linkId);
}

void TouchManager::setrfCommLinkId(uint8_t linkId) {
  rfCommLinkId = linkId;
}

void TouchManager::setMacAddress(const iwrap_address_t *mac) {
  memcpy(storage->macAddress.address, mac->address, 6);
  storageManager.updateConfiguration();
}

iwrap_address_t * TouchManager::getMacAddress() {
  return &(storage->macAddress);
}

void TouchManager::checkForTouch() {
  if (isStarted && ((millis() - loopDelay) >= READ_LOOP_DELAY)) {
    // If we aren't accepting serial data, process touch data
    loopDelay = millis();
    TSPoint p = ts.getPoint();

    if ((p.z > MIN_PRESSURE) && (p.z < MAX_PRESSURE)) {
      sendHidCoordinate(p.x, p.y, p.z);
      isTouching = true;
      touchDelay = millis();
    } else if (isTouching && ((millis() - touchDelay) >= TOUCH_UP_DELAY)) {
      // I might need to adjust the touch up delay, 50 - 100ms should work
      // TODO: need to create a class that manages Bluetooth HID reports
      // and sends the correct report over bluetooth
      touchDevice.release();
      isTouching = false;
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

  dZ = MAX_PRESSURE - (tZ - storage->minResistance);

  touchDevice.moveTo(dX, dY);
}

void TouchManager::processCommand(String command) {
  if (command == "") {
    // no command received
    return;
  } else if (command == "CAL_POINT") {
    // Get a single point from the touch screen and send it

    // writeToSerial("<LOG:GET_POINT_OK>");

    // We have a calibration command, so stop sending hid coordinates
    if (isStarted) {
      isStarted = false;
      digitalWrite(LED_PIN, HIGH);
    }
    getPoint();
  } else if (command == "CAL_PRESSURE") {
    // Get points until the user lifts their finger

    // writeToSerial("<LOG:GET_PRESSURE_OK>");

    // We have a calibration command, so stop sending hid coordinates
    if (isStarted) {
      isStarted = false;
      digitalWrite(LED_PIN, HIGH);
    }
    delay(500);
    getPressure();
  } else if (command == "CAL_FAIL") {
    // if we have some kind of valid configuration, restart sending hid reports
    isStarted = storageManager.isConfigValid();

    if (isStarted) {
      digitalWrite(LED_PIN, LOW);
    }
  } else if (command == "CAL_SUCCESS") {
    storageManager.updateConfiguration();
    isStarted = true;
    digitalWrite(LED_PIN, LOW);
  } else if (command == "ERROR") {
    // if we have some kind of valid configuration, restart sending hid reports
    isStarted = storageManager.isConfigValid();

    if (isStarted) {
      digitalWrite(LED_PIN, LOW);
    }
  }
  else if (command.startsWith("$")) {
    // this is a command to store a calibration variable, we don't need to
    // send it the control character
    setStorageVariables(command.substring(1));
  } else if (command.startsWith("SET_ROTATION")) {
    storage->rotation = atoi(command.substring(13).c_str());
    writeToSerial("<LOG:OK>");
    String tmp = "<LOG:Rotation=" + String(storage->rotation) + ">";
    writeToSerial(tmp);
  } else {
    // unknown command
    String logString = command;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:UKNOWN COMMAND " + logString + ">";
    writeToSerial(logString);
  }
}

void TouchManager::setStorageVariables(String data) {
  String varType;
  String value;
  String out;

  varType = data.substring(0, 1);
  value   = data.substring(2);

  if (varType == "A") {
    storage->A = atol(value.c_str());
    writeToSerial("<LOG:OK>");
    out = "<LOG:A=" + String(storage->A) + ">";
    writeToSerial(out);
  } else if (varType == "B") {
    storage->B = atol(value.c_str());
    writeToSerial("<LOG:OK>");
    out = "<LOG:B=" + String(storage->B) + ">";
    writeToSerial(out);
  } else if (varType == "C") {
    storage->C = atol(value.c_str());
    writeToSerial("<LOG:OK>");
    out = "<LOG:C=" + String(storage->C) + ">";
    writeToSerial(out);
  } else if (varType == "D") {
    storage->D = atol(value.c_str());
    writeToSerial("<LOG:OK>");
    out = "<LOG:D=" + String(storage->D) + ">";
    writeToSerial(out);
  } else if (varType == "E") {
    storage->E = atol(value.c_str());
    writeToSerial("<LOG:OK>");
    out = "<LOG:E=" + String(storage->E) + ">";
    writeToSerial(out);
  } else if (varType == "F") {
    storage->F = atol(value.c_str());
    writeToSerial("<LOG:OK>");
    out = "<LOG:F=" + String(storage->F) + ">";
    writeToSerial(out);
  } else if (varType == "M") {
    storage->minResistance = atoi(value.c_str());
    writeToSerial("<LOG:OK>");
    out = "<LOG:M=" + String(storage->minResistance) + ">";
    writeToSerial(out);
  } else {
    // invalid command
    String logString = data;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:PACKET " + logString + " invalid>";
    writeToSerial(logString);
  }
}

void TouchManager::getPoint() {
  bool pointRecd = false;

  while (!pointRecd) {
    TSPoint p = ts.getPoint();

    if ((p.z > MIN_PRESSURE) && (p.z < MAX_PRESSURE)) {
      String point = "<CAL:" +
                     String(p.x) + ":" +
                     String(p.y) + ":" +
                     String(p.z) + ">";
      writeToSerial(point);
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

    if ((p.z > MIN_PRESSURE) && (p.z < MAX_PRESSURE)) {
      String point = "<CAL:" +
                     String(p.x) + ":" +
                     String(p.y) + ":" +
                     String(p.z) + ">";
      writeToSerial(point);
      isTouching = true;
      touchDelay = millis();
    } else if (isTouching && ((millis() - touchDelay) >= TOUCH_UP_DELAY)) {
      touchUp    = true;
      isTouching = false;
      writeToSerial("<STOP:0:0:0>");
    }

    delay(10);
  }
}

/**
 * Writes a given string to the connected bluetooth device. The trailing null
 * character is left out, as the serial "write"function is used rather than
 * print.
 * @param strToWrite [String to write]
 */
void TouchManager::writeToSerial(String strToWrite) {
  uint8_t length   = strToWrite.length();
  const char *data = strToWrite.c_str();

  iwrap_send_data(rfCommLinkId, length, (const uint8_t *)data, iwrapMode);
}
