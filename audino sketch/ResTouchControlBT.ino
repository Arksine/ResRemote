#include <TouchScreen.h>

#define YP A1  // must be an analog pin, use "An" notation!
#define XM A2  // must be an analog pin, use "An" notation!
#define YM 8   // can be a digital pin
#define XP 9   // can be a digital pin

TouchScreen ts = TouchScreen(XP, YP, XM, YM, 470);

boolean start = false;
boolean isTouching;

void setup() {
  Serial.begin(38400);
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
  
  delay(10);
}

void checkStart() {
  start = false;
  String command;

  while (!start) {
    command = "";
    // read serial commands from the tablet if available
    while (Serial.available() > 0)
    {
      delay(2);
      char c = Serial.read();
      command += c;
    }

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
  }
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


