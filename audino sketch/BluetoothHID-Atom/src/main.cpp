/**
 * BluetoothHID-atom
 *
 * Reads x, y and z coordinates from a resistive touch screen, then sends them
 * to a device via a Bluegiga HID radio
 */

#include <arduino.h>
#include "TouchManager.h"
#include <AltSoftSerial.h>

// define the Platform Specific serial command
#if defined(AVR_PROMICRO16)
# define UsbSerial Serial
# define IwrapSerial Serial1
#elif defined(ARDUINO_AVR_NANO)

AltSoftSerial altSerial; // RX=8, TX=9
# define UsbSerial Serial
# define IwrapSerial altSerial
#else /* if defined(AVR_PROMICRO16) */
# error Select a supported platform, or add support for your own
#endif /* if defined(AVR_PROMICRO16) */

TouchManager touchManager;

// ***** BEGIN IWRAP VARIABLES *********
iwrap_connection_t *iwrap_connection_map[IWRAP_MAX_PAIRINGS];

// IWRAP STATE TRACKING
uint8_t  iwrap_mode                 = IWRAP_MODE_MUX;
uint8_t  iwrap_state                = IWRAP_STATE_UNKNOWN;
uint8_t  iwrap_initialized          = 0;
uint32_t iwrap_time_ref             = 0;
uint8_t  iwrap_pairings             = 0;
uint8_t  iwrap_pending_calls        = 0;
uint8_t  iwrap_pending_call_link_id = 0xFF;
uint8_t  iwrap_connected_devices    = 0;
uint8_t  iwrap_active_connections   = 0;
uint16_t iwrap_call_delay_ms        = 10000;
uint32_t iwrap_call_last_time       = 0;
uint8_t  iwrap_call_index           = 0;
bool     iwrap_autocall_on          = true;
bool     stored_mac_called          = false;

// index of the currently selected connected HID device
uint8_t hid_connected_index = 0xFF;
uint8_t spp_connected_index = 0xFF;
String  commandBuffer       = "";

// ******* END IWRAP VARIABLES **************

// ******* BEGIN IWRAP CALLBACK PROTOTYPES ************
void my_iwrap_rxdata(uint8_t        channel,
                     uint16_t       length,
                     const uint8_t *data);
void my_iwrap_txdata(uint8_t        channel,
                     uint16_t       length,
                     const uint8_t *data);
