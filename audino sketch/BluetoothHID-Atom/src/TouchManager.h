#ifndef TOUCHMANAGER_H
#define TOUCHMANAGER_H

#include <TouchScreen.h>
#include "StorageManager.h"
#include "HidTouchDevice.h"

class TouchManager {
public:

  TouchManager();
  ~TouchManager();

  void setHidLinkId(uint8_t linkId);
  void setrfCommLinkId(uint8_t linkId);
  void addToCommandBuffer(char ch);
  void checkForTouch();

private:

  TouchScreen ts;
  HidTouchDevice touchDevice;
  StorageManager storageManager;
  StoreStruct   *storage;
  bool isStarted;
  bool isTouching;
  unsigned long loopDelay;
  unsigned long touchDelay;
  String  commandBuffer;
  uint8_t iwrapMode;
  uint8_t rfCommLinkId;

  void sendHidCoordinate(int tX,
                         int tY,
                         int tZ);
  void processCommand(String command);
  void setStorageVariables(String data);
  void getPoint();
  void getPressure();
  void writeToSerial(String strToWrite);
};

#endif /* ifndef TOUCHMANAGER_H */
