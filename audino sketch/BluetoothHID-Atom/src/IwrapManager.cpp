#include "IwrapManager.h"
#include "IwrapCallbacks.h"

IwrapManager_::IwrapManager_() : iwrapMode(IWRAP_MODE_MUX) {
  iwrapState             = IWRAP_STATE_UNKNOWN;
  iwrapInitialized       = 0;
  iwrapTimeRef           = 0;
  iwrapNumPairings       = 0;
  iwrapPendingCalls      = 0;
  iwrapPendingCallLinkId = 0xFF;
  iwrapConnectedDevices  = 0;
  iwrapActiveConnections = 0;
  iwrapCallDelayMs       = 10000;
  iwrapCallLastTime      = 0;
  iwrapCallIndex         = 0;
  iwrapAutocallOn        = true;
  storedMacCalled        = false;

  // index of the currently selected connected HID device
  hidConnectedIndex = 0xFF;
  sppConnectedIndex = 0xFF;

  assignIwrapCallbacks();
}

IwrapManager_::~IwrapManager_() {}

void IwrapManager_::manageIwrapState() {
  // manage iWRAP state machine
  if (!iwrap_pending_commands) {
    // no pending commands, some state transition occurring
    if (iwrapState) {
      // not idle, in the middle of some process
      if (iwrapState == IWRAP_STATE_UNKNOWN) {
        // reset all detailed state trackers
        iwrapInitialized       = 0;
        iwrapNumPairings       = 0;
        iwrapPendingCalls      = 0;
        iwrapPendingCallLinkId = 0xFF;
        iwrapConnectedDevices  = 0;
        iwrapActiveConnections = 0;

        // send command to test module connectivity
        #ifdef TOUCH_DEBUG
        serialOut(F("Testing iWRAP communication...\n"));
        #endif // ifdef TOUCH_DEBUG
        iwrap_send_command("AT", iwrapMode);

        iwrapState = IWRAP_STATE_PENDING_AT;

        // initialize time reference for connectivity test timeout
        iwrapTimeRef = millis();
      } else if (iwrapState == IWRAP_STATE_PENDING_AT) {
        // send command to dump all module settings and pairings
        #ifdef TOUCH_DEBUG
        serialOut(F("Getting iWRAP settings...\n"));
        #endif // ifdef TOUCH_DEBUG
        iwrap_send_command("SET", iwrapMode);
        iwrapState = IWRAP_STATE_PENDING_SET;
      } else if (iwrapState == IWRAP_STATE_PENDING_SET) {
        // send command to show all current connections
        #ifdef TOUCH_DEBUG
        serialOut(F("Getting active connection list...\n"));
        #endif // ifdef TOUCH_DEBUG
        iwrap_send_command("LIST", iwrapMode);
        iwrapState = IWRAP_STATE_PENDING_LIST;
      } else if (iwrapState == IWRAP_STATE_PENDING_LIST) {
        // all done!
        if (!iwrapInitialized) {
          iwrapInitialized = 1;
          #ifdef TOUCH_DEBUG
          serialOut(F("iWRAP initialization complete\n"));
          #endif // ifdef TOUCH_DEBUG

          // set the autocall time ref so we give a connected hid device a
          // chance to ring
          iwrapCallLastTime = millis();
        }
        iwrapState = IWRAP_STATE_IDLE;
      } else if ((iwrapState == IWRAP_STATE_PENDING_CALL) &&
                 !iwrapPendingCalls) {
        // all done!
        #ifdef TOUCH_DEBUG
        serialOut(F("Pending call processed\n"));
        #endif // ifdef TOUCH_DEBUG
        iwrapState = IWRAP_STATE_IDLE;
      }
    } else if (iwrapInitialized) {
      // idle

      // If we have a paired hid device that didn't ring, attempt to call it.
      // there is a 10 second wait prior to the first call, and between calls
      if (iwrapAutocallOn && iwrapNumPairings && (hidConnectedIndex == 0xFF) &&
          !iwrapPendingCalls &&
          ((millis() - iwrapCallLastTime) >= iwrapCallDelayMs)) {
        // We will try to retreive a mac address stored in EEPROM for the first
        // call attempt.  If it fails then the autocall functionality will
        // cycle through all paired devices every 10 seconds
        if (!storedMacCalled) {
          iwrapCallIndex = findPairingFromMac(touchManager.getMacAddress());

          if (iwrapCallIndex == 0xFF) {
            // either no mac address was stored, or its no longer paired.  Try
            // connection 0
            iwrapCallIndex = 0;
          }
          storedMacCalled = true;
        }
        callHidDevice();
      }
    }
  }

  // check for timeout if still testing communication
  if (!iwrapInitialized && (iwrapState == IWRAP_STATE_PENDING_AT)) {
    if (millis() - iwrapTimeRef > 5000) {
      #ifdef TOUCH_DEBUG
      serialOut(F("ERROR: Could not communicate with iWRAP module\n"));
      #endif // ifdef TOUCH_DEBUG
      iwrapState             = IWRAP_STATE_COMM_FAILED;
      iwrap_pending_commands = 0; // normally handled by the parser, but comms
                                  // failed
    }
  }
}