void my_iwrap_rsp_call(uint8_t link_id);
void my_iwrap_rsp_list_count(uint8_t num_of_connections);
void my_iwrap_rsp_list_result(uint8_t                link_id,
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
void my_iwrap_rsp_set(uint8_t     category,
                      const char *option,
                      const char *value);
void my_iwrap_evt_connect(uint8_t                link_id,
                          const char            *type,
                          uint16_t               target,
                          const iwrap_address_t *address);
void my_iwrap_evt_no_carrier(uint8_t     link_id,
                             uint16_t    error_code,
                             const char *message);
void my_iwrap_evt_pair(const iwrap_address_t *address,
                       uint8_t                key_type,
                       const uint8_t         *link_key);
void my_iwrap_evt_ready();
void my_iwrap_evt_ring(uint8_t                link_id,
                       const iwrap_address_t *address,
                       uint16_t               channel,
                       const char            *profile);

/*void my_iwrap_evt_hid_output(uint8_t        link_id,
                             uint16_t       data_length,
                             const uint8_t *data);*/

// ************** END IWRAP CALLBACK PROTOTYPES *************************

// ****** BEGIN IWRAP HELPER FUNCTION PROTOTYPES *****************
uint8_t find_pairing_from_mac(const iwrap_address_t *mac);
uint8_t find_pairing_from_link_id(uint8_t link_id);
void    add_mapped_connection(uint8_t                link_id,
                              const iwrap_address_t *addr,
                              const char            *mode,
                              uint16_t               channel);
uint8_t remove_mapped_connection(uint8_t link_id);
void    process_arduino_command(String command);
void    iwrap_call_hid_device();

// platform-specific helper functions
int     serial_out(const char *str);
int     serial_out(const __FlashStringHelper *str);
int     iwrap_out(int            len,
                  unsigned char *data);


void setup() {
  // Set up LED pin (will turn on when device needs configuration or is
  // in configuration mode)
  pinMode(LED_PIN, OUTPUT);

  // Set up touch screen toggle pin.  When its pulsed, It will send a signal
  // on another pin to toggle the touch screen switcher between
  // TODO:  Implement this functionality later.  We need to actually test
  // iwrap/hid code first
  // digitalWrite(TOUCH_SCREEN_TOGGLE_PIN, LOW);
  // pinMode(TOUCH_SCREEN_TOGGLE_PIN, INPUT);

  // setup optional hardware reset pin connection (digital pin 9 or 12)
  // digitalWrite(MODULE_RESET_PIN, LOW);
  // pinMode(MODULE_RESET_PIN, OUTPUT);

  #ifdef IWRAP_DEBUG
  UsbSerial.begin(HOST_BAUD);

  while (!UsbSerial);
  UsbSerial.flush();
  #endif // ifdef IWRAP_DEBUG

  IwrapSerial.begin(IWRAP_BAUD);

  // assign transport/debug output
  iwrap_output = iwrap_out;

  #ifdef TOUCH_DEBUG
  iwrap_callback_txdata = my_iwrap_txdata;
  #endif // ifdef TOUCH_DEBUG

  #ifdef IWRAP_DEBUG
  iwrap_debug = serial_out;
  #endif /* IWRAP_DEBUG */

  // assign event callbacks
  iwrap_callback_rxdata = my_iwrap_rxdata;
  iwrap_rsp_call        = my_iwrap_rsp_call;
  iwrap_rsp_list_count  = my_iwrap_rsp_list_count;
  iwrap_rsp_list_result = my_iwrap_rsp_list_result;
  iwrap_rsp_set         = my_iwrap_rsp_set;
  iwrap_evt_connect     = my_iwrap_evt_connect;
  iwrap_evt_no_carrier  = my_iwrap_evt_no_carrier;
  iwrap_evt_pair        = my_iwrap_evt_pair;
  iwrap_evt_ready       = my_iwrap_evt_ready;
  iwrap_evt_ring        = my_iwrap_evt_ring;

  // iwrap_evt_hid_output  =  my_iwrap_evt_hid_output;
}

void loop() {
  // manage iWRAP state machine
  if (!iwrap_pending_commands) {
    // no pending commands, some state transition occurring
    if (iwrap_state) {
      // not idle, in the middle of some process
      if (iwrap_state == IWRAP_STATE_UNKNOWN) {
        // reset all detailed state trackers
        iwrap_initialized          = 0;
        iwrap_pairings             = 0;
        iwrap_pending_calls        = 0;
        iwrap_pending_call_link_id = 0xFF;
        iwrap_connected_devices    = 0;
        iwrap_active_connections   = 0;

        // send command to test module connectivity
        #ifdef TOUCH_DEBUG
        serial_out(F("Testing iWRAP communication...\n"));
        #endif // ifdef TOUCH_DEBUG
        iwrap_send_command("AT", iwrap_mode);

        iwrap_state = IWRAP_STATE_PENDING_AT;

        // initialize time reference for connectivity test timeout
        iwrap_time_ref = millis();
      } else if (iwrap_state == IWRAP_STATE_PENDING_AT) {
        // send command to dump all module settings and pairings
        #ifdef TOUCH_DEBUG
        serial_out(F("Getting iWRAP settings...\n"));
        #endif // ifdef TOUCH_DEBUG
        iwrap_send_command("SET", iwrap_mode);
        iwrap_state = IWRAP_STATE_PENDING_SET;
      } else if (iwrap_state == IWRAP_STATE_PENDING_SET) {
        // send command to show all current connections
        #ifdef TOUCH_DEBUG
        serial_out(F("Getting active connection list...\n"));
        #endif // ifdef TOUCH_DEBUG
        iwrap_send_command("LIST", iwrap_mode);
        iwrap_state = IWRAP_STATE_PENDING_LIST;
      } else if (iwrap_state == IWRAP_STATE_PENDING_LIST) {
        // all done!
        if (!iwrap_initialized) {
          iwrap_initialized = 1;
          #ifdef TOUCH_DEBUG
          serial_out(F("iWRAP initialization complete\n"));
          #endif // ifdef TOUCH_DEBUG

          // set the autocall time ref so we give a connected hid device a
          // chance to ring
          iwrap_call_last_time = millis();
        }
        iwrap_state = IWRAP_STATE_IDLE;
      } else if ((iwrap_state == IWRAP_STATE_PENDING_CALL) &&
                 !iwrap_pending_calls) {
        // all done!
        #ifdef TOUCH_DEBUG
        serial_out(F("Pending call processed\n"));
        #endif // ifdef TOUCH_DEBUG
        iwrap_state = IWRAP_STATE_IDLE;
      }
    } else if (iwrap_initialized) {
      // idle

      // If we have a paired hid device that didn't ring, attempt to call it.
      // there is a 10 second wait prior to the first call, and between calls
      if (iwrap_autocall_on && iwrap_pairings &&
          (hid_connected_index == 0xFF) &&
          !iwrap_pending_calls &&
          ((millis() - iwrap_call_last_time) >= iwrap_call_delay_ms)) {
        // We will try to retreive a mac address stored in EEPROM for the first
        // call attempt.  If it fails then the autocall functionality will
        // cycle through all paired devices every 10 seconds
        if (!stored_mac_called) {
          iwrap_call_index = find_pairing_from_mac(touchManager.getMacAddress());

          if (iwrap_call_index == 0xFF) {
            // either no mac address was stored, or its no longer paired.  Try
            // connection 0
            iwrap_call_index = 0;
          }

          stored_mac_called = true;

          iwrap_call_hid_device();
        }
      }
    }
  }

  // check for incoming iwrap data
  uint16_t result;

  if ((result = IwrapSerial.read()) < 256) {
    iwrap_parse(result & 0xFF, iwrap_mode);
  }

  // check for timeout if still testing communication
  if (!iwrap_initialized && (iwrap_state == IWRAP_STATE_PENDING_AT)) {
    if (millis() - iwrap_time_ref > 5000) {
      #ifdef TOUCH_DEBUG
      serial_out(F("ERROR: Could not communicate with iWRAP module\n"));
      #endif // ifdef TOUCH_DEBUG
      iwrap_state            = IWRAP_STATE_COMM_FAILED;
      iwrap_pending_commands = 0; // normally handled by the parser, but comms
                                  // failed
    }
  }

  // make sure that the hid index has been set and the hid control channel has
  // been opened
  if ((hid_connected_index != 0xFF) &&
      (iwrap_connection_map[hid_connected_index]->link_hid_interrupt != 0xFF)) {
    touchManager.checkForTouch();
  }
}

void my_iwrap_rxdata(uint8_t channel, uint16_t length, const uint8_t *data) {
  bool selected = false;

  if (spp_connected_index != 0xFF) {
    if (channel == iwrap_connection_map[spp_connected_index]->link_spp) {
      selected = true;

      // we have data from the rfcomm channel (should be SPP), now send the data
      // to the touch manager for processing
      for (uint8_t i = 0; i < length; i++) {
        if ((char)i == '<') {
          // packet is beginning, clear buffer
          commandBuffer = "";
        }
        else if ((char)i == '>') {
          // end of packet, send the command for processing
          if (commandBuffer.charAt(0) == '#') {
            // This is a command that affects arduino or iwrap functionality
            // directly
            process_arduino_command(commandBuffer);
          } else {
            // This command is related to device calibration.  Send it to the
            // TouchManager class for processing
            touchManager.processCommand(commandBuffer);
          }
        }
        else {
          // part of the stream, add it to the buffer
          commandBuffer += (char)i;
        }
      }
    }
  }

  // This data isn't from an SPP connection associated with the hid device
  if (!selected) {
    // TODO: if we allow the bluetooth device to simultaneously connect to
    // multiple serial devices, this is data received from it. do something with
    // it
  }
}

#ifdef TOUCH_DEBUG
void my_iwrap_txdata(uint8_t channel, uint16_t length, const uint8_t *data) {
  if (spp_connected_index != 0xFF) {
    if (channel == iwrap_connection_map[spp_connected_index]->link_spp) {
      serial_out(F("TX to Host:\n"));
      String dataBuf = "";

      for (uint8_t i = 0; i < length; i++) {
        dataBuf += (char)data[i];
      }
      dataBuf += '\n';
      serial_out(dataBuf.c_str());
    }
  }
}

#endif // ifdef TOUCH_DEBUG


void my_iwrap_rsp_call(uint8_t link_id) {
  // TODO:  I need to create a different algoritm for calling devices.  I'm
  //        not sure if this logic will hold true, or if I'll need different
  //        variables for tracking.
  iwrap_pending_calls++;
  iwrap_pending_call_link_id = link_id;
  iwrap_call_index           = (iwrap_call_index + 1) % iwrap_pairings;
  iwrap_state                = IWRAP_STATE_PENDING_CALL;
}

void my_iwrap_rsp_list_count(uint8_t num_of_connections) {
  iwrap_active_connections = num_of_connections;
}

void my_iwrap_rsp_list_result(uint8_t link_id, const char *mode,
                              uint16_t blocksize, uint32_t elapsed_time,
                              uint16_t local_msc, uint16_t remote_msc,
                              const iwrap_address_t *addr, uint16_t channel,
                              uint8_t direction, uint8_t powermode,
                              uint8_t role, uint8_t crypt,
                              uint16_t buffer,  uint8_t eretx) {
  add_mapped_connection(link_id, addr, mode, channel);
}

void my_iwrap_rsp_set(uint8_t category, const char *option, const char *value) {
  if (category == IWRAP_SET_CATEGORY_BT) {
    if (strncmp((char *)option, "BDADDR", 6) == 0) {
      #ifdef TOUCH_DEBUG
      iwrap_address_t local_mac;
      iwrap_hexstrtobin((char *)value, 0, local_mac.address, 0);
      char *local_mac_str = (char *)malloc(18);

      if (local_mac_str) {
        iwrap_bintohexstr(local_mac.address, 6, &local_mac_str, ':', 1);
        serial_out(F(":: Module MAC is "));
        serial_out(local_mac_str);
        serial_out(F("\n"));
        free(local_mac_str);
      }
      #endif // ifdef TOUCH_DEBUG
    } else if (strncmp((char *)option, "NAME", 4) == 0) {
      #ifdef TOUCH_DEBUG
      serial_out(F(":: Friendly name is "));
      serial_out(value);
      serial_out(F("\n"));
      #endif // ifdef TOUCH_DEBUG
    } else if (strncmp((char *)option, "PAIR", 4) == 0) {
      iwrap_address_t remote_mac;
      iwrap_hexstrtobin((char *)value, 0, remote_mac.address, 0);

      // make sure we allocate memory for the connection map entry
      if (iwrap_connection_map[iwrap_pairings] == 0) {
        iwrap_connection_map[iwrap_pairings] = (iwrap_connection_t *)malloc(
          sizeof(iwrap_connection_t));
      }
      memset(iwrap_connection_map[iwrap_pairings],
             0xFF,
             sizeof(iwrap_connection_t)); // 0xFF is "no link ID"
      memcpy(&(iwrap_connection_map[iwrap_pairings]->mac), &remote_mac,
             sizeof(iwrap_address_t));
      iwrap_connection_map[iwrap_pairings]->active_links = 0;
      iwrap_pairings++;

      #ifdef TOUCH_DEBUG
      char *remote_mac_str = (char *)malloc(18);

      if (remote_mac_str) {
        iwrap_bintohexstr(remote_mac.address, 6, &remote_mac_str, ':', 1);
        serial_out(F(":: Pairing (MAC="));
        serial_out(remote_mac_str);
        serial_out(F(", key="));
        serial_out(value + 18);
        serial_out(F(")\n"));
        free(remote_mac_str);
      }
      #endif // ifdef TOUCH_DEBUG
    }
  }
}

void my_iwrap_evt_connect(uint8_t                link_id,
                          const char            *type,
                          uint16_t               target,
                          const iwrap_address_t *address) {
  if (iwrap_pending_call_link_id == link_id) {
    if (iwrap_pending_calls) iwrap_pending_calls--;

    if (iwrap_state == IWRAP_STATE_PENDING_CALL) iwrap_state = IWRAP_STATE_IDLE;
    iwrap_pending_call_link_id = 0xFF;
  }
  iwrap_active_connections++;
  add_mapped_connection(link_id, address, type, target);
}

void my_iwrap_evt_no_carrier(uint8_t link_id, uint16_t error_code,
                             const char *message) {
  if (iwrap_pending_call_link_id == link_id) {
    if (iwrap_pending_calls) iwrap_pending_calls--;

    if (iwrap_state == IWRAP_STATE_PENDING_CALL) iwrap_state = IWRAP_STATE_IDLE;
    iwrap_pending_call_link_id = 0xFF;
  }

  remove_mapped_connection(link_id);
}

void my_iwrap_evt_pair(const iwrap_address_t *address, uint8_t key_type,
                       const uint8_t *link_key) {
  // request pair list again (could be a new pair, or updated pair, or new +
  // overwritten pair)
  iwrap_send_command("SET BT PAIR", iwrap_mode);
  iwrap_state = IWRAP_STATE_PENDING_SET;
}

void my_iwrap_evt_ready() {
  iwrap_state = IWRAP_STATE_UNKNOWN;
}

void my_iwrap_evt_ring(uint8_t link_id, const iwrap_address_t *address,
                       uint16_t channel, const char *profile) {
  iwrap_active_connections++;
  add_mapped_connection(link_id, address, profile, channel);
}

/*void my_iwrap_evt_hid_output(uint8_t link_id, uint16_t data_length,
                             const uint8_t *data) {
   // this is for data received back from the HID host (for example, raw
   // hid or led status lights for a keybaord)
   //
   // I probably dont have any use for this.
   }*/
uint8_t find_pairing_from_mac(const iwrap_address_t *mac) {
  uint8_t i;

  for (i = 0; i < iwrap_pairings; i++) {
    if (memcmp(&(iwrap_connection_map[i]->mac), mac,
               sizeof(iwrap_address_t)) == 0) return i;
  }
  return i >= iwrap_pairings ? 0xFF : i;
}

uint8_t find_pairing_from_link_id(uint8_t link_id) {
  for (uint8_t i = 0; i < iwrap_pairings; i++) {
    if ((iwrap_connection_map[i]->link_hid_control == link_id) ||
        (iwrap_connection_map[i]->link_hid_interrupt == link_id) ||
        (iwrap_connection_map[i]->link_spp == link_id)) {
      return i;
    }
  }
  return 0xFF;
}

void add_mapped_connection(uint8_t link_id, const iwrap_address_t *addr,
                           const char  *mode, uint16_t channel) {
  uint8_t pairing_index = find_pairing_from_mac(addr);

  // make sure we found a match (we SHOULD always match something, if we
  // properly parsed the pairing data first)
  if (pairing_index == 0xFF) return; // uh oh

  // updated connected device count and overall active link count for this
  // device
  if (!iwrap_connection_map[pairing_index]->active_links) {
    iwrap_connected_devices++;
  }
  iwrap_connection_map[pairing_index]->active_links++;

  // add link ID to connection map
  if (strcmp(mode, "HID") == 0) {
    if (channel == 0x11) {
      iwrap_connection_map[pairing_index]->link_hid_control = link_id;
    } else {
      iwrap_connection_map[pairing_index]->link_hid_interrupt = link_id;

      // Since this link is the data/interrupt channel, this will be the
      // currently connected device if one has not been set.
      if (hid_connected_index == 0xFF) {
        // Make sure the control link has been established.
        if (iwrap_connection_map[pairing_index]->link_hid_control != 0xFF) {
          hid_connected_index = pairing_index;
          touchManager.setHidLinkId(link_id);
          touchManager.setMacAddress(addr);
          stored_mac_called = true;
        } else {
          // The control channel did not connect, but the interrupt did.  Close
          // this link
          String closeCmd = "CLOSE " + String(link_id);
          iwrap_send_command(closeCmd.c_str(), iwrap_mode);
        }
      }
    }
  } else if (strcmp(mode, "RFCOMM") == 0) {
    // TODO: SPP should be identified by "channel" 1, but its manufacturer
    // specific, and posssibly another channel, so we won't check for it.
    // probably SPP, possibly other RFCOMM-based connections
    iwrap_connection_map[pairing_index]->link_spp = link_id;

    // set the first connected rfcomm device as the current rfcomm index
    if (spp_connected_index == 0xFF) {
      spp_connected_index = pairing_index;
      touchManager.setrfCommLinkId(link_id);
    }
  }
}

uint8_t remove_mapped_connection(uint8_t link_id) {
  uint8_t i;

  for (i = 0; i < iwrap_pairings; i++) {
    if (iwrap_connection_map[i]->link_hid_control == link_id) {
      iwrap_connection_map[i]->link_hid_control = 0xFF;
      break;
    }

    if (iwrap_connection_map[i]->link_hid_interrupt == link_id) {
      iwrap_connection_map[i]->link_hid_interrupt = 0xFF;

      // index is currently connected device.  Reset the index
      if (i == hid_connected_index) {
        hid_connected_index = 0xFF;

        // since we lost our HID connection, reset the link ids in the touch
        // manager
        touchManager.setHidLinkId(0xFE);
      }
      break;
    }

    if (iwrap_connection_map[i]->link_spp == link_id) {
      iwrap_connection_map[i]->link_spp = 0xFF;

      if (i == spp_connected_index) {
        spp_connected_index = 0xFF;

        // we lost rfcomm connection prior to losing hid connection
        touchManager.setrfCommLinkId(0xFE);
      }
      break;
    }
  }

  // check to make sure we found the link ID in the map
  if (i < iwrap_pairings) {
    // updated connected device count and overall active link count for this
    // device
    if (iwrap_connection_map[i]->active_links) {
      iwrap_connection_map[i]->active_links--;

      if (!iwrap_connection_map[i]->active_links) iwrap_connected_devices--;
    }
    return i;
  }

  // not found, return 0xFF
  return 0xFF;
}

void process_arduino_command(String command) {
  // get rid of the leading # that signifies an arduino command
  command.remove(0);

  if (command == "GET_DEVICE_LIST") {
    // Sends a list of MAC Addresses currently paired with the bluetooth
    char   macAddr[]  = "AABBCCDDEEFF";
    char  *macptr     = macAddr;
    String macString  = "<MAC_COUNT:" + String(iwrap_pairings) + ">";
    const char *count = macString.c_str();
    iwrap_send_data(iwrap_connection_map[spp_connected_index]->link_spp,
                    macString.length(),  (uint8_t *)count, iwrap_mode);

    for (uint8_t i = 0; i < iwrap_pairings; i++) {
      iwrap_bintohexstr((uint8_t *)(iwrap_connection_map[i]->mac.address),
                        6, &macptr, 0, 0);
      macString = "<MAC:";
      macString.concat(macAddr);
      macString += ">";
      const char *data = macString.c_str();

      iwrap_send_data(iwrap_connection_map[spp_connected_index]->link_spp,
                      macString.length(),  (uint8_t *)data, iwrap_mode);
    }
  } else if (command == "TOGGLE_AUTOCALL") {
    // Toggle autocall function off/on
    iwrap_autocall_on = !iwrap_autocall_on;
  } else if (command.startsWith("CONN_HID")) {
    // attempts to connect to the mac address specified
    const char *macAddr = command.substring(9).c_str();
    char *end;
    iwrap_address_t curMac;
    iwrap_hexstrtobin(macAddr, &end, curMac.address, 6);

    iwrap_call_index = find_pairing_from_mac(&curMac);

    if (iwrap_call_index == 0xFF) {
      // error, mac address not found TODO: send to debug, perhaps send a packet
      // back to the BT host telling them the mac doesn't exist
    } else {
      // Call this device
      if ((hid_connected_index != 0xFF) &&
          (iwrap_connection_map[hid_connected_index]->link_hid_control != 0xFF)) {
        // a hid device is currently connected, disconnect first
        String closeCmd = "CLOSE " +
                          String(
          iwrap_connection_map[hid_connected_index]->link_hid_control);
        iwrap_send_command(closeCmd.c_str(), iwrap_mode);
      }

      iwrap_call_hid_device();
    }
  #ifdef CAMARO_SCREEN
  } else if (command == "TOGGLE_TS") {
    // send a pulse to turn the touch screen on or off
    // TODO:  need state tracking for touch screen switcher.  Also need to
    // set up output pins
  } else if (command.startsWith("HDLINK_INPUT")) {
    // changes to the next input, or the selected input
    // TODO:  need state tracking for this (which input is selected), and
  #endif // ifdef CAMARO_SCREEN
  }
}

void iwrap_call_hid_device() {
  char  cmd[] = "CALL AA:BB:CC:DD:EE:FF 11 HID"; // HID
  char *cptr  = cmd + 5;

  iwrap_bintohexstr(
    (uint8_t *)(iwrap_connection_map[iwrap_call_index]->mac.address),
    6, &cptr, ':', 0);

  #ifdef TOUCH_DEBUG
  char s[21];
  sprintf(s, "Calling device #%d\r\n", iwrap_call_index);
  serial_out(s);
  #endif // ifdef TOUCH_DEBUG

  iwrap_send_command(cmd, iwrap_mode);
  iwrap_call_last_time = millis();
}

int serial_out(const char *str) {
  // debug output to host goes through hardware serial
  return UsbSerial.print(str);
}

int serial_out(const __FlashStringHelper *str) {
  // debug output to host goes through hardware serial
  return UsbSerial.print(str);
}

int iwrap_out(int len, unsigned char *data) {
  // iWRAP output to module goes through software serial if using Atmega 328,
  // otherwise it goes through uart
  return IwrapSerial.write(data, len);
}
