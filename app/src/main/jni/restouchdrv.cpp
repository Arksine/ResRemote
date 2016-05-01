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
const int coordOffset = 3;  // The number of pixels a tool must travel before the touch is registered
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
		uinp.absmax[ABS_MT_PRESSURE] = 1000;

		// Setup the uinput device
		int ret = 0;
		ret = ret | ioctl(uinp_fd, UI_SET_EVBIT, EV_KEY);
		ret = ret | ioctl(uinp_fd, UI_SET_EVBIT, EV_REL);
		ret = ret | ioctl(uinp_fd, UI_SET_EVBIT, EV_SYN);
    
		// Touch
		ret = ret | ioctl (uinp_fd, UI_SET_EVBIT,  EV_ABS);
		ret = ret | ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_SLOT);
		ret = ret | ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_TOUCH_MAJOR);
		ret = ret | ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_POSITION_X);
		ret = ret | ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_POSITION_Y);
		ret = ret | ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID);
		ret = ret | ioctl (uinp_fd, UI_SET_ABSBIT, ABS_MT_PRESSURE);
		ret = ret | ioctl (uinp_fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);

        if (ret < 0) {
        LOGE("Unable to ioctl to device");
        	return false;
        }
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

				if ((x < (prevXCoord - coordOffset)) || (x > (prevXCoord + coordOffset))) {
					send_event(EV_ABS, ABS_MT_POSITION_X, x);
					prevXCoord = x;
					coordChanged = true;
				}

				if ((y < (prevYCoord - coordOffset)) || (y > (prevYCoord + coordOffset))) {
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

void send_event(int type, int code, int value) {
	struct input_event ev;
	memset(&ev, 0, sizeof(ev));
    gettimeofday(&ev.time, nullptr);
    ev.type = type;  
    ev.code = code;
    ev.value = value;
    write(uinp_fd, &ev, sizeof(ev));
}