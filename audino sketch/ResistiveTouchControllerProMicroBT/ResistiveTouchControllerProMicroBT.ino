
/**
 * ResistiveTouchController
 * 
 * A controller that sends x, y, and z data via Serial
 */

#include <TouchScreen.h>
#include <EEPROMex.h>
#include <HID-Project.h>
#include <HID-Settings.h>


#define CONFIG_VERSION "rt1"
#define MEMORYBASE 32 // where to store and retrieve EEPROM memory

#define YP A0   // must be an analog pin, use "An" notation!
#define XM A1   // must be an analog pin, use "An" notation!
#define YM A2   // can be a digital pin
#define XP A3   // can be a digital pin

#define MINPRESSURE 10
#define MAXPRESSURE 1000
#define TOUCHUPDELAY 100  // min number of milliseconds a touch isn't registered before I send touch up
#define READLOOPDELAY 10  // min number of milliseconds between loop reads
#define XPLATE 470             // Resistance across the X-plate of the touchscreen
#define DEVICEMAXPRESSURE 255  // the maximum pressure the device receives.  It is typically 255

// TODO:  need to measure resistance of the x-plate on the camaro touch screen
TouchScreen ts = TouchScreen(XP, YP, XM, YM, XPLATE);

boolean start = false;
boolean isCalibrated = false;
boolean isTouching = false;
unsigned long readLoopTime = 0;  
unsigned long touchTime = 0;
int configAddress = 0;   
String serialBuffer = ""; 

/**
 * StroreStruct
 * Contains the calibration coefficients required to convert touch coordinates to device coordinates.
 * Also contains the measured min resistance and the coefficent required to convert resistance to pressure
 */
struct StoreStruct {
  char version[4];  // unique identifier to make sure we are getting the right data
  long A;     // A-F are coefficients calculated to convert touch coordinates to device coordinates
  long B;
  long C;
  long D;
  long E;
  long F;
  long R;          // resistance coefficiient
  int pressureMax;  // maximum device pressure (typically 255)
  int resMin;      // minimum resistance measured between the plates
  
} storage = {CONFIG_VERSION, 1, 1, 1, 1, 1, 1, 1, MAXPRESSURE, MINPRESSURE};



void setup() {
  Serial1.begin(115200);
  while (!Serial1);
  Serial1.flush();

  EEPROM.setMemPool(MEMORYBASE, EEPROMSizeATmega328);
  configAddress  = EEPROM.getAddress(sizeof(StoreStruct)); // Size of config object 
  isCalibrated = loadConfig();

  Digitizer.begin();
}

void loop() {

  // Check for incoming commands
  checkSerial();

  // main loop, app must tell us to start
  if (start && ((millis() - readLoopTime) >= READLOOPDELAY)) {
    readLoopTime = millis();
    TSPoint p = ts.getPoint();
    
    if (p.z > MINPRESSURE && p.z < MAXPRESSURE) {
      sendConvertedCoordinate(p.x, p.y, p.z);
      isTouching = true;
      touchTime = millis();
     
    } 
    else if (isTouching && ((millis() - touchTime) >= TOUCHUPDELAY)) {
      // I might need to adjust the touch up delay, 50 - 100ms should work
     
        isTouching = false;
        Serial1.print("<UP:0:0:0>");
    }
  }

}

bool loadConfig() {
  EEPROM.readBlock(configAddress, storage);
  return (storage.version == CONFIG_VERSION);
}

/**
 * Converts Touch Screen Coordinates to device coordinates
 */
void sendConvertedCoordinate(int tX, int tY, int tZ) {
    int dX;
    int dY;
    int dZ;

    // Calculate points.  Add  5000 to each coefficient calculation so things are 
    // rounded correctly. 
    dX = ((storage.A * tX) + (storage.B * tY) + storage.C + 5000l) / 10000l;
    dY = ((storage.D * tX) + (storage.E * tY) + storage.F + 5000l) / 10000l;
    dZ = storage.pressureMax - ((((tZ - storage.resMin) * storage.R) + 5000l) / 10000l);

    Serial1.print("<DOWN:");
    Serial1.print(dX);
    Serial1.print(":");
    Serial1.print(dY);
    Serial1.print(":");
    Serial1.print(dZ);
    Serial1.print(">");
  
}

/**
 * Listens for a formatted serial packet
 *
 */
void checkSerial() {
  
  if (Serial1.available() > 0)
  {
    char ch = Serial1.read();

    if (ch == '<') {
      // packet is beginning, clear buffer
      serialBuffer = "";
    }
    else if (ch == '>') {
      // end of packet, parse it
      checkPacket();
    }
    else {
      // part of the stream, add it to the buffer
      serialBuffer += ch;
    }
  }
}

/**
 * takes a formatted incoming serial packet and checks it against valid commands,
 * which are then executed
 */