void IwrapManager_::checkIwrapRx() {
  // check for incoming iwrap data
  uint16_t result;

  if ((result = IwrapSerial.read()) < 256) {
    iwrap_parse(result & 0xFF, iwrapMode);
  }
}

void IwrapManager_::processRxPacket(String rxPacket) {
  if (rxPacket.charAt(0) == '#') {
    // This is an irwap or device control command

    // get rid of the leading # that signifies an arduino command
    rxPacket.remove(0);

    if (rxPacket == "GET_DEVICE_LIST") {
      // Sends a list of MAC Addresses currently paired with the bluetooth
      char   macAddr[]  = "AABBCCDDEEFF";
      char  *macptr     = macAddr;
      String macString  = "<MAC_COUNT:" + String(iwrapNumPairings) + ">";
      const char *count = macString.c_str();
      iwrap_send_data(iwrapConnectionMap[sppConnectedIndex]->link_spp,
                      macString.length(),  (uint8_t *)count, iwrapMode);

      for (uint8_t i = 0; i < iwrapNumPairings; i++) {
        iwrap_bintohexstr((uint8_t *)(iwrapConnectionMap[i]->mac.address),
                          6, &macptr, 0, 0);
        macString = "<MAC:";
        macString.concat(macAddr);
        macString += ">";
        const char *data = macString.c_str();

        iwrap_send_data(iwrapConnectionMap[sppConnectedIndex]->link_spp,
                        macString.length(),  (uint8_t *)data, iwrapMode);
      }
    } else if (rxPacket == "TOGGLE_AUTOCALL") {
      // Toggle autocall function off/on
      iwrapAutocallOn = !iwrapAutocallOn;
    } else if (rxPacket.startsWith("CONN_HID")) {
      // attempts to connect to the mac address specified
      const char *macAddr = rxPacket.substring(9).c_str();
      char *end;
      iwrap_address_t curMac;
      iwrap_hexstrtobin(macAddr, &end, curMac.address, 6);

      iwrapCallIndex = findPairingFromMac(&curMac);

      if (iwrapCallIndex == 0xFF) {
        // error, mac address not found TODO: send to debug, perhaps send a
        // packet
        // back to the BT host telling them the mac doesn't exist
      } else {
        // Call this device
        if ((hidConnectedIndex != 0xFF) &&
            (iwrapConnectionMap[hidConnectedIndex]->link_hid_control != 0xFF)) {
          // a hid device is currently connected, disconnect first
          String closeCmd = "CLOSE " +
                            String(
            iwrapConnectionMap[hidConnectedIndex]->link_hid_control);
          iwrap_send_command(closeCmd.c_str(), iwrapMode);
        }

        callHidDevice();
      }
    #ifdef CAMARO_SCREEN
    } else if (rxPacket == "TOGGLE_TS") {
      // send a pulse to turn the touch screen on or off
      // TODO:  need state tracking for touch screen switcher.  Also need to
      // set up output pins
    } else if (rxPacket.startsWith("HDLINK_INPUT")) {
      // changes to the next input, or the selected input
      // TODO:  need state tracking for this (which input is selected), and
    #endif // ifdef CAMARO_SCREEN
    } else {
      // TODO: unknown command received
    }
  } else {
    // This is a calibration command send it to the touch manager
    touchManager.processCommand(rxPacket);
  }
}

