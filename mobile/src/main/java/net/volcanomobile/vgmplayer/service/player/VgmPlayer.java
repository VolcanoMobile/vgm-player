package net.volcanomobile.vgmplayer.service.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by Philippe Simons on 6/13/17.
 */

public class VgmPlayer {

    static {
        System.loadLibrary("vgmplay");
        System.loadLibrary("VGMPlayer_JNI");
    }

    public static final int STATE_IDLE = 1;
    public static final int STATE_BUFFERING = 2;
    public static final int STATE_READY = 3;
    public static final int STATE_ENDED = 4;

    /**
     * Listener of changes in player state.
     */
    public interface EventListener {

        void onPlayerStateChanged(boolean playWhenReady, int playbackState);
        void onAudioSessionId(int audioSessionId);
        void onPlayerError(Exception error);
    }

    private final Handler eventHandler;
    private final Context context;
    private final CopyOnWriteArraySet<EventListener> listeners;
    private boolean playWhenReady;
    private int playbackState;
    private final PlayerInternal internalPlayer;

    public VgmPlayer(Context context) {
        listeners = new CopyOnWriteArraySet<>();
        playWhenReady = false;
        playbackState = STATE_IDLE;
        this.context = context;

        Looper eventLooper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
        eventHandler = new Handler(eventLooper) {
            @Override
            public void handleMessage(Message msg) {
                VgmPlayer.this.handleEvent(msg);
            }
        };

        internalPlayer = new VgmPlayerInternal(context, playWhenReady, eventHandler);
    }

    public int getPlaybackState() {
        return playbackState;
    }

    public void addListener(EventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(EventListener listener) {
        listeners.remove(listener);
    }

    public void setVolume(float volume) {
        internalPlayer.setVolume(volume);
    }

    public void prepare(Uri uri) {
        playbackState = STATE_BUFFERING;
        internalPlayer.prepare(uri);
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        if (this.playWhenReady != playWhenReady) {
            this.playWhenReady = playWhenReady;
            internalPlayer.setPlayWhenReady(playWhenReady);
            for (EventListener listener : listeners) {
                listener.onPlayerStateChanged(playWhenReady, playbackState);
            }
        }
    }

    public boolean getPlayWhenReady() {
        return playWhenReady;
    }

    public void seekTo(long positionMs) {
        internalPlayer.seekTo(positionMs);
    }

    public void release() {
        internalPlayer.release();
        eventHandler.removeCallbacksAndMessages(null);
    }

    public long getCurrentPosition() {
        return internalPlayer.getCurrentPosition();
    }

    private void handleEvent(Message msg) {
        switch (msg.what) {
            case PlayerInternal.MSG_STATE_CHANGED: {
                playbackState = msg.arg1;
                for (EventListener listener : listeners) {
                    listener.onPlayerStateChanged(playWhenReady, playbackState);
                }
                break;
            }
            case PlayerInternal.MSG_ERROR: {
                Exception exception = (Exception) msg.obj;
                for (EventListener listener : listeners) {
                    listener.onPlayerError(exception);
                }
                break;
            }
            case PlayerInternal.MSG_ON_AUDIOSESSION: {
                int sessionId = msg.arg1;
                for (EventListener listener : listeners) {
                    listener.onAudioSessionId(sessionId);
                }
                break;
            }
            default:
                throw new IllegalStateException();
        }
    }
}
