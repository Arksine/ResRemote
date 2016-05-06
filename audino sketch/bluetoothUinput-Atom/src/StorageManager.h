/**
 * StorageManager.h
 *
 * Class that handles retreiving and updating variables stored in EEPROM.
 *
 */

#ifndef STORAGEMANAGER_H
#define STORAGEMANAGER_H

#include "definitions.h"

// Todo: need to include arduino libraries
class StorageManager {
public:

  StorageManager();
  ~StorageManager();

  StoreStruct* getStorage();
  void         updateConfiguration();
  bool         isConfigValid();

private:

  bool loadConfiguration();

  StoreStruct storage;
  int  configAddress;
  bool configValid;
};


#endif /* end of include guard:  */
