#ifndef TOUCHMANAGER_H
#define TOUCHMANAGER_H

#include <TouchScreen.h>
#include "StorageManager.h"
#include "HidTouchDevice.h"

class TouchManager {
public:

  TouchManager();
  ~TouchManager();

  void             setHidLinkId(uint8_t linkId);
  void             setrfCommLinkId(uint8_t linkId);
  void             setMacAddress(const iwrap_address_t *mac);
  iwrap_address_t* getMacAddress();
  void             processCommand(String command);
  void             checkForTouch();

private:

  TouchScreen ts;
  HidTouchDevice touchDevice;
  StorageManager storageManager;
  StoreStruct   *storage;
  bool isStarted;
  bool isTouching;
  unsigned long loopDelay;
  unsigned long touchDelay;
  uint8_t iwrapMode;
  uint8_t rfCommLinkId;

  void sendHidCoordinate(int tX,
                         int tY,
                         int tZ);
  void setStorageVariables(String data);
  void getPoint();
  void getPressure();
  void writeToSerial(String strToWrite);
};

#endif /* ifndef TOUCHMANAGER_H */
