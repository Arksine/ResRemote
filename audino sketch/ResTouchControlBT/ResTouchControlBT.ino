#include <TouchScreen.h>

#define YP A1  // must be an analog pin, use "An" notation!
#define XM A2  // must be an analog pin, use "An" notation!
#define YM 8   // can be a digital pin
#define XP 9   // can be a digital pin
#define XPLATE 470  // Resistance across the X-plate of the touchscreen

// TODO:  need to measure resistance of the x-plate on the camaro touch screen
TouchScreen ts = TouchScreen(XP, YP, XM, YM, XPLATE);

boolean start = false;
boolean isTouching;

void setup() {
  Serial.begin(115200);
  while (!Serial);
  Serial.flush();
  
  checkStart();
  isTouching = false;
}

void loop() {

  // TODO: add ability to receive commands, one to turn on the touchscreen

  TSPoint p = ts.getPoint();
  
  if (p.z > ts.pressureThreshhold) {
    Serial.print("<DOWN:");
    Serial.print(p.x);
    Serial.print(":");
    Serial.print(p.y);
    Serial.print(":");
    Serial.print(p.z);
    Serial.print(">");
    isTouching = true;
   
  } 
  else {
    if (isTouching) {
      isTouching = false;
      Serial.print("<UP:0:0:0>");
    }
  }

  //TODO: Get command to toggle touchscreen switcher on/off here.  
  
  delay(10);
}

void checkStart() {
  start = false;
  String command;

  while (!start) {
    command = getCommand();

    if (command == "<START>") {
      start = true;
    }
    else if (command == "<CAL_POINT>") {
      // Get a single point from the touch screen and send it
      getPoint();
    }
    else if (command == "<CAL_PRESSURE>") {
      // Get points until the user lifts their finger
      getPressure();
    }

    delay(10);
  }
}

String getCommand() {
  String command = "";
  
  while (Serial.available() > 0)
  {
    delay(2);
    char c = Serial.read();
    command += c;
  }

  return command;
}

void getPoint() {
  boolean pointRecd = false;

  while (!pointRecd) {
    TSPoint p = ts.getPoint();
    if (p.z > ts.pressureThreshhold) {
      Serial.print("<CAL:");
      Serial.print(p.x);
      Serial.print(":");
      Serial.print(p.y);
      Serial.print(":");
      Serial.print(p.z);
      Serial.print(">");
      pointRecd = true;
    } 
    delay(10);
  }
}

void getPressure () {
  boolean touchUp = false;
  isTouching = false;
  while (!touchUp) {
    
    TSPoint p = ts.getPoint();
    if (p.z > ts.pressureThreshhold) {
      Serial.print("<CAL:");
      Serial.print(p.x);
      Serial.print(":");
      Serial.print(p.y);
      Serial.print(":");
      Serial.print(p.z);
      Serial.print(">");
      isTouching = true;
     
    } 
    else {
      if (isTouching) {
        touchUp = true;
        isTouching = false;
        Serial.print("<STOP:0:0:0>");
      }
    }
    
    delay(10);
  }
  
}


