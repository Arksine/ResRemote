/**
 * ResistiveTouchController
 * 
 * A controller that sends x, y, and z data via Serial
 */

#include <TouchScreen.h>
#include <EEPROMex.h>

#define CONFIG_VERSION "rt1"
#define MEMORYBASE 32 // where to store and retrieve EEPROM memory

#define YP A1  // must be an analog pin, use "An" notation!
#define XM A2  // must be an analog pin, use "An" notation!
#define YM 8   // can be a digital pin
#define XP 9   // can be a digital pin

#define MINPRESSURE 10
#define MAXPRESSURE 1000
#define TOUCHUPDELAY 100  // number of milliseconds a touch isn't registered before I send touch up
#define XPLATE 470             // Resistance across the X-plate of the touchscreen
#define DEVICEMAXPRESSURE 255  // the maximum pressure the device receives.  It is typically 255

// TODO:  need to measure resistance of the x-plate on the camaro touch screen
TouchScreen ts = TouchScreen(XP, YP, XM, YM, XPLATE);

boolean start = false;
boolean isCalibrated = false;
boolean isTouching;
unsigned long touchTime = 0;
int configAddress = 0;    

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
  
} storage = {CONFIG_VERSION, 1000, 1000, 1000, 1000, 1000, 1000, 1000, MAXPRESSURE, MINPRESSURE};



void setup() {
  Serial.begin(115200);
  while (!Serial);
  Serial.flush();

  EEPROM.setMemPool(MEMORYBASE, EEPROMSizeATmega328);
  configAddress  = EEPROM.getAddress(sizeof(StoreStruct)); // Size of config object 
  isCalibrated = loadConfig();
  
  isTouching = false;
}

void loop() {

  // Check for incoming commands
  checkCommand();

  // main loop, app must tell us to start
  if (start) {
    TSPoint p = ts.getPoint();
    
    if (p.z > MINPRESSURE && p.z < MAXPRESSURE) {
      sendConvertedCoordinate(p.x, p.y, p.z);
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

bool loadConfig() {
  EEPROM.readBlock(configAddress, storage);
  return (storage.version == CONFIG_VERSION);
}

/**
 * Converts Touch Screen Coordinates to device coordinates
 */
void sendConvertedCoordinate(int tX, int tY, int tZ) {
    long dX;
    long dY;
    long dZ;

    // Calculate points.  Add  500 to each coefficient calculation so things are 
    // rounded correctly.  We divide by 1000 because the coefficient carry 3 decimal places
    dX = (((storage.A * tX) + (storage.B * tY) + storage.C) + 500) / 1000;
    dY = (((storage.D * tX) + (storage.E * tY) + storage.F) + 500) / 1000;
    dZ = storage.pressureMax - ((((tZ - storage.resMin) * storage.R) + 500) / 1000);

    Serial.print("<DOWN:");
    Serial.print(dX);
    Serial.print(":");
    Serial.print(dY);
    Serial.print(":");
    Serial.print(dZ);
    Serial.print(">");
  
}

/**
 * retreives a formatted incoming serial packet and checks it against valid commands,
 * which are then executed
 */
void checkCommand() {

  String command = getSerialPacket();
  
  if (command == "") {
    // no command received
    return;
  }
  else if (command == "START") {
    // start main loop
    if (!isCalibrated) {
      // set defaults for storage struct, output will be raw coordinates
      strcpy(storage.version, "nnn"); // the version doesnt match, so don't assign CONFIG_VERSION
      storage.A = 1000;
      storage.B = 1000;
      storage.C = 1000;
      storage.D = 1000;
      storage.E = 1000;
      storage.F = 1000;
      storage.R = 1000;
      storage.pressureMax = MAXPRESSURE;
      storage.resMin = MINPRESSURE;
      Serial.write("<LOG:Device not calibrated>");
    }
    start = true;
  }
  else if (command == "STOP") {
    start = false;
  }
  else if (command == "CAL_POINT") {
    // Get a single point from the touch screen and send it
    start = false;
    //Serial.print("<LOG:GET_POINT_OK>");
    getPoint();
  }
  else if (command == "CAL_PRESSURE") {
    // Get points until the user lifts their finger
    start = false;
    //Serial.print("<LOG:GET_PRESSURE_OK>");
    delay(500);
    getPressure();
  }
  else if (command == "TOGGLE_TS") {
    // Toggles the touch screen switcher
    // TODO: Bring whichever pin is connected to touchscreen to high here
  }
  else if (command == "STORE_CALIBRATION") {
    storeCalibration();
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

/**
 * Listens for a formatted serial packet
 * TODO: this function should probably write data to a global buffer, then call
 *       a function to check the data against what was received
 */
String getSerialPacket() {
  String data= "";
  
  if (Serial.available() > 0)
  {
    char c = Serial.read();
    
    //valid command, get the string
    if (c == '<') {
      delay(2);
      c = Serial.read();
      while (c != '>') {
        data += c;
        delay(2);
        c = Serial.read();
      }
    }
  }
  return data;
}

/**
 * retreives a single touch screen coordinate for calibration
 */
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

/**
 * Retreives a set of coordinates for pressure calibration.  It will write data until the user lifts their finger.
 */
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

/**
 * Retreives and writes calibration coefficients calculated by the application to EEPROM
 */
void storeCalibration() {
  isCalibrated = false;
  
  String packet;
  String varType;
  String value;
  
  while (!isCalibrated) {
    packet = getSerialPacket();
    varType = packet.substring(0,1);
    value = packet.substring(2);

    if (varType == "A:") {
      
      storage.A = atol(value.c_str());
      Serial.print("<LOG:OK>");
      
    } else if (varType == "B:") {
      
      storage.B = atol(value.c_str());
      Serial.print("<LOG:OK>");
      
    } else if (varType == "C:") {
      
      storage.C = atol(value.c_str());
      Serial.print("<LOG:OK>");
      
    } else if (varType == "D:") {

      storage.D = atol(value.c_str());
      Serial.print("<LOG:OK>");
      
    } else if (varType == "E:") {

      storage.E = atol(value.c_str());
      Serial.print("<LOG:OK>");
      
    } else if (varType == "F:") {

      storage.F = atol(value.c_str());
      Serial.print("<LOG:OK>");
      
    } else if (varType == "R:") {

      storage.R = atol(value.c_str());
      Serial.print("<LOG:OK>");      
    
    } else if (varType == "M:") {

       storage.resMin = value.toInt();
       Serial.print("<LOG:OK>");
      
    } else {
      // invalid command
      String logString = packet;
      logString.replace("<", "[");
      logString.replace(">", "]");
      logString.replace(":", "|");
      logString = "<LOG:PACKET " + logString + " invalid>";
      Serial.print(logString);
    }

    if (packet == "WRITE_CALIBRATION") {

      isCalibrated = true;
      strcpy(storage.version, CONFIG_VERSION);
      storage.pressureMax = DEVICEMAXPRESSURE; // TODO: Get this from device
      EEPROM.writeBlock(configAddress, storage);
      
    } else if (packet == "ERROR") {
      break;
    }
  }
}