void IwrapManager_::checkHidTouch() {
  if (iwrapGetHidChannel() != 0xFF) {
    touchManager.checkForTouch();
  }
}

void IwrapManager_::setActiveConnections(uint8_t numConnections) {
  iwrapActiveConnections = numConnections;
}

void IwrapManager_::addActiveConnection() {
  iwrapActiveConnections++;
}

void IwrapManager_::iwrapAddPairing(iwrap_address_t *remoteMac) {
  // make sure we allocate memory for the connection map entry
  if (iwrapConnectionMap[iwrapNumPairings] == 0) {
    iwrapConnectionMap[iwrapNumPairings] = (iwrap_connection_t *)malloc(
      sizeof(iwrap_connection_t));
  }
  memset(iwrapConnectionMap[iwrapNumPairings],
         0xFF,
         sizeof(iwrap_connection_t)); // 0xFF is "no link ID"
  memcpy(&(iwrapConnectionMap[iwrapNumPairings]->mac), remoteMac,
         sizeof(iwrap_address_t));
  iwrapConnectionMap[iwrapNumPairings]->active_links = 0;
  iwrapNumPairings++;
}

void IwrapManager_::iwrapChangeState(uint8_t newIwrapState) {
  iwrapState = newIwrapState;
}

void IwrapManager_::iwrapSetupCall(uint8_t link_id) {
  iwrapPendingCalls++;
  iwrapPendingCallLinkId = link_id;
  iwrapCallIndex         = (iwrapCallIndex + 1) % iwrapNumPairings;
  iwrapState             = IWRAP_STATE_PENDING_CALL;
}

void IwrapManager_::iwrapCheckPendingCall(uint8_t link_id) {
  if (iwrapPendingCallLinkId == link_id) {
    if (iwrapPendingCalls) iwrapPendingCalls--;

    if (iwrapState == IWRAP_STATE_PENDING_CALL) iwrapState = IWRAP_STATE_IDLE;
    iwrapPendingCallLinkId = 0xFF;
  }
}

void IwrapManager_::iwrapDisconnect(uint8_t link_id) {
  if (iwrapPendingCallLinkId == link_id) {
    if (iwrapPendingCalls) iwrapPendingCalls--;

    if (iwrapState == IWRAP_STATE_PENDING_CALL) iwrapState = IWRAP_STATE_IDLE;
    iwrapPendingCallLinkId = 0xFF;
  }
  removeMappedConnection(link_id);
}

uint8_t IwrapManager_::iwrapGetHidChannel() {
  return (hidConnectedIndex != 0xFF) ?
         iwrapConnectionMap[hidConnectedIndex]->link_hid_interrupt : 0xFF;
}

uint8_t IwrapManager_::iwrapGetSppChannel() {
  return (sppConnectedIndex != 0xFF) ?
         iwrapConnectionMap[sppConnectedIndex]->link_spp : 0xFF;
}

uint8_t IwrapManager_::findPairingFromMac(const iwrap_address_t *mac) {
  uint8_t i;

  for (i = 0; i < iwrapNumPairings; i++) {
    if (memcmp(&(iwrapConnectionMap[i]->mac), mac,
               sizeof(iwrap_address_t)) == 0) return i;
  }
  return i >= iwrapNumPairings ? 0xFF : i;
}

uint8_t IwrapManager_::findPairingFromLinkId(uint8_t link_id) {
  for (uint8_t i = 0; i < iwrapNumPairings; i++) {
    if ((iwrapConnectionMap[i]->link_hid_control == link_id) ||
        (iwrapConnectionMap[i]->link_hid_interrupt == link_id) ||
        (iwrapConnectionMap[i]->link_spp == link_id)) {
      return i;
    }
  }
  return 0xFF;
}

