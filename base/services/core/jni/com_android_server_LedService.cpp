/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "LedService"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/misc.h>
#include <utils/Log.h>
#include <hardware/led_hal.h>

#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>


namespace android
{

static led_device_t* led_device;

/*
注意：C函数比Java里的声明多2个参数: (JNIEnv *env, jclass cls)
*/
jint ledOpen(JNIEnv *env, jobject cls)
{
	jint err;
	hw_module_t* module;
	hw_device_t* device;

	ALOGI("native ledOpen ...");
	/* 1. hw_get_module */
	err = hw_get_module("led", (hw_module_t const**)&module);
	/* 2.get_device: module->methods->open*/
	if (err == 0) {
		err = module->methods->open(module, NULL, &device);
		if (err == 0) {
			led_device = (led_device_t*)device;
			/* 3. call led open */
			return led_device->led_open(led_device);
		} else {
			return -1;
		}
	}

	return -1;
}

void ledClose(JNIEnv *env, jobject cls)
{

}

jint ledCtrl(JNIEnv *env, jobject cls, jint which, jint status)
{
	ALOGI("native ledCtrl led%d:%d",which,status);
	return led_device->led_ctrl(led_device,which,status);

}

/* 
定义一个映射数组JNINativeMethod[]
可以注册多个本地函数
*/

static const JNINativeMethod methods[] = {
    {"native_ledOpen","()I",(void *)ledOpen},
	{"native_ledCtrl","(II)I",(void *)ledCtrl},
	{"native_ledClose","()V",(void *)ledClose},
};

int register_android_server_LedService(JNIEnv *env)
{
    return jniRegisterNativeMethods(env, "com/android/server/LedService",
            methods, NELEM(methods));
}

};
