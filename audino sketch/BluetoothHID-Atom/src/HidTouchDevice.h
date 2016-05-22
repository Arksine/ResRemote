// Include guard
#ifndef HIDTOUCHDEVICE_H
#define HIDTOUCHDEVICE_H

#include <arduino.h>

// This multitouch device can register up to 8 individual contacts.  Each must
// have its own identifier, defined below
#define CONTACT_ONE     (1 << 0)
#define CONTACT_TWO     (1 << 1)
#define CONTACT_THREE   (1 << 2)
#define CONTACT_FOUR    (1 << 3)
#define CONTACT_FIVE    (1 << 4)
#define CONTACT_SIX     (1 << 5)
#define CONTACT_SEVEN   (1 << 6)
#define CONTACT_EIGHT   (1 << 7)

#define TIP_SWITCH      (1 << 0)
#define IN_RANGE        (1 << 1)

// TIP_SWITCH is an activated on a finger press.  Some touch screens have the
// capability to detect
// when a tool (pen, finger) is in range, hence the IN_RANGE usage.  It must be
// activated for
// a touch to register.
#define TOUCH_ALL (TIP_SWITCH | IN_RANGE)


typedef union {
  // MultiTouch report: numContacts, contactId, touch, 2 absolute axis
  uint8_t data[];
  struct {
    uint8_t header;      // Bluegiga HID header, 0x9F
    uint8_t length;      // total of all bytes below, so should be 9
    uint8_t filler;      // 0xA1 according to docs TODO: Not sure this is
    // needed
    uint8_t reportId;
    uint8_t numContacts; // Number of contacts currently touching the screen
    uint8_t contactId;   // Id of the contact for this particular report
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
  uint8_t  _contacts;
  uint8_t  _numContacts;
  uint8_t  hidLinkId;

public:

  HidTouchDevice(void);
  void setHidLinkId(uint8_t linkId);
  void begin(void);
  void end(void);

  void click(uint8_t id = CONTACT_ONE);
  void moveTo(int     x,
              int     y,
              uint8_t id = CONTACT_ONE,
              uint8_t t = TOUCH_ALL);
  void press(uint8_t id = CONTACT_ONE);
  void release(uint8_t id = CONTACT_ONE);
  void setNumContacts(uint8_t nCts = 1);
  bool isPressed(uint8_t c);

  // Sending is public in the base class for advanced users.
  void SendReport(HidTouchReport_Data_t *report,
                  uint8_t                length);
};

#endif /* ifndef HIDTOUCHDEVICE_H */
