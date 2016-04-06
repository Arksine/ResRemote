//
// Created by Eric on 4/5/2016.
//
#include <jni.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <string.h>
#

extern "C" {

	JNIEXPORT void JNICALL Java_com_arksine_resremote_NativeInput_processEvent(JNIEnv* jenv,
					jobject thisObj, jstring command, jint x, jint y, jint z) {

	}
}