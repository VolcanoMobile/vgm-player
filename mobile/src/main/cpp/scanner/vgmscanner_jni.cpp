#include <jni.h>

#include <android/log.h>
#define APPNAME "VGMPlayer_JNI"

#define LOGD(...) \
  __android_log_print(ANDROID_LOG_DEBUG, APPNAME, __VA_ARGS__)
#define LOGI(...) \
  __android_log_print(ANDROID_LOG_INFO, APPNAME, __VA_ARGS__)
#define LOGE(...) \
  __android_log_print(ANDROID_LOG_ERROR, APPNAME, __VA_ARGS__)

extern "C" {
#include "chips/mamedef.h"
#include "stdbool.h"
#include "VGMPlay.h"
#include "VGMPlay_Intf.h"

extern VGM_HEADER VGMHead;
extern UINT32 SampleRate;

extern UINT32 VGMMaxLoopM;
extern UINT32 VGMMaxLoop;
extern UINT32 FadeTime;
extern UINT32 PauseTime;
}

// configuration.
#define SAMPLE_RATE 44100

extern "C" jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

	VGMMaxLoop = 2;
	FadeTime = 5000;
	SampleRate = SAMPLE_RATE;

    return JNI_VERSION_1_6;
}

static int GetFileLength(VGM_HEADER* FileHead)
{
	UINT32 SmplCnt;
	UINT32 MSecCnt;

	if (! VGMMaxLoopM && FileHead->lngLoopSamples)
		return -1;

	// Note: SmplCnt is ALWAYS 44.1 KHz, VGM's native sample rate
	SmplCnt = FileHead->lngTotalSamples + FileHead->lngLoopSamples * (VGMMaxLoopM - 0x01);
	if (FileHead == &VGMHead)
		MSecCnt = CalcSampleMSec(SmplCnt, 0x02);
	else
		MSecCnt = CalcSampleMSecExt(SmplCnt, 0x02, FileHead);

	if (FileHead->lngLoopSamples)
		MSecCnt += FadeTime + PauseTime;
	else
		MSecCnt += PauseTime;

	return MSecCnt;
}

extern "C" JNIEXPORT jint JNICALL
Java_net_volcanomobile_vgmplayer_service_RemoteVGMScannerService_nativeGetDuration(JNIEnv *env, jclass type, jstring filename)
{
    int msec = 0;
    const char *nativeString;
	UINT32 FileSize;
	VGM_HEADER FileHead;

	VGMPlay_Init();
	VGMPlay_Init2();

    nativeString = env->GetStringUTFChars(filename, JNI_FALSE);

	if (!OpenVGMFile(nativeString)) {
		LOGE("error: failed to open vgm_file (%s)\n", nativeString);
    	env->ReleaseStringUTFChars(filename, nativeString);
		return -1;
	}

    PlayVGM();
    FileSize = GetVGMFileInfo(nativeString, &FileHead, 0);
    if (FileSize) {
        msec = GetFileLength(&FileHead);
    }
    env->ReleaseStringUTFChars(filename, nativeString);
	StopVGM();

	CloseVGMFile();

	VGMPlay_Deinit();

    return msec;
}
