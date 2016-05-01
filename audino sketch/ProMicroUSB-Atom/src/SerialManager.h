/**
 * SerialManager.h
 *
 * Class that manages serial communication
 */
#ifndef SERIALMANAGER_H
#define SERIALMANAGER_H

#include "definitions.h"
#include "StorageManager.h"

class SerialManager {
public:

  SerialManager();
  ~SerialManager();

  bool   isConnected();
  void   startSerial();
  void   endSerial();
  String checkSerial();

private:

  bool   connected;
  String serialBuffer;
};

#endif /* end of include guard:  */
