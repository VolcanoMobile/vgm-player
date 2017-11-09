package net.volcanomobile.vgmplayer.service.player;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;

import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Created by Philippe Simons on 6/13/17.
 */

class VgmPlayerInternal extends PlayerInternal {

    private static final String TAG = "VgmPlayerInternal";

    VgmPlayerInternal(Context context, boolean playWhenReady, Handler eventHandler) {
        super(context, playWhenReady, eventHandler);
    }

    @Override
    public long getCurrentPosition() {
        return nativeGetCurrentPosition();
    }

    @Override
    void seekToInternal(long positionMs) {
        nativeSeekTo((int) positionMs);
    }

    @Override
    void init() {
        if(nativeInit() != 0) {
            eventHandler.obtainMessage(MSG_ERROR, new RuntimeException("Init failed")).sendToTarget();
            stopInternal();
        }
    }

    @Override
    boolean canSeek() {
        return true;
    }

    private native int nativeInit();

    @Override
    native int nativePrepare(String fileName);

    @Override
    native void nativeRelease();

    @Override
    native int nativeStart();

    @Override
    native void nativeReset();

    @Override
    native int nativeFillBuffer(ByteBuffer buffer);
    private native int nativeGetCurrentPosition();
    private native void nativeSeekTo(int tick);
}