void IwrapManager_::addMappedConnection(uint8_t                link_id,
                                        const iwrap_address_t *addr,
                                        const char            *mode,
                                        uint16_t               channel) {
  uint8_t pairingIndex = findPairingFromMac(addr);

  // make sure we found a match (we SHOULD always match something, if we
  // properly parsed the pairing data first)
  if (pairingIndex == 0xFF) return; // uh oh

  // updated connected device count and overall active link count for this
  // device
  if (!iwrapConnectionMap[pairingIndex]->active_links) {
    iwrapConnectedDevices++;
  }
  iwrapConnectionMap[pairingIndex]->active_links++;

  // add link ID to connection map
  if (strcmp(mode, "HID") == 0) {
    if (channel == 0x11) {
      iwrapConnectionMap[pairingIndex]->link_hid_control = link_id;
    } else {
      iwrapConnectionMap[pairingIndex]->link_hid_interrupt = link_id;

      // Since this link is the data/interrupt channel, this will be the
      // currently connected device if one has not been set.
      if (hidConnectedIndex == 0xFF) {
        // Make sure the control link has been established.
        if (iwrapConnectionMap[pairingIndex]->link_hid_control != 0xFF) {
          hidConnectedIndex = pairingIndex;
          touchManager.setHidLinkId(link_id);
          touchManager.setMacAddress(addr);
          storedMacCalled = true;
        } else {
          // The control channel did not connect, but the interrupt did.  Close
          // this link
          String closeCmd = "CLOSE " + String(link_id);
          iwrap_send_command(closeCmd.c_str(), iwrapMode);
        }
      }
    }
  } else if (strcmp(mode, "RFCOMM") == 0) {
    // TODO: SPP should be identified by "channel" 1, but its manufacturer
    // specific, and posssibly another channel, so we won't check for it.
    // probably SPP, possibly other RFCOMM-based connections
    iwrapConnectionMap[pairingIndex]->link_spp = link_id;

    // set the first connected rfcomm device as the current rfcomm index
    if (sppConnectedIndex == 0xFF) {
      sppConnectedIndex = pairingIndex;
      touchManager.setrfCommLinkId(link_id);
    }
  }
}

uint8_t IwrapManager_::removeMappedConnection(uint8_t link_id) {
  uint8_t i;

  for (i = 0; i < iwrapNumPairings; i++) {
    if (iwrapConnectionMap[i]->link_hid_control == link_id) {
      iwrapConnectionMap[i]->link_hid_control = 0xFF;
      break;
    }

    if (iwrapConnectionMap[i]->link_hid_interrupt == link_id) {
      iwrapConnectionMap[i]->link_hid_interrupt = 0xFF;

      // index is currently connected device.  Reset the index
      if (i == hidConnectedIndex) {
        hidConnectedIndex = 0xFF;

        // since we lost our HID connection, reset the link ids in the touch
        // manager
        touchManager.setHidLinkId(0xFE);
      }
      break;
    }

    if (iwrapConnectionMap[i]->link_spp == link_id) {
      iwrapConnectionMap[i]->link_spp = 0xFF;

      if (i == sppConnectedIndex) {
        sppConnectedIndex = 0xFF;

        // we lost rfcomm connection prior to losing hid connection
        touchManager.setrfCommLinkId(0xFE);
      }
      break;
    }
  }

  // check to make sure we found the link ID in the map
  if (i < iwrapNumPairings) {
    // updated connected device count and overall active link count for this
    // device
    if (iwrapConnectionMap[i]->active_links) {
      iwrapConnectionMap[i]->active_links--;

      if (!iwrapConnectionMap[i]->active_links) iwrapConnectedDevices--;
    }
    return i;
  }

  // not found, return 0xFF
  return 0xFF;
}

void IwrapManager_::callHidDevice() {
  char  cmd[] = "CALL AA:BB:CC:DD:EE:FF 11 HID"; // HID
  char *cptr  = cmd + 5;

  iwrap_bintohexstr(
    (uint8_t *)(iwrapConnectionMap[iwrapCallIndex]->mac.address),
    6, &cptr, ':', 0);

  #ifdef TOUCH_DEBUG
  char s[21];
  sprintf(s, "Calling device #%d\r\n", iwrapCallIndex);
  serialOut(s);
  #endif // ifdef TOUCH_DEBUG

  iwrap_send_command(cmd, iwrapMode);
  iwrapCallLastTime = millis();
}

// Creates a global instance of the IwrapManager class, necessary for
// communication with the iwrap callbacks
IwrapManager_ IwrapManager;
