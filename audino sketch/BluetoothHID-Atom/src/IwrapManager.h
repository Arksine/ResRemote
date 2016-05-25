#ifndef IWRAPMANAGER_H
#define IWRAPMANAGER_H

#include "definitions.h"
#include "TouchManager.h"

class IwrapManager_ {
public:

  IwrapManager_();
  ~IwrapManager_();

  void    manageIwrapState();
  void    checkIwrapRx();
  void    checkHidTouch();
  void    processRxPacket(String rxPacket);

  // Iwrap callback manipulation
  void    setActiveConnections(uint8_t numConnections);
  void    addActiveConnection();
  void    iwrapAddPairing(iwrap_address_t *remoteMac);
  void    iwrapChangeState(uint8_t newIwrapState);
  void    iwrapSetupCall(uint8_t link_id);
  void    iwrapCheckPendingCall(uint8_t link_id);
  void    iwrapDisconnect(uint8_t link_id);
  uint8_t iwrapGetHidChannel();
  uint8_t iwrapGetSppChannel();

  // Iwrap helpers
  uint8_t findPairingFromMac(const iwrap_address_t *mac);
  uint8_t findPairingFromLinkId(uint8_t link_id);
  void    addMappedConnection(uint8_t                link_id,
                              const iwrap_address_t *addr,
                              const char            *mode,
                              uint16_t               channel);
  uint8_t removeMappedConnection(uint8_t link_id);
  void    callHidDevice();

  const uint8_t iwrapMode;

private:

  iwrap_connection_t *iwrapConnectionMap[IWRAP_MAX_PAIRINGS];

  // IWRAP STATE TRACKING
  uint8_t  iwrapState;
  uint8_t  iwrapInitialized;
  uint32_t iwrapTimeRef;
  uint8_t  iwrapNumPairings;
  uint8_t  iwrapPendingCalls;
  uint8_t  iwrapPendingCallLinkId;
  uint8_t  iwrapConnectedDevices;
  uint8_t  iwrapActiveConnections;
  uint16_t iwrapCallDelayMs;
  uint32_t iwrapCallLastTime;
  uint8_t  iwrapCallIndex;
  bool     iwrapAutocallOn;
  bool     storedMacCalled;

  // index of the currently selected connected HID device
  uint8_t hidConnectedIndex;
  uint8_t sppConnectedIndex;

  TouchManager touchManager;
};

extern IwrapManager_ IwrapManager;

#endif /* ifndef IWRAPMANAGER_H */
