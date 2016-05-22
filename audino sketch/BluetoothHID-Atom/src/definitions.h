#ifndef DEFINITIONS_H
#define DEFINITIONS_H

#include <arduino.h>
#include "iwrap.h"

/*
 * The definitions below denote the device rotation you would like to use.
 * Calibration is done in landscape mode, which
 * is ROTATION_O for desktop OSes.  However on mobile OSes such as android
 * Rotation 0 is Portrait mode.  There are two options
 * available to deal with this, calibrate in portrait mode for mobile OSes or
 * select the  device rotation you would like to use.
 * Recalibration in portrait mode is very difficult for fixed installations, as
 * MHL/Miracast devices do not allow portrait mode,
 * and Chromecast devices scale portrait mode to landscape.
 *
 * So, if you are using a desktop OS the Windows Calibration Tool will always
 * set the rotation to ROTATION_0.  If you are using
 * Android, the calibration tool will determine which orientation the device is
 * in at the time of calibration (either ROTATION_90 or
 * ROTATION_270).  If you wish to change the rotation, simply open the
 * calibration tool, make sure the device is in the orientation you
 * want to use, and select the "Set controller to current orientation" option.
 *
 * One final note...If android properly remapped axes based on the current
 * orientation for devices that aren't orientation aware,
 * all of this would be moot.  It doesn't, and to this date I don't know how to
 * force it to do so, or even if there is a way to force it.
 */
#define ROTATION_0        0
#define ROTATION_90       1
#define ROTATION_180      2
#define ROTATION_270      3

/**
 * IWRAP DEFINITIONS
 */

// While the IWRAP_DEBUG definition prints all data, responses and events
// received from the bluetooth connection, TOUCH_DEBUG prints all serial
// data sent (TXDATA) over the bluetooth RFCOMM channel.  IWRAP_DEBUG should
// be defined for TOUCH_DEBUG to work, as both use the same serial transport
// method
#ifdef IWRAP_DEBUG
# define TOUCH_DEBUG
#endif /* ifdef IWRAP_DEBUG */

#if defined(AVR_PROMICRO16)
  # define MODULE_RESET_PIN            9  // optional connection for MCU-based
                                          // module reset
#elif defined(ARDUINO_AVR_NANO)
  # define MODULE_RESET_PIN            12 // optional connection for MCU-based
                                          // module reset
#endif /* if defined(AVR_PROMICRO16) */

#define HOST_BAUD                   38400 // 38400 shouldn't be too fast for
                                          // ALtSoftSerial on a 16mhz avr
#define IWRAP_BAUD                  38400 // works with 16MHz CPU clock
                                          // (REQUIRES iWRAP RECONFIGURATION,
                                          // DEFAULT IS 115200)


#define IWRAP_STATE_IDLE            0
#define IWRAP_STATE_UNKNOWN         1
#define IWRAP_STATE_PENDING_AT      2
#define IWRAP_STATE_PENDING_SET     3
#define IWRAP_STATE_PENDING_LIST    4
#define IWRAP_STATE_PENDING_CALL    5
#define IWRAP_STATE_COMM_FAILED     255

#define IWRAP_MAX_PAIRINGS          16

// connection map structure
typedef struct {
  iwrap_address_t mac;
  uint8_t         active_links;
  uint8_t         link_hid_control;
  uint8_t         link_hid_interrupt;
  uint8_t         link_spp;

  // other profile-specific link IDs may be added here
} iwrap_connection_t;

/**
 * END IWRAP DEFINITIONS
 */


#define CONFIG_VERSION    "rt2"
#define MEMORYBASE        32 // where to store and retrieve EEPROM
                             // memory

#define YP                A0 // Red (must be analog)
#define XM                A1 // White (must be analog)
#define YM                A2 // Green (can be digital)
#define XP                A3 // Black (can be digital)

#define LEDPIN 3
#define TOUCH_SCREEN_TOGGLE_PIN 4

#define MINPRESSURE       10
#define MAXPRESSURE       1000
#define TOUCHUPDELAY      100 // min number of milliseconds a touch isn't
                              // registered
                              // before I send touch up
#define READLOOPDELAY     10  // min number of milliseconds between loop reads
#define XPLATE            800 // Resistance across the X-plate of the
                              // touchscreen

struct StoreStruct {
  char version[4];            // unique identifier to make sure we are getting
                              // the right data

  // A-F are coefficients calculated to convert touch coordinates to device
  // coordinates
  long A;
  long B;
  long C;
  long D;
  long E;
  long F;
  int  minResistance;
  byte rotation;
};

#endif /* ifndef DEFINITIONS_H */
