/**
 * TouchManager.h
 *
 * Manages Touchscreen functionaltiy
 */
#ifndef TOUCHMANAGER_H
#define TOUCHMANAGER_H

#include "definitions.h"
#include "StorageManager.h"
#include "SerialManager.h"
#include <TouchScreen.h>

class TouchManager {
public:

  TouchManager();
  ~TouchManager();

  void loopFunction();

private:

  TouchScreen ts;
  bool isTouching;
  SerialManager  serialManager;
  StorageManager storageManager;
  StoreStruct   *storage;
  unsigned long  loopDelay;
  unsigned long  touchDelay;

  void sendHidCoordinate(int tX,
                         int tY,
                         int tZ);
  void processCommand(String command);
  void setStorageVariables(String data);
  void getPoint();
  void getPressure();
  void blinkLED();
};


#endif /* end of include guard:  */
