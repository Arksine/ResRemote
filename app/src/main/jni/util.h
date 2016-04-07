//
// Created by Eric on 4/6/2016.
//

#ifndef RESREMOTE_UTIL_H
#define RESREMOTE_UTIL_H

#include <android/log.h>

#define LOG_TAG "NativeInputJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define CLEAR(x) memset(&(x), 0, sizeof(x))

#define ERROR_LOCAL -1
#define SUCCESS_LOCAL 0

int errnoexit(const char *s);

/* Private: Repeat an ioctl call until it completes and is not interrupted by a
 * a signal.
 *
 * The ioctl may still succeed or fail, so do check the return status.
 *
 * fd - the file descriptor for the ioctl.
 * request - the type of IOCTL to request.
 * arg - the target argument for the ioctl.
 *
 * Returns the status of the ioctl when it completes.
 */
int xioctl(int fd, int request, void *arg);

#endif //RESREMOTE_UTIL_H
