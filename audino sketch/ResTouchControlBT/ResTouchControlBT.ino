#include <TouchScreen.h>

#define YP A1  // must be an analog pin, use "An" notation!
#define XM A2  // must be an analog pin, use "An" notation!
#define YM 8   // can be a digital pin
#define XP 9   // can be a digital pin

#define MINPRESSURE 10
#define MAXPRESSURE 1000
#define TOUCHUPDELAY 100  // number of milliseconds a touch isn't registered before I send touch up
#define XPLATE 470  // Resistance across the X-plate of the touchscreen

// TODO:  need to measure resistance of the x-plate on the camaro touch screen
TouchScreen ts = TouchScreen(XP, YP, XM, YM, XPLATE);

boolean start = false;
boolean isTouching;
unsigned long touchTime = 0;


void setup() {
  Serial.begin(115200);
  while (!Serial);
  Serial.flush();
  
  isTouching = false;
}

void loop() {

  // Check for incoming commands
  checkCommand();

  // main loop, only execute if start is true
  if (start) {
    TSPoint p = ts.getPoint();
    
    if (p.z > MINPRESSURE && p.z < MAXPRESSURE) {
      Serial.print("<DOWN:");
      Serial.print(p.x);
      Serial.print(":");
      Serial.print(p.y);
      Serial.print(":");
      Serial.print(p.z);
      Serial.print(">");
      isTouching = true;
      touchTime = millis();
     
    } 
    else if (isTouching && ((millis() - touchTime) > TOUCHUPDELAY)) {
      // I might need to adjust the touch up delay, 50 - 100ms should work
     
        isTouching = false;
        Serial.print("<UP:0:0:0>");
    }
  }

  delay(10);
}


void checkCommand() {

  String command = getCommand();
  
  if (command == "") {
    // no command received
    return;
  }
  else if (command == "<START>") {
    // start main loop
    start = true;
  }
  else if (command == "<STOP>") {
    start = false;
  }
  else if (command == "<CAL_POINT>") {
    // Get a single point from the touch screen and send it
    start = false;
    //Serial.print("<LOG:GET_POINT_OK>");
    getPoint();
  }
  else if (command == "<CAL_PRESSURE>") {
    // Get points until the user lifts their finger
    start = false;
    //Serial.print("<LOG:GET_PRESSURE_OK>");
    delay(500);
    getPressure();
  }
  else if (command == "<TOGGLE_TS>") {
    // TODO: Bring whichever pin is connected to touchscreen to high here
  }
  else {
    // unknown command
    String logString = command;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:UKNOWN COMMAND " + logString + ">";
    Serial.print(logString);
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
    if (p.z > MINPRESSURE && p.z < MAXPRESSURE) {
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
    if (p.z > MINPRESSURE && p.z < MAXPRESSURE) {
      Serial.print("<CAL:");
      Serial.print(p.x);
      Serial.print(":");
      Serial.print(p.y);
      Serial.print(":");
      Serial.print(p.z);
      Serial.print(">");
      isTouching = true;
      touchTime = millis();
    
    } 
    else if (isTouching && ((millis() - touchTime) > TOUCHUPDELAY)) {
        touchUp = true;
        isTouching = false;
        Serial.print("<STOP:0:0:0>");
    }
    
    delay(10);
  }
  
}


