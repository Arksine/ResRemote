/**
 * ResistiveTouchController
 * 
 * A controller that sends x, y, and z data via Serial
 */

#include <TouchScreen.h>  
#include <EEPROMex.h>
#include <HID-Project.h>
#include <HID-Settings.h>

/*
 * The definitions below denote the device rotation you would like to use.  Calibration is done in landscape mode, which
 * is ROTATION_O for desktop OSes.  However on mobile OSes such as android Rotation 0 is Portrait mode.  There are two options
 * available to deal with this, calibrate in portrait mode for mobile OSes or select the  device rotation you would like to use. 
 * Recalibration in portrait mode is very difficult for fixed installations, as MHL/Miracast devices do not allow portrait mode, 
 * and Chromecast devices scale portrait mode to landscape.
 * 
 * So, if you are using a desktop OS the Windows Calibration Tool will always set the rotation to ROTATION_0.  If you are using
 * Android, the calibration tool will determine which orientation the device is in at the time of calibration (either ROTATION_90 or
 * ROTATION_270).  If you wish to change the rotation, simply open the calibration tool, make sure the device is in the orientation you
 * want to use, and select the "Set controller to current orientation" option.
 * 
 * One final note...If android properly remapped axes based on the current orientation for devices that aren't orientation aware, 
 * all of this would be moot.  It doesn't, and to this date I don't know how to force it to do so, or even if there is a way to force it.
 */
#define ROTATION_0    0
#define ROTATION_90   1
#define ROTATION_180  2
#define ROTATION_270  3

#define CONFIG_VERSION "rt2"
#define MEMORYBASE 32 // where to store and retrieve EEPROM memory

#define YP A0   // must be an analog pin, use "An" notation!
#define XM A1   // must be an analog pin, use "An" notation!
#define YM A2   // can be a digital pin
#define XP A3   // can be a digital pin

#define CALPIN 2  // When this pin is pulled high, device goes into calibration mode
#define LEDPIN 3

#define MINPRESSURE 10
#define MAXPRESSURE 1000
#define TOUCHUPDELAY 100  // min number of milliseconds a touch isn't registered before I send touch up
#define READLOOPDELAY 10  // min number of milliseconds between loop reads
#define XPLATE 470             // Resistance across the X-plate of the touchscreen

// TODO:  need to measure resistance of the x-plate on the camaro touch screen
TouchScreen ts = TouchScreen(XP, YP, XM, YM, XPLATE);

boolean start;
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
  int minResistance;
  byte rotation;
} storage = {CONFIG_VERSION, 1, 1, 1, 1, 1, 1, 1, ROTATION_0};

void setup() {
  
  pinMode(CALPIN, INPUT);
  pinMode(LEDPIN, OUTPUT);
 
  EEPROM.setMemPool(MEMORYBASE, EEPROMSizeATmega32u4);
  configAddress  = EEPROM.getAddress(sizeof(StoreStruct)); // Size of config object 
  isCalibrated = loadConfig();

  if (!isCalibrated) {
    // set defaults for storage struct, output will be raw coordinates * 10
    strcpy(storage.version, CONFIG_VERSION);
    storage.A = 100000;
    storage.B = 0;
    storage.C = 0;
    storage.D = 0;
    storage.E = 100000;
    storage.F = 0;
    storage.minResistance = MINPRESSURE;

    start = false;

    //Serial.write("<LOG:Device not calibrated>");
    
    
  } else {

    //Serial.write("<LOG:Device calibrated>");
    start = true;
    MultiTouch.begin();
  }
}

void loop() {

  // go into calibration mode if the calibration pin is pulled high
  if (digitalRead(CALPIN) == HIGH) {
    // The switch is momentary, so wait for the pin to go low before continuing
    while (digitalRead(CALPIN) == HIGH) {
      delay(10);
    }
    calibrate();
  }

  // main loop, app must tell us to start
  if (start && (millis() - readLoopTime) >= READLOOPDELAY) {
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
        MultiTouch.release();
    }
  }

}

bool loadConfig() {
  EEPROM.readBlock(configAddress, storage);
  /*Serial.write("<LOG:Sketch Version-");
  Serial.write(CONFIG_VERSION);
  Serial.write(">");
  Serial.write("<LOG:Storage Version-");
  Serial.write(storage.version);
  Serial.write(">");*/
  return (strcmp(storage.version, CONFIG_VERSION) == 0);
}



/**
 * Converts Touch Screen Coordinates to device coordinates
 */
void sendConvertedCoordinate(int tX, int tY, int tZ) {
    int dX;
    int dY;
    int dZ;

    switch (storage.rotation) {
      case ROTATION_0:
        dX = ((storage.A * tX) + (storage.B * tY) + storage.C + 5000l) / 10000l;
        dY = ((storage.D * tX) + (storage.E * tY) + storage.F + 5000l) / 10000l;  
        break;
      case ROTATION_90:
        dY = ((storage.A * tX) + (storage.B * tY) + storage.C + 5000l) / 10000l;
        dX = 10000 - (((storage.D * tX) + (storage.E * tY) + storage.F + 5000l) / 10000l);
        break;
      case ROTATION_180:
        dX = 10000 - ((storage.A * tX) + (storage.B * tY) + storage.C + 5000l) / 10000l;
        dY = 10000 - ((storage.D * tX) + (storage.E * tY) + storage.F + 5000l) / 10000l;  
        break;
      case ROTATION_270:
        dY = 10000 - (((storage.A * tX) + (storage.B * tY) + storage.C + 5000l) / 10000l);
        dX = ((storage.D * tX) + (storage.E * tY) + storage.F + 5000l) / 10000l;  
        break;
      default:
        dX = ((storage.A * tX) + (storage.B * tY) + storage.C + 5000l) / 10000l;
        dY = ((storage.D * tX) + (storage.E * tY) + storage.F + 5000l) / 10000l;  
      
    }

    dZ = MAXPRESSURE - (tZ - storage.minResistance);

    MultiTouch.moveTo(dX, dY);
}

