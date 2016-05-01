/**
 * TouchManager.h
 *
 * Manages Touchscreen functionaltiy
 */
#ifndef TOUCHMANAGER_H
#define TOUCHMANAGER_H

#include "definitions.h"
#include "StorageManager.h"
#include "AltSoftSerial.h"
#include <TouchScreen.h>

class TouchManager {
public:

  TouchManager();
  ~TouchManager();

  void startSerial(long speed);
  void loopFunction();

private:

  TouchScreen    ts;
  AltSoftSerial  mySerial;
  StorageManager storageManager;
  StoreStruct   *storage;
  bool start;
  bool isTouching;
  unsigned long loopDelay;
  unsigned long touchDelay;
  String serialBuffer;

  void checkSerial();
  void sendSerialCoordinate(int tX,
                            int tY,
                            int tZ);
  void processCommand(String command);
  void setStorageVariables(String data);
  void getPoint();
  void getPressure();
};


#endif /* end of include guard:  */
