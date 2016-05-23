#include "HidTouchDevice.h"
#include "definitions.h"

HidTouchDevice::HidTouchDevice(void) :
  xAxis(0), yAxis(0), _touch(0), hidLinkId(0xFE)
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
  moveTo(xAxis, yAxis, 0);
}

void HidTouchDevice::click(uint8_t t) {
  _touch = t;
  moveTo(xAxis, yAxis, t);
  _touch = 0;
  moveTo(xAxis, yAxis, 0);
}

void HidTouchDevice::moveTo(int x, int y,  uint8_t t) {
  xAxis  = x;
  yAxis  = y;
  _touch = t;

  HidTouchReport_Data_t report;
  report.header  = 0x9F;
  report.length  = 6;
  report.btStart = 0xA1;
  report.touch   = t;
  report.xAxis   = x;
  report.yAxis   = y;
  SendReport(&report);
}

void HidTouchDevice::press() {
  _touch = TOUCH_ALL;
  moveTo(xAxis, yAxis, _touch);
}

void HidTouchDevice::release() {
  _touch = 0;
  moveTo(xAxis, yAxis, _touch);
}

bool HidTouchDevice::isPressed() {
  if (_touch == TOUCH_ALL) return true;

  return false;
}

void HidTouchDevice::SendReport(HidTouchReport_Data_t *report) {
  #ifdef TOUCH_DEBUG
  char  out[] = "<X:0000 Y:0000> ";
  char *cptr  = out + 3;
  iwrap_bintohexstr((report->data + 4), 2, &cptr, 0, 0);
  cptr = out + 10;
  iwrap_bintohexstr((report->data + 6), 2, &cptr, 0, 0);
  Serial.print(out);
  Serial.print("<X:");
  Serial.print(report->xAxis);
  Serial.print(" Y:");
  Serial.print(report->yAxis);
  Serial.println(">");
  #endif // ifdef TOUCH_DEBUG

  iwrap_send_data(hidLinkId,
                  sizeof(HidTouchReport_Data_t),
                  report->data,
                  IWRAP_MODE_MUX);
}
