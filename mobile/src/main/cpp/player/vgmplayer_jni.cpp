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
extern bool EndPlay;

extern UINT32 VGMMaxLoopM;
extern UINT32 VGMMaxLoop;
extern UINT32 FadeTime;

extern CHIPS_OPTION ChipOpts[0x02];
}

// configuration.
#define SMPL_BYTES	4
#define SAMPLE_RATE 44100

static int prepared = 0;
static int playing = 0;
static INT32 decode_pos;				// current decoding position (depends on SampleRate)
static volatile int decode_pos_ms;			// Used for correcting DSP plug-in pitch changes
static volatile int seek_needed;	// if != -1, it is the point that the decode

inline UINT32 MulDivRound(UINT64 Number, UINT64 Numerator, UINT64 Denominator)
{
	return (UINT32)((Number * Numerator + Denominator / 2) / Denominator);
}

extern "C" JNIEXPORT jint JNICALL
Java_net_volcanomobile_vgmplayer_service_player_VgmPlayerInternal_nativeInit(JNIEnv *env, jclass type)
{
	VGMMaxLoop = 2;
	FadeTime = 5000;
	SampleRate = SAMPLE_RATE;

    VGMPlay_Init();

/*
    CHIP_OPTS* ym2612 = (CHIP_OPTS*)&ChipOpts[0x00] + 0x02; // YM2612
    ym2612->EmuCore = 2; // Nuked OPN2

    CHIP_OPTS* ymf262 = (CHIP_OPTS*)&ChipOpts[0x00] + 0x0C; // YMF262
    ymf262->EmuCore = 1; // MAME

    CHIP_OPTS* ym3812 = (CHIP_OPTS*)&ChipOpts[0x00] + 0x0C; // YM3812
    ym3812->EmuCore = 1; // MAME
*/

    VGMPlay_Init2();
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_net_volcanomobile_vgmplayer_service_player_VgmPlayerInternal_nativeRelease(JNIEnv *env, jclass type)
{
	VGMPlay_Deinit();
}

extern "C" JNIEXPORT int JNICALL
Java_net_volcanomobile_vgmplayer_service_player_VgmPlayerInternal_nativePrepare(JNIEnv *env, jclass type, jstring filename)
{
    const char *nativeString;
    nativeString = env->GetStringUTFChars(filename, JNI_FALSE);
	if (!OpenVGMFile(nativeString)) {
		LOGE("error: failed to open vgm_file (%s)\n", nativeString);
    	env->ReleaseStringUTFChars(filename, nativeString);
		return 1;
	}
	env->ReleaseStringUTFChars(filename, nativeString);
	playing = 0;
    prepared = 1;
    return 0;
}

extern "C" JNIEXPORT int JNICALL
Java_net_volcanomobile_vgmplayer_service_player_VgmPlayerInternal_nativeStart(JNIEnv *env, jclass type)
{
    if(!playing) {
        PlayVGM();
        playing = 1;
    }
    return 0;
}

extern "C" JNIEXPORT int JNICALL
Java_net_volcanomobile_vgmplayer_service_player_VgmPlayerInternal_nativeReset(JNIEnv *env, jclass type)
{
    if (prepared) {
        StopVGM();
        CloseVGMFile();
        prepared = 0;
    }

    seek_needed = -1;
    decode_pos = 0;
    decode_pos_ms = 0;
    playing = 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_net_volcanomobile_vgmplayer_service_player_VgmPlayerInternal_nativeFillBuffer(JNIEnv *env, jclass type, jobject buffer)
{
    UINT32 RetSamples;

    if (seek_needed != -1) // seek is needed.
    {
        decode_pos = MulDivRound(seek_needed, SampleRate, 1000);
        decode_pos_ms = seek_needed;
        SeekVGM(false, decode_pos);
        seek_needed = -1;
    }

    if(!EndPlay) {

        jlong buffer_size = env->GetDirectBufferCapacity(buffer);
        int16_t* data = (int16_t*) env->GetDirectBufferAddress(buffer);

        RetSamples = FillBuffer((WAVE_16BS*) data, (UINT32) (buffer_size / SMPL_BYTES));
		decode_pos += RetSamples;
		decode_pos_ms = MulDivRound((UINT64) decode_pos, 1000, SampleRate);

        return (jint) RetSamples * SMPL_BYTES;
    } else {
        playing = 0;
    }

    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_net_volcanomobile_vgmplayer_service_player_VgmPlayerInternal_nativeGetCurrentPosition(JNIEnv *env, jclass type)
{
    return seek_needed != -1 ? seek_needed : decode_pos_ms;
}

extern "C" JNIEXPORT void JNICALL
Java_net_volcanomobile_vgmplayer_service_player_VgmPlayerInternal_nativeSeekTo(JNIEnv *env, jclass type, jint time_ms)
{
    seek_needed = time_ms;
}