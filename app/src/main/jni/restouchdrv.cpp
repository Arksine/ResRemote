//
// Created by Eric on 4/5/2016.
//
#include <jni.h>
#include <cstddef>
#include <linux/input.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <sys/time.h>
#include <cstring>
#include "util.h"

void send_event(int type, int code, int value);

static int uinp_fd;			// uinput file descriptor
bool isTouching = false;
int prevXCoord = 0;
int prevYCoord = 0;
const int trackingID = 9000;

// TODO: this is an arbitrary pressure for initial testing, once the app is working 
//		 we will attempt to use pressure calculated from the screen
const int pressure = 50;		

#ifdef __cplusplus
extern "C" {
#endif

	JNIEXPORT jboolean JNICALL Java_com_arksine_resremote_NativeInput_openUinput(JNIEnv* jenv, 
		jobject thisObj, jint screenSizeX, jint screenSizeY) {

		struct uinput_user_dev uinp;
		uinp_fd = open("/dev/uinput", O_WRONLY|O_NONBLOCK);
		if(uinp_fd == -1) {
			 LOGE("Unable to open uinput device");
			 return false;
		}

		// configure touch device event properties
		memset(&uinp, 0, sizeof(uinp));
		strncpy(uinp.name, "ArduinoTouchScreen", UINPUT_MAX_NAME_SIZE);
		uinp.id.version = 4;
		uinp.id.bustype = BUS_USB;
		uinp.absmin[ABS_MT_SLOT] = 0;
		uinp.absmax[ABS_MT_SLOT] = 9; // track up to 9 fingers
		uinp.absmin[ABS_MT_TOUCH_MAJOR] = 0;
		uinp.absmax[ABS_MT_TOUCH_MAJOR] = 15;
		uinp.absmin[ABS_MT_POSITION_X] = 0; // screen dimension
		uinp.absmax[ABS_MT_POSITION_X] = (int)screenSizeX - 1; // screen dimension
		uinp.absmin[ABS_MT_POSITION_Y] = 0; // screen dimension
		uinp.absmax[ABS_MT_POSITION_Y] = (int)screenSizeY - 1; // screen dimension
		uinp.absmin[ABS_MT_TRACKING_ID] = 0;
		uinp.absmax[ABS_MT_TRACKING_ID] = 65535;
		uinp.absmin[ABS_MT_PRESSURE] = 0;
		uinp.absmax[ABS_MT_PRESSURE] = 255;

		// Setup the uinput device
		ioctl(uinp_fd, UI_SET_EVBIT, EV_KEY);
		ioctl(uinp_fd, UI_SET_EVBIT, EV_REL);
		ioctl(uinp_fd, UI_SET_EVBIT, EV_SYN);
    
		// Touch
		ioctl (uinp_fd, UI_SET_EVBIT,  EV_ABS);
		ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_SLOT);
		ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_TOUCH_MAJOR);
		ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_POSITION_X);
		ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_POSITION_Y);
		ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID);
		ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_PRESSURE);    
		ioctl (uinp_fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);

    
		/* Create input device into input sub-system */
		write(uinp_fd, &uinp, sizeof(uinp));
		ioctl(uinp_fd, UI_DEV_CREATE);

		return true;
	}

	JNIEXPORT void JNICALL Java_com_arksine_resremote_NativeInput_processEvent(JNIEnv* jenv, 
		jobject thisObj, jstring command, jint x, jint y, jint z) {

		
		const char* cmd = jenv->GetStringUTFChars(command, 0);

		if (strncmp(cmd, "DOWN", 4) == 0) {		// command finger down

			if (!isTouching) {		// first touch
				isTouching = true;

				send_event(EV_ABS, ABS_MT_TRACKING_ID, trackingID);
				send_event(EV_ABS, ABS_MT_POSITION_X, x);
				send_event(EV_ABS, ABS_MT_POSITION_Y, y);
				send_event(EV_ABS, ABS_MT_PRESSURE, pressure);
				send_event(EV_SYN, SYN_REPORT, 0);

				prevXCoord = x;
				prevYCoord = y;

			}
			else {					// holding
				bool coordChanged = false;  // we need to send a sync event if something has changed

				if (x != prevXCoord) {
					send_event(EV_ABS, ABS_MT_POSITION_X, x);
					prevXCoord = x;
					coordChanged = true;
				}

				if (y != prevYCoord) {
					send_event(EV_ABS, ABS_MT_POSITION_Y, y);
					prevYCoord = y;
					coordChanged = true;
				}

				if (coordChanged) {
					send_event(EV_SYN, SYN_REPORT, 0);
				}
			}
			

		}
		else if (strncmp(cmd, "UP", 2) == 0) {	// command finger up
			isTouching = false;

			// TODO: we are trying -1 first, if that doesn't work we'll try 0xFFFFFFFF
			send_event(EV_ABS, ABS_MT_TRACKING_ID, -1);
			send_event(EV_SYN, SYN_REPORT, 0);
				
		}
		else {	// unknown command
			LOGI("Unknown command %s: ", cmd);
		}

		jenv->ReleaseStringUTFChars(command, cmd);
	}

	JNIEXPORT void JNICALL Java_com_arksine_resremote_NativeInput_closeUinput(JNIEnv* jenv, 
		jobject thisObj) {

		ioctl(uinp_fd, UI_DEV_DESTROY);
		close(uinp_fd);
	}

#ifdef __cplusplus
}
#endif

// TODO:  Intially I will try executing commands one at a time with this function.  If
//		  it isn't working well we'll try writing events in batches via an array of input_events
void send_event(int type, int code, int value) {
	struct input_event ev;
	memset(&ev, 0, sizeof(ev));
    gettimeofday(&ev.time, nullptr);
    ev.type = type;  
    ev.code = code;
    ev.value = value;
    write(uinp_fd, &ev, sizeof(ev));
}