NDK_TOOLCHAIN_VERSION := clang
APP_ABI := armeabi-v7a x86
#  Enable C++11. However, pthread, rtti and exceptions arent enabled
APP_CPPFLAGS += -std=c++11
APP_PLATFORM := android-21
APP_STL := stlport_static
