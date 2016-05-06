#ifndef DEFINITIONS_H
#define DEFINITIONS_H

#include <arduino.h>

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

#define CONFIG_VERSION    "rt2"
#define MEMORYBASE        32 // where to store and retrieve EEPROM memory

#define YP                A0 // Purple (must be analog)
#define XM                A1 // Blue (must be analog)
#define YM                A2 // Black (can be digital)
#define XP                A3 // Yellow (can be digital)

#define CALPIN            2  // When this pin is pulled high, device goes into
                             // calibration mode
#define LEDPIN            3

#define MINPRESSURE       10
#define MAXPRESSURE       1000
#define TOUCHUPDELAY      100 // min number of milliseconds a touch isn't
                              // registered
                              // before I send touch up
#define READLOOPDELAY     10  // min number of milliseconds between loop reads
#define XPLATE            470 // Resistance across the X-plate of the
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
