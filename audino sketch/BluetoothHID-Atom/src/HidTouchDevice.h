// Include guard
#ifndef HIDTOUCHDEVICE_H
#define HIDTOUCHDEVICE_H

#include <arduino.h>


#define TIP_SWITCH      (1 << 0)
#define IN_RANGE        (1 << 1)

// TIP_SWITCH is an activated on a finger press.  Some touch screens have the
// capability to detect
// when a tool (pen, finger) is in range, hence the IN_RANGE usage.  It must be
// activated for
// a touch to register.
#define TOUCH_ALL (TIP_SWITCH | IN_RANGE)

// TODO: Upload and see what happens with the 2nd 0xA1
typedef union {
  uint8_t data[];
  struct {
    uint8_t header;  // Bluegiga HID header, 0x9F
    uint8_t length;  // total of all bytes below, so should be 9
    uint8_t btStart; // 0xA1 according to docs
    uint8_t touch;
    int16_t xAxis;
    int16_t yAxis;
  };
} HidTouchReport_Data_t;


class HidTouchDevice
{
protected:

  uint16_t xAxis;
  uint16_t yAxis;
  uint8_t  _touch;
  uint8_t  hidLinkId;

public:

  HidTouchDevice(void);
  void setHidLinkId(uint8_t linkId);
  void begin(void);
  void end(void);

  void click(uint8_t t = TOUCH_ALL);
  void moveTo(int     x,
              int     y,
              uint8_t t = TOUCH_ALL);
  void press();
  void release();
  bool isPressed();

  // Sending is public in the base class for advanced users.
  void SendReport(HidTouchReport_Data_t *report);
};

#endif /* ifndef HIDTOUCHDEVICE_H */
