#include <jni.h>

#include <android/log.h>
#define APPNAME "CastPlayer_JNI"

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
extern bool EndPlay;

extern UINT32 VGMMaxLoopM;
extern UINT32 VGMMaxLoop;
extern UINT32 FadeTime;
}

// configuration.
#define NUM_CHN		2
#define BIT_PER_SEC	16
#define SMPL_BYTES	(NUM_CHN * (BIT_PER_SEC / 8))

#define SAMPLE_RATE 44100

#define RENDER_SAMPLES	1024
#define BLOCK_SIZE		(RENDER_SAMPLES * SMPL_BYTES)

static char tmp[BLOCK_SIZE * 2]; // must match VGMPlayer::BUFFER_SIZE

extern "C" JNIEXPORT void JNICALL
Java_net_volcanomobile_vgmplayer_service_player_CastPlayback_nativeRelease(JNIEnv *env, jclass type)
{
	StopVGM();
	CloseVGMFile();
	VGMPlay_Deinit();
}

extern "C" JNIEXPORT void JNICALL
Java_net_volcanomobile_vgmplayer_service_player_CastPlayback_nativePrepare(JNIEnv *env, jclass type, jstring filename)
{
	VGMMaxLoop = 2;
	FadeTime = 5000;
	SampleRate = SAMPLE_RATE;

	VGMPlay_Init();
	VGMPlay_Init2();

    const char *nativeString;
    nativeString = env->GetStringUTFChars(filename, JNI_FALSE);
	if (!OpenVGMFile(nativeString)) {
		LOGE("error: failed to open vgm_file (%s)\n", nativeString);
    	env->ReleaseStringUTFChars(filename, nativeString);
		return;
	}
	env->ReleaseStringUTFChars(filename, nativeString);

    PlayVGM();
}

extern "C" JNIEXPORT jint JNICALL
Java_net_volcanomobile_vgmplayer_service_player_CastPlayback_nativeFillBuffer(JNIEnv *env, jclass type, jbyteArray buffer)
{
    UINT32 RetSamples;

    if(!EndPlay) {
        RetSamples = FillBuffer((WAVE_16BS*) tmp, RENDER_SAMPLES);
        env->SetByteArrayRegion(buffer, 0, RetSamples * SMPL_BYTES, (jbyte*)tmp);
    } else {
        return 0;
    }

    return RetSamples * SMPL_BYTES;
}
