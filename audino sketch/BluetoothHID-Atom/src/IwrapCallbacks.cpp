#include "IwrapCallbacks.h"
#include "IwrapManager.h"


#if defined(ARDUINO_AVR_NANO)
AltSoftSerial altSerial;
#endif // if defined(ARDUINO_AVR_NANO)

String rxPacket = "";

void assignIwrapCallbacks() {
  // assign transport/debug output
  iwrap_output = iwrapOut;

  #ifdef TOUCH_DEBUG

  // mirrors data sent over Iwrap SPPs tx channel to usb
  iwrap_callback_txdata = cbIwrapTxdata;
  #endif // ifdef TOUCH_DEBUG

  #ifdef IWRAP_DEBUG
  iwrap_debug = serialOut;
  #endif /* IWRAP_DEBUG */

  // assign event callbacks
  iwrap_callback_rxdata = cbIwrapRxdata;
  iwrap_rsp_call        = cbIwrapRspCall;
  iwrap_rsp_list_count  = cbIwrapRspListCount;
  iwrap_rsp_list_result = cbIwrapRspListResult;
  iwrap_rsp_set         = cbIwrapRspSet;
  iwrap_evt_connect     = cbIwrapEvtConnect;
  iwrap_evt_no_carrier  = cbIwrapEvtNoCarrier;
  iwrap_evt_pair        = cbIwrapEvtPair;
  iwrap_evt_ready       = cbIwrapEvtReady;
  iwrap_evt_ring        = cbIwrapEvtRing;
}

void cbIwrapRxdata(uint8_t channel, uint16_t length, const uint8_t *data) {
  uint8_t sppChannel = IwrapManager.iwrapGetSppChannel();

  if (channel == sppChannel) {
    // we have data from the rfcomm channel (should be SPP), now send the data
    // to the touch manager for processing
    for (uint8_t i = 0; i < length; i++) {
      if ((char)i == '<') {
        // packet is beginning, clear buffer
        rxPacket = "";
      }
      else if ((char)i == '>') {
        // end of packet, send  for processing
        IwrapManager.processRxPacket(rxPacket);
      }
      else {
        // part of the stream, add it to the buffer
        rxPacket += (char)i;
      }
    }
  }
}

#ifdef TOUCH_DEBUG
void cbIwrapTxdata(uint8_t channel, uint16_t length, const uint8_t *data) {
  // get the currently connected RFCOMM/SPP channel number
  uint8_t sppChannel = IwrapManager.iwrapGetSppChannel();

  if (channel == sppChannel) {
    serialOut(F("TX to Host:\n"));
    String dataBuf = "";

    for (uint8_t i = 0; i < length; i++) {
      dataBuf += (char)data[i];
    }
    dataBuf += '\n';
    serialOut(dataBuf.c_str());
  }
}

#endif // ifdef TOUCH_DEBUG

void cbIwrapRspCall(uint8_t link_id) {
  IwrapManager.iwrapSetupCall(link_id);
}

void cbIwrapRspListCount(uint8_t num_of_connections) {
  IwrapManager.setActiveConnections(num_of_connections);
}

void cbIwrapRspListResult(uint8_t                link_id,
                          const char            *mode,
                          uint16_t               blocksize,
                          uint32_t               elapsed_time,
                          uint16_t               local_msc,
                          uint16_t               remote_msc,
                          const iwrap_address_t *addr,
                          uint16_t               channel,
                          uint8_t                direction,
                          uint8_t                powermode,
                          uint8_t                role,
                          uint8_t                crypt,
                          uint16_t               buffer,
                          uint8_t                eretx);
void cbIwrapRspSet(uint8_t     category,
                   const char *option,
                   const char *value) {
  if (category == IWRAP_SET_CATEGORY_BT) {
    if (strncmp((char *)option, "BDADDR", 6) == 0) {
                         #ifdef TOUCH_DEBUG
      iwrap_address_t local_mac;
      iwrap_hexstrtobin((char *)value, 0, local_mac.address, 0);
      char *local_mac_str = (char *)malloc(18);

      if (local_mac_str) {
        iwrap_bintohexstr(local_mac.address, 6, &local_mac_str, ':', 1);
        serialOut(F(":: Module MAC is "));
        serialOut(local_mac_str);
        serialOut(F("\n"));
        free(local_mac_str);
      }
                         #endif // ifdef TOUCH_DEBUG
    } else if (strncmp((char *)option, "NAME", 4) == 0) {
                         #ifdef TOUCH_DEBUG
      serialOut(F(":: Friendly name is "));
      serialOut(value);
      serialOut(F("\n"));
                         #endif // ifdef TOUCH_DEBUG
    } else if (strncmp((char *)option, "PAIR", 4) == 0) {
      iwrap_address_t remote_mac;
      iwrap_hexstrtobin((char *)value, 0, remote_mac.address, 0);

      IwrapManager.iwrapAddPairing(&remote_mac);

      #ifdef TOUCH_DEBUG
      char *remote_mac_str = (char *)malloc(18);

      if (remote_mac_str) {
        iwrap_bintohexstr(remote_mac.address, 6, &remote_mac_str, ':', 1);
        serialOut(F(":: Pairing (MAC="));
        serialOut(remote_mac_str);
        serialOut(F(", key="));
        serialOut(value + 18);
        serialOut(F(")\n"));
        free(remote_mac_str);
      }
      #endif // ifdef TOUCH_DEBUG
    }
  }
}

void cbIwrapEvtConnect(uint8_t link_id, const char *type, uint16_t target,
                       const iwrap_address_t *address) {
  IwrapManager.iwrapCheckPendingCall(link_id);
  IwrapManager.addActiveConnection();
  IwrapManager.addMappedConnection(link_id, address, type, target);
}

void cbIwrapEvtNoCarrier(uint8_t link_id, uint16_t error_code,
                         const char *message) {
  IwrapManager.iwrapDisconnect(link_id);
}

void cbIwrapEvtPair(const iwrap_address_t *address, uint8_t key_type,
                    const uint8_t *link_key) {
  // request pair list again (could be a new pair, or updated pair, or new +
  // overwritten pair)
  iwrap_send_command("SET BT PAIR", IwrapManager.iwrapMode);
  IwrapManager.iwrapChangeState(IWRAP_STATE_PENDING_SET);
}

void cbIwrapEvtReady() {
  IwrapManager.iwrapChangeState(IWRAP_STATE_UNKNOWN);
}

void cbIwrapEvtRing(uint8_t link_id, const iwrap_address_t *address,
                    uint16_t channel, const char *profile) {
  IwrapManager.addActiveConnection();
  IwrapManager.addMappedConnection(link_id, address, profile, channel);
}

int serialOut(const char *str) {
  // debug output to host goes through hardware serial
  return UsbSerial.print(str);
}

int serialOut(const __FlashStringHelper *str) {
  // debug output to host goes through hardware serial
  return UsbSerial.print(str);
}

int iwrapOut(int len, unsigned char *data) {
  // iWRAP output to module goes through software serial if using Atmega 328,
  // otherwise it goes through uart
  return IwrapSerial.write(data, len);
}
