/**
 * StorageManager.cpp
 *
 * StorageManager Implementation
 */
#include "StorageManager.h"
#include "EEPROMex.h"

StorageManager::StorageManager() {
  // allow 10 writes per cycle so we don't accidentally burn out the EEPROM
  EEPROM.setMaxAllowedWrites(10);
  EEPROM.setMemPool(MEMORYBASE, EEPROMSizeATmega32u4);
  configAddress = EEPROM.getAddress(sizeof(StoreStruct));
  configValid   = loadConfiguration();

  if (!configValid) {
    strcpy(storage.version, CONFIG_VERSION);
    storage.A             = 100000;
    storage.B             = 0;
    storage.C             = 0;
    storage.D             = 0;
    storage.E             = 100000;
    storage.F             = 0;
    storage.minResistance = MINPRESSURE;
    storage.rotation      = 0;
  }
}

StorageManager::~StorageManager() {}

bool StorageManager::isConfigValid() {
  return configValid;
}

StoreStruct * StorageManager::getStorage() {
  return &storage;
}

bool StorageManager::loadConfiguration() {
  EEPROM.readBlock(configAddress, storage);
  return strcmp(storage.version, CONFIG_VERSION) == 0;
}

void StorageManager::updateConfiguration() {
  EEPROM.updateBlock(configAddress, storage);
}
