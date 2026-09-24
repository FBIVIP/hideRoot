LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := roothider
LOCAL_SRC_FILES := module.cpp
LOCAL_LDLIBS    := -llog
LOCAL_CPPFLAGS  := -std=c++17 -fno-exceptions -fno-rtti -fvisibility=hidden
LOCAL_STATIC_LIBRARIES :=
include $(BUILD_SHARED_LIBRARY)
