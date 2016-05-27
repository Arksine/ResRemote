/**
 * BluetoothHID-atom
 *
 * Reads x, y and z coordinates from a resistive touch screen, then sends them
 * to a device via a Bluegiga HID radio
 */

#include <arduino.h>
#include "IwrapManager.h"
#include "IwrapCallbacks.h"

void setup() {
  // Set up LED pin (will turn on when device needs configuration or is
  // in configuration mode)
  pinMode(LED_PIN, OUTPUT);

  // Set up touch screen toggle pin.  When its pulsed, It will send a signal
  // on another pin to toggle the touch screen switcher between
  // TODO:  Implement this functionality later.  We need to actually test
  // iwrap/hid code first
  // digitalWrite(TOUCH_SCREEN_TOGGLE_PIN, LOW);
  // pinMode(TOUCH_SCREEN_TOGGLE_PIN, INPUT);

  // setup optional hardware reset pin connection (digital pin 9 or 12)
  // I beleive this works on 3.3v logic, so it will need to pass through a logic
  // level converter
  // digitalWrite(MODULE_RESET_PIN, LOW);
  // pinMode(MODULE_RESET_PIN, OUTPUT);

  #ifdef IWRAP_DEBUG
  UsbSerial.begin(HOST_BAUD);

  while (!UsbSerial);
  UsbSerial.flush();
  #endif // ifdef IWRAP_DEBUG

  IwrapSerial.begin(IWRAP_BAUD);
}

void loop() {
  IwrapManager.manageIwrapState();
  IwrapManager.checkIwrapRx();
  IwrapManager.checkHidTouch();
}
