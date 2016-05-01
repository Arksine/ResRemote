/**
 * ProMicroUSB-Atom
 *
 * Reads x, y and z coordinates from a resistive touch screen, then sends them
 * to a device as a HID digitizer/touchscreen
 */

#include <arduino.h>
#include "TouchManager.h"

TouchManager touch;

void setup() {
  pinMode(CALPIN, INPUT);
  pinMode(LEDPIN, OUTPUT);
}

void loop() {
  touch.loopFunction();
}
