/**
 * NanoBT_Serial-Atom
 *
 * Reads x, y and z coordinates from a resistive touch screen, then sends them
 * to a device via serial
 */

#include <arduino.h>
#include "TouchManager.h"

TouchManager touch;

void setup() {
  pinMode(LEDPIN, OUTPUT);
  digitalWrite(LEDPIN, HIGH);
  uartSerial.begin(115200);

  while (!uartSerial);
  uartSerial.flush();
}

void loop() {
  touch.loopFunction();
}
