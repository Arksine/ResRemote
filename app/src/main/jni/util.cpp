//
// Created by Eric on 4/6/2016.
//
#include "util.h"
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <cerrno>
#include <cstring>

int errnoexit(const char *s) {
    LOGE("%s error %d, %s", s, errno, strerror(errno));
    return ERROR_LOCAL;
}

int xioctl(int fd, int request, void *arg) {
    int r;

    do {
        r = ioctl(fd, request, arg);
    } while(-1 == r && EINTR == errno);

    return r;
}