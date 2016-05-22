#include "HidTouchDevice.h"
#include "iwrap.h"

HidTouchDevice::HidTouchDevice(void) :
  xAxis(0), yAxis(0), _touch(0), _contacts(0), _numContacts(1), hidLinkId(0xFE)
{
  // Empty
}

void HidTouchDevice::setHidLinkId(uint8_t linkId) {
  hidLinkId = linkId;
}

void HidTouchDevice::begin() {
  // release all buttons
  end();
}

void HidTouchDevice::end(void) {
  _touch = 0;
  moveTo(xAxis, yAxis, CONTACT_ONE, 0);
}

void HidTouchDevice::click(uint8_t id) {
  _touch = TOUCH_ALL;
  moveTo(xAxis, yAxis, id, TOUCH_ALL);
  _touch = 0;
  moveTo(xAxis, yAxis, id, 0);
}

void HidTouchDevice::moveTo(int x, int y, uint8_t id, uint8_t t) {
  xAxis  = x;
  yAxis  = y;
  _touch = t;

  if (_touch == TOUCH_ALL) {
    _contacts = _contacts | id;
  } else {
    _contacts = _contacts & ~id;
  }
  HidTouchReport_Data_t report;
  report.header = 0x9F;
  report.length = 9;

  report.filler = 0xA1;              // TODO:  Make sure this variable is
  // necessary
  report.reportId    = 0x01;
  report.numContacts = _numContacts; // initial testing will limit to one
                                     // contact
  report.contactId = id;
  report.touch     = t;
  report.xAxis     = x;
  report.yAxis     = y;
  SendReport(&report, sizeof(report));
}

void HidTouchDevice::press(uint8_t id) {
  _touch = TOUCH_ALL;
  moveTo(xAxis, yAxis, id, _touch);
}

void HidTouchDevice::release(uint8_t id) {
  _touch = 0;
  moveTo(xAxis, yAxis, id, _touch);
}

void HidTouchDevice::setNumContacts(uint8_t nCts) {
  _numContacts = nCts;
}

bool HidTouchDevice::isPressed(uint8_t id) {
  if ((_contacts && id) > 0) return true;

  return false;
}

void HidTouchDevice::SendReport(HidTouchReport_Data_t *report, uint8_t length) {
  iwrap_send_data(hidLinkId,
                  length,
                  report->data,
                  IWRAP_MODE_MUX);
}
