/**
 * SerialManager.cpp
 *
 * Implementation of the SerialManager class
 */

#include "SerialManager.h"

SerialManager::SerialManager() {
  connected    = false;
  serialBuffer = "";
}

SerialManager::~SerialManager() {}

bool SerialManager::isConnected() {
  return connected;
}

void SerialManager::startSerial() {
  // already connected, no need to start
  if (connected) {
    return;
  }

  digitalWrite(LEDPIN, HIGH);

  // Open the serial port
  Serial.begin(9600);
  connected = true;

  while (!Serial) {
    // If we accidentally enter serial mode without a connection, we can exit
    // with the same switch we used to enter
    if (digitalRead(CALPIN) == HIGH) {
      // The switch is momentary, so wait for the pin to go low before
      // continuing
      while (digitalRead(CALPIN) == HIGH) {
        delay(10);
      }
      endSerial();
      return;
    }
  }
  Serial.flush();
}

void SerialManager::endSerial() {
  if (!connected) {
    return;
  }

  connected = false;

  digitalWrite(LEDPIN, LOW);
  Serial.end();
}

String SerialManager::checkSerial() {
  if (!connected) {
    return "";
  }


  if (Serial.available() > 0) {
    char ch = Serial.read();

    if (ch == '<') {
      // packet is beginning, clear buffer
      serialBuffer = "";
    }
    else if (ch == '>') {
      // end of packet, return the command
      return serialBuffer;
    }
    else {
      // part of the stream, add it to the buffer
      serialBuffer += ch;
    }
  }

  return "";
}