void checkPacket() {
  
  if (serialBuffer == "") {
    // no command received
    return;
  }
  else if (serialBuffer == "START") {
    // start main loop
    if (!isCalibrated) {
      // set defaults for storage struct, output will be raw coordinates
      strcpy(storage.version, "nnn"); // the version doesnt match, so don't assign CONFIG_VERSION
      storage.A = 10000;
      storage.B = 0;
      storage.C = 0;
      storage.D = 0;
      storage.E = 10000;
      storage.F = 0;
      storage.R = 10000;
      storage.pressureMax = MAXPRESSURE;
      storage.resMin = MINPRESSURE;
      Serial1.write("<LOG:Device not calibrated>");
    }
    start = true;
  }
  else if (serialBuffer == "STOP") {
    start = false;
  }
  else if (serialBuffer == "CAL_POINT") {
    // Get a single point from the touch screen and send it
    start = false;
    //Serial1.print("<LOG:GET_POINT_OK>");
    getPoint();
  }
  else if (serialBuffer == "CAL_PRESSURE") {
    // Get points until the user lifts their finger
    start = false;
    //Serial1.print("<LOG:GET_PRESSURE_OK>");
    delay(500);
    getPressure();
  }
  else if (serialBuffer == "TOGGLE_TS") {
    // Toggles the touch screen switcher
    // TODO: Bring whichever pin is connected to touchscreen to high here
  }
  else if (serialBuffer == "WRITE_CALIBRATION") {
    // write calibration to EEPROM
    isCalibrated = true;
    strcpy(storage.version, CONFIG_VERSION);
    storage.pressureMax = DEVICEMAXPRESSURE; // TODO: Get this from device
    EEPROM.writeBlock(configAddress, storage);
  }  
  else if (serialBuffer == "ERROR") {
    // TODO: should probably handle this error somehow
    return;
  }
  else if(serialBuffer.startsWith("$")) {
    // this is a command to store a calibration variable, we don't need to
    // send it the control character
    storeCalibration(serialBuffer.substring(1));
  }
  else {
    // unknown command
    String logString = serialBuffer;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:UKNOWN COMMAND " + logString + ">";
    Serial1.print(logString);
  }
 
}

/**
 * retreives a single touch screen coordinate for calibration
 */
void getPoint() {
  boolean pointRecd = false;

  while (!pointRecd) {
    TSPoint p = ts.getPoint();
    if (p.z > MINPRESSURE && p.z < MAXPRESSURE) {
      Serial1.print("<CAL:");
      Serial1.print(p.x);
      Serial1.print(":");
      Serial1.print(p.y);
      Serial1.print(":");
      Serial1.print(p.z);
      Serial1.print(">");
      pointRecd = true;
    } 
    delay(10);
  }
}

/**
 * Retreives a set of coordinates for pressure calibration.  It will write data until the user lifts their finger.
 */
void getPressure () {
  boolean touchUp = false;
  isTouching = false;
  while (!touchUp) {
    
    TSPoint p = ts.getPoint();
    if (p.z > MINPRESSURE && p.z < MAXPRESSURE) {
      Serial1.print("<CAL:");
      Serial1.print(p.x);
      Serial1.print(":");
      Serial1.print(p.y);
      Serial1.print(":");
      Serial1.print(p.z);
      Serial1.print(">");
      isTouching = true;
      touchTime = millis();
    
    } 
    else if (isTouching && ((millis() - touchTime) >= TOUCHUPDELAY)) {
        touchUp = true;
        isTouching = false;
        Serial1.print("<STOP:0:0:0>");
    }
    
    delay(10);
  }
}

/**
 * Retreives and writes calibration coefficients calculated by the application to EEPROM
 */
void storeCalibration(String data) {
  isCalibrated = false;
  
  String varType;
  String value;
  
  varType = data.substring(0,1);
  value = data.substring(2);

  if (varType == "A") {
    
    storage.A = atol(value.c_str());
    Serial1.print("<LOG:OK>");
    Serial1.print("<LOG:A="); 
    Serial1.print(storage.A);
    Serial1.print(">");
    
  } else if (varType == "B") {
    
    storage.B = atol(value.c_str());
    Serial1.print("<LOG:OK>");
    Serial1.print("<LOG:B="); 
    Serial1.print(storage.B);
    Serial1.print(">");
    
  } else if (varType == "C") {
    
    storage.C = atol(value.c_str());
    Serial1.print("<LOG:OK>");
    Serial1.print("<LOG:C="); 
    Serial1.print(storage.C);
    Serial1.print(">");
    
  } else if (varType == "D") {

    storage.D = atol(value.c_str());
    Serial1.print("<LOG:OK>");
    Serial1.print("<LOG:D="); 
    Serial1.print(storage.D);
    Serial1.print(">");
    
  } else if (varType == "E") {

    storage.E = atol(value.c_str());
    Serial1.print("<LOG:OK>");
    Serial1.print("<LOG:E="); 
    Serial1.print(storage.E);
    Serial1.print(">");
    
  } else if (varType == "F") {

    storage.F = atol(value.c_str());
    Serial1.print("<LOG:OK>");
    Serial1.print("<LOG:F="); 
    Serial1.print(storage.F);
    Serial1.print(">");
    
  } else if (varType == "R") {

    storage.R = atol(value.c_str());
    Serial1.print("<LOG:OK>");
    Serial1.print("<LOG:RESCOEF="); 
    Serial1.print(storage.R);
    Serial1.print(">");   
  
  } else if (varType == "M") {

    storage.resMin = atoi(value.c_str());
    Serial1.print("<LOG:OK>");
    Serial1.print("<LOG:RESMIN="); 
    Serial1.print(storage.resMin);
    Serial1.print(">");
    
  } else {
    // invalid command
    String logString = data;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:PACKET " + logString + " invalid>";
    Serial1.print(logString);
  }
}