void calibrate() {
  digitalWrite(LEDPIN, HIGH);
    
  MultiTouch.end();
  
  // Open the serial port
  Serial.begin(9600);
  while (!Serial) {
    // If we accidentally enter serial mode without a connection, we can exit with the
    // same switch we used to enter
    if (digitalRead(CALPIN) == HIGH) {
      // The switch is momentary, so wait for the pin to go low before continuing
      while (digitalRead(CALPIN) == HIGH) {
        delay(10);      
      }
      goto quit;   // bypass the serial functions and exit
    }
  }
  Serial.flush();

  while(checkSerial()) {
    // The user can use the same momentary button that enters calibration to exit
    // calibration
    if (digitalRead(CALPIN) == HIGH) {
      // The switch is momentary, so wait for the pin to go low before continuing
      while (digitalRead(CALPIN) == HIGH) {
        delay(20);      
      }
      break;
    }
  }

  quit:
  digitalWrite(LEDPIN, LOW);

  if (isCalibrated) {
    start = true;  // go ahead and restart the loop
    MultiTouch.begin();
  }

  Serial.end();
}

/**
 * Listens for a formatted serial packet
 *
 */
boolean checkSerial() {

  boolean calResume = true;
  
  if (Serial.available() > 0)
  {
    char ch = Serial.read();

    if (ch == '<') {
      // packet is beginning, clear buffer
      serialBuffer = "";
    }
    else if (ch == '>') {
      // end of packet, parse it
      calResume = checkPacket();
    }
    else {
      // part of the stream, add it to the buffer
      serialBuffer += ch;
    }
  }

  return calResume;
}

/**
 * takes a formatted incoming serial packet and checks it against valid commands,
 * which are then executed
 */
boolean checkPacket() {

  boolean calResume = true;
  
  if (serialBuffer == "") {
    // no command received
  }
  else if (serialBuffer == "CAL_POINT") {
    // Get a single point from the touch screen and send it
    start = false;
    //Serial.print("<LOG:GET_POINT_OK>");
    getPoint();
  }
  else if (serialBuffer == "CAL_PRESSURE") {
    // Get points until the user lifts their finger
    start = false;
    //Serial.print("<LOG:GET_PRESSURE_OK>");
    delay(500);
    getPressure();
  }
  else if (serialBuffer == "TOGGLE_TS") {
    // Toggles the touch screen switcher
    // TODO: Bring whichever pin is connected to touchscreen to high here
  }
  else if (serialBuffer == "CAL_FAIL") {
    // break calibration loop
    calResume = false;
  }
  else if (serialBuffer == "CAL_SUCCESS") {
    
    EEPROM.updateBlock(configAddress, storage);
    isCalibrated = true;
    calResume = false;
  }
  else if (serialBuffer == "ERROR") {
    // TODO: should probably handle this error somehow
    calResume = false;
  }
  else if(serialBuffer.startsWith("$")) {
    // this is a command to store a calibration variable, we don't need to
    // send it the control character
    storeCalibration(serialBuffer.substring(1));
  }
  else if (serialBuffer.startsWith("SET_ROTATION")) {
    storage.rotation = atoi(serialBuffer.substring(13).c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:Rotation="); 
    Serial.print(storage.rotation);
    Serial.print(">");
  }
  else {
    // unknown command
    String logString = serialBuffer;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:UKNOWN COMMAND " + logString + ">";
    Serial.print(logString);
  }

  return calResume;
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
    else if (isTouching && ((millis() - touchTime) >= TOUCHUPDELAY)) {
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
void storeCalibration(String data) {
  
  String varType;
  String value;
  
  varType = data.substring(0,1);
  value = data.substring(2);

  if (varType == "A") {
    
    storage.A = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:A="); 
    Serial.print(storage.A);
    Serial.print(">");
    
  } else if (varType == "B") {
    
    storage.B = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:B="); 
    Serial.print(storage.B);
    Serial.print(">");
    
  } else if (varType == "C") {
    
    storage.C = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:C="); 
    Serial.print(storage.C);
    Serial.print(">");
    
  } else if (varType == "D") {

    storage.D = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:D="); 
    Serial.print(storage.D);
    Serial.print(">");
    
  } else if (varType == "E") {

    storage.E = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:E="); 
    Serial.print(storage.E);
    Serial.print(">");
    
  } else if (varType == "F") {

    storage.F = atol(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:F="); 
    Serial.print(storage.F);
    Serial.print(">");
    
  } else if (varType == "M") {

    storage.minResistance = atoi(value.c_str());
    Serial.print("<LOG:OK>");
    Serial.print("<LOG:minResistance="); 
    Serial.print(storage.minResistance);
    Serial.print(">");
    
  } else {
    // invalid command
    String logString = data;
    logString.replace("<", "[");
    logString.replace(">", "]");
    logString.replace(":", "|");
    logString = "<LOG:PACKET " + logString + " invalid>";
    Serial.print(logString);
  }
}



