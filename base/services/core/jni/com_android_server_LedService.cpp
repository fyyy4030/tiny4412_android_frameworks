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
#include <hardware_legacy/vibrator.h>

#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>


namespace android
{


static jint fd1;
static jint fd2;
static jint fd3;
static jint fd4;

char const *const LED1_FILE = "/sys/class/leds/led1/brightness";
char const *const LED2_FILE = "/sys/class/leds/led2/brightness";
char const *const LED3_FILE = "/sys/class/leds/led3/brightness";
char const *const LED4_FILE = "/sys/class/leds/led4/brightness";


/*
注意：C函数比Java里的声明多2个参数: (JNIEnv *env, jclass cls)
*/
jint ledOpen(JNIEnv *env, jobject cls)
{
	fd1 = open(LED1_FILE,O_RDWR);
	if(fd1 < 0){
		ALOGI("native led open led1 errno!");
		return -1;
	}
	fd2 = open(LED2_FILE,O_RDWR);
	if(fd2 < 0){
		ALOGI("native led open led2 errno!");
		return -1;
	}
	fd3 = open(LED3_FILE,O_RDWR);
	if(fd3 < 0){
		ALOGI("native led open led3 errno!");
		return -1;
	}
	fd4 = open(LED4_FILE,O_RDWR);
	if(fd4 < 0){
		ALOGI("native led open led4 errno!");
		return -1;
	}
	ALOGI("native led open success!");

	return 0;
}

void ledClose(JNIEnv *env, jobject cls)
{
	close(fd1);
	close(fd2);
	close(fd3);
	close(fd4);
	ALOGI("native led close ...");

}

jint ledCtrl(JNIEnv *env, jobject cls, jint which, jint status)
{
	int fd = 0;
	int ret = 0;
	char buf[1] = {0};

	if(status){
		buf[0] = '1';
	}else{
		buf[0] = '0';
	}
	switch (which) {
		case 1:
			fd = fd1;
			break;
		case 2:
			fd = fd2;
			break;
		case 3:
			fd = fd3;
			break;
		case 4:
			fd = fd4;
			break;
		default:
			ALOGI("native led ctrl led:%d status:%d is invaild",which,status);
			return -1;
	}
	ret = write(fd,buf,sizeof(buf));
	if(ret < 0){
		ALOGI("native led ctrl led:%d status:%d write fail!!!",which,status);
	}
	ALOGI("native led ctrl led:%d status:%d",which,status);
	return 0;

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
