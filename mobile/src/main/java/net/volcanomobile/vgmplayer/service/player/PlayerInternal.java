package net.volcanomobile.vgmplayer.service.player;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

import net.volcanomobile.vgmplayer.utils.LogHelper;

import java.nio.ByteBuffer;

/**
 * Created by Philippe Simons on 6/13/17.
 */

abstract class PlayerInternal implements Handler.Callback {

    private static final String TAG = "PlayerInternal";

    /**
     * The minimum increment of time to wait for an AudioTrack to finish
     * playing.
     */
    private static final long MIN_SLEEP_TIME_MS = 20;
    /**
     * The maximum increment of time to sleep while waiting for an AudioTrack
     * to finish playing.
     */
    private static final long MAX_SLEEP_TIME_MS = 2500;
    /**
     * The maximum amount of time to wait for an audio track to make progress while
     * it remains in PLAYSTATE_PLAYING. This should never happen in normal usage, but
     * could happen in exceptional circumstances like a media_server crash.
     */
    private static final long MAX_PROGRESS_WAIT_MS = MAX_SLEEP_TIME_MS;

    /**
     * @see AudioTrack#WRITE_NON_BLOCKING
     */
    @SuppressLint("InlinedApi")
    private static final int WRITE_NON_BLOCKING = AudioTrack.WRITE_NON_BLOCKING;

    /**
     * A minimum length for the {@link AudioTrack} buffer, in microseconds.
     */
    private static final long MIN_BUFFER_DURATION_US = 250000L;
    /**
     * A maximum length for the {@link AudioTrack} buffer, in microseconds.
     */
    private static final long MAX_BUFFER_DURATION_US = 750000L;
    /**
     * A multiplication factor to apply to the minimum buffer size requested by the underlying
     * {@link AudioTrack}.
     */
    private static final int BUFFER_MULTIPLICATION_FACTOR = 4;

    private static final long MICROS_PER_SECOND = 1000000L;

    // Internal messages
    private static final int MSG_PREPARE = 0;
    private static final int MSG_SET_PLAY_WHEN_READY = 1;
    private static final int MSG_DO_SOME_WORK = 2;
    private static final int MSG_SEEK_TO = 3;
    private static final int MSG_RELEASE = 6;

    // External messages
    static final int MSG_STATE_CHANGED = 1;
    static final int MSG_ON_AUDIOSESSION = 2;
    static final int MSG_ERROR = 8;

    final Handler eventHandler;
    private final HandlerThread internalPlaybackThread;
    private final Handler handler;
    protected final Context context;

    private final AudioTrackUtil audioTrackUtil;
    private final int outputPcmFrameSize = 4;
    private final int bufferSize;
    private int writtenPcmBytes;

    private boolean released;
    private boolean playWhenReady;
    private int state;
    private boolean initialized;

    private final int SAMPLE_RATE = 44100;
    private final AudioTrack audioTrack;

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(19200); // about 100ms
    private byte[] preV21OutputBuffer;
    private int preV21OutputBufferOffset;

    private Uri uri;
    private ByteBuffer outputBuffer;

    PlayerInternal(Context context, boolean playWhenReady, Handler eventHandler) {
        this.playWhenReady = playWhenReady;
        this.eventHandler = eventHandler;
        this.context = context;

        state = VgmPlayer.STATE_IDLE;
        initialized = false;

        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int multipliedBufferSize = minBufferSize * BUFFER_MULTIPLICATION_FACTOR;
        int minAppBufferSize = (int) durationUsToFrames(MIN_BUFFER_DURATION_US) * outputPcmFrameSize;
        int maxAppBufferSize = (int) durationUsToFrames(MAX_BUFFER_DURATION_US) * outputPcmFrameSize;

        bufferSize = multipliedBufferSize < minAppBufferSize ? minAppBufferSize
                : multipliedBufferSize > maxAppBufferSize ? maxAppBufferSize
                : multipliedBufferSize;

        LogHelper.d(TAG, "bufferSize = " + bufferSize);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);

        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            throw new RuntimeException("AudioTrack not initialized");
        }

        audioTrackUtil = new AudioTrackUtil();

        // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
        // not normally change to this priority" is incorrect.
        internalPlaybackThread = new HandlerThread("PlayerInternal:Handler",
                Process.THREAD_PRIORITY_URGENT_AUDIO);
        internalPlaybackThread.start();
        handler = new Handler(internalPlaybackThread.getLooper(), this);
    }

    abstract boolean canSeek();
    abstract long getCurrentPosition();
    abstract void seekToInternal(long positionMs);
    abstract void abstractNativeRelease();
    abstract void abstractNativeReset();
    abstract int abstractNativeFillBuffer(ByteBuffer buffer);
    abstract int abstractNativePrepare(String fileName);
    abstract int abstractNativeStart();
    abstract void init();

    void setVolume(float volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            audioTrack.setVolume(volume);
        } else {
            //noinspection deprecation
            audioTrack.setStereoVolume(volume, volume);
        }
    }

    void prepare(Uri uri) {
        handler.obtainMessage(MSG_PREPARE, uri)
                .sendToTarget();
    }

    void setPlayWhenReady(boolean playWhenReady) {
        LogHelper.d(TAG, "setPlayWhenReady: " + playWhenReady);
        handler.obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, 0).sendToTarget();
    }

    void seekTo(long positionMs) {
        handler.obtainMessage(MSG_SEEK_TO, positionMs).sendToTarget();
    }

    public synchronized void release() {
        if (released) {
            return;
        }
        handler.sendEmptyMessage(MSG_RELEASE);
        while (!released) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        internalPlaybackThread.quit();
    }

    @Override
    public boolean handleMessage(Message msg) {
        try {
            switch (msg.what) {
                case MSG_PREPARE: {
                    prepareInternal((Uri) msg.obj);
                    return true;
                }
                case MSG_SET_PLAY_WHEN_READY: {
                    setPlayWhenReadyInternal(msg.arg1 != 0);
                    return true;
                }
                case MSG_DO_SOME_WORK: {
                    doSomeWork();
                    return true;
                }
                case MSG_SEEK_TO: {
                    seekInternal((Long) msg.obj);
                    eventHandler.obtainMessage(MSG_STATE_CHANGED, state, 0).sendToTarget();
                    return true;
                }
                case MSG_RELEASE: {
                    releaseInternal();
                    return true;
                }
                default:
                    return false;
            }
        } catch (Exception e) {
            LogHelper.w(TAG, "Renderer error.", e);
            eventHandler.obtainMessage(MSG_ERROR, e).sendToTarget();
            stopInternal();
            return true;
        }
    }

    private void setState(int state) {
        if (this.state != state) {
            this.state = state;
            eventHandler.obtainMessage(MSG_STATE_CHANGED, state, 0).sendToTarget();
        }
    }

    private void seekInternal(long position) {
        if (canSeek()) {
            audioTrack.pause();
            audioTrack.flush();
            writtenPcmBytes = 0;
            outputBuffer = null;
            seekToInternal(position);

            // fill buffer
            buffer.clear();
            int size = abstractNativeFillBuffer(buffer);
            buffer.limit(size);
        }
    }

    private void resetInternal() {
        writtenPcmBytes = 0;
        outputBuffer = null;
        handler.removeMessages(MSG_DO_SOME_WORK);
    }

    private void prepareInternal(Uri uri) {
        resetInternal();
        this.uri = uri;
        setState(VgmPlayer.STATE_BUFFERING);
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }

    private void setPlayWhenReadyInternal(boolean playWhenReady) {
        this.playWhenReady = playWhenReady;
        if (!playWhenReady) {
            audioTrack.pause();
            handler.removeMessages(MSG_DO_SOME_WORK);
        } else {
            if (state == VgmPlayer.STATE_READY || state == VgmPlayer.STATE_BUFFERING) {
                if (!handler.hasMessages(MSG_DO_SOME_WORK)) {
                    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
                }
            }
        }
    }

    void stopInternal() {
        resetInternal();
        setState(VgmPlayer.STATE_IDLE);
        abstractNativeReset();

        if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause();
        }
        audioTrack.flush();
    }

    private void releaseInternal() {
        resetInternal();
        if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause();
        }
        audioTrack.release();
        abstractNativeRelease();
        synchronized (this) {
            released = true;
            notifyAll();
        }
    }

    private void doSomeWork() {
        int delay = 10;

        if (state == VgmPlayer.STATE_BUFFERING) {

            if (!initialized) {
                eventHandler.obtainMessage(MSG_ON_AUDIOSESSION, audioTrack.getAudioSessionId(), 0).sendToTarget();
                init();
                initialized = true;
            }

            stopInternal();

            String filePath = uri.getPath();

            if(abstractNativePrepare(filePath) != 0) {
                eventHandler.obtainMessage(MSG_ERROR, new RuntimeException("Prepare failed")).sendToTarget();
                stopInternal();
                return;
            }

            if(abstractNativeStart() != 0) {
                eventHandler.obtainMessage(MSG_ERROR, new RuntimeException("Start failed")).sendToTarget();
                stopInternal();
                return;
            }

            setState(VgmPlayer.STATE_READY);
            audioTrackUtil.reconfigure(audioTrack);

            // fill buffer
            buffer.clear();
            int size = abstractNativeFillBuffer(buffer);
            buffer.limit(size);

        } else if (state == VgmPlayer.STATE_READY) {

            if (handleBuffer(buffer)) {
                buffer.clear();
                delay = 0;
                int size = abstractNativeFillBuffer(buffer);
                if (size <= 0) {
                    blockUntilCompletion(audioTrack);
                    audioTrack.stop();
                    setState(VgmPlayer.STATE_ENDED);
                } else {
                    buffer.limit(size);
                }
            }
        }

        if ((playWhenReady && state == VgmPlayer.STATE_READY) || state == VgmPlayer.STATE_BUFFERING) {
            handler.sendEmptyMessageDelayed(MSG_DO_SOME_WORK, delay);
        } else {
            handler.removeMessages(MSG_DO_SOME_WORK);
        }
    }

    private boolean handleBuffer(ByteBuffer buffer) {

        if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.play();
        }

        if (!buffer.hasRemaining()) {
            return true;
        }

        if (outputBuffer == null) {
            outputBuffer = buffer;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                int bytesRemaining = buffer.remaining();
                if (preV21OutputBuffer == null || preV21OutputBuffer.length < bytesRemaining) {
                    preV21OutputBuffer = new byte[bytesRemaining];
                }
                int originalPosition = buffer.position();
                buffer.get(preV21OutputBuffer, 0, bytesRemaining);
                buffer.position(originalPosition);
                preV21OutputBufferOffset = 0;
            }
        }

        int bytesWritten = 0;
        int bytesRemaining = buffer.remaining();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Work out how many bytes we can write without the risk of blocking.
            int bytesPending =
                    (int) (writtenPcmBytes - (audioTrackUtil.getPlaybackHeadPosition() * outputPcmFrameSize));
            int bytesToWrite = bufferSize - bytesPending;
            if (bytesToWrite > 0) {
                bytesToWrite = Math.min(bytesRemaining, bytesToWrite);
                bytesWritten = audioTrack.write(preV21OutputBuffer, preV21OutputBufferOffset, bytesToWrite);
                if (bytesWritten > 0) {
                    preV21OutputBufferOffset += bytesWritten;
                    buffer.position(buffer.position() + bytesWritten);
                }
            }
        } else {
            bytesWritten = writeNonBlockingV21(audioTrack, buffer, bytesRemaining);
        }

        writtenPcmBytes += bytesWritten;

        if (bytesWritten == bytesRemaining) {
            outputBuffer = null;
            return true;
        }

        return false;
    }

    @TargetApi(21)
    private static int writeNonBlockingV21(AudioTrack audioTrack, ByteBuffer buffer,
                                           int size) {
        return audioTrack.write(buffer, size, WRITE_NON_BLOCKING);
    }

    private long durationUsToFrames(long durationUs) {
        return (durationUs * SAMPLE_RATE) / MICROS_PER_SECOND;
    }

    private static long clip(long value, long min, long max) {
        return value < min ? min : (value < max ? value : max);
    }

    private void blockUntilCompletion(AudioTrack audioTrack) {
        final int bytesPerFrame = 4;
        final int lengthInFrames = writtenPcmBytes / bytesPerFrame;
        int previousPosition = -1;
        int currentPosition = 0;
        long blockedTimeMs = 0;
        while ((currentPosition = audioTrack.getPlaybackHeadPosition()) < lengthInFrames &&
                audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            final long estimatedTimeMs = ((lengthInFrames - currentPosition) * 1000) /
                    audioTrack.getSampleRate();
            final long sleepTimeMs = clip(estimatedTimeMs, MIN_SLEEP_TIME_MS, MAX_SLEEP_TIME_MS);
            // Check if the audio track has made progress since the last loop
            // iteration. We should then add in the amount of time that was
            // spent sleeping in the last iteration.
            if (currentPosition == previousPosition) {
                // This works only because the sleep time that would have been calculated
                // would be the same in the previous iteration too.
                blockedTimeMs += sleepTimeMs;
                // If we've taken too long to make progress, bail.
                if (blockedTimeMs > MAX_PROGRESS_WAIT_MS) {
                    LogHelper.w(TAG, "Waited unsuccessfully for " + MAX_PROGRESS_WAIT_MS + "ms " +
                            "for AudioTrack to make progress, Aborting");
                    break;
                }
            } else {
                blockedTimeMs = 0;
            }
            previousPosition = currentPosition;
            LogHelper.d(TAG, "About to sleep for : " + sleepTimeMs + " ms," +
                    " Playback position : " + currentPosition + ", Length in frames : "
                    + lengthInFrames);
            try {
                Thread.sleep(sleepTimeMs);
            } catch (InterruptedException ie) {
                break;
            }
        }
    }

    /**
     * Wraps an {@link AudioTrack} to expose useful utility methods.
     */
    private static class AudioTrackUtil {

        AudioTrack audioTrack;
        private long lastRawPlaybackHeadPosition;
        private long rawPlaybackHeadWrapCount;

        /**
         * Reconfigures the audio track utility helper to use the specified {@code audioTrack}.
         *
         * @param audioTrack The audio track to wrap.
         */
        void reconfigure(AudioTrack audioTrack) {
            this.audioTrack = audioTrack;
            lastRawPlaybackHeadPosition = 0;
            rawPlaybackHeadWrapCount = 0;
        }

        /**
         * {@link AudioTrack#getPlaybackHeadPosition()} returns a value intended to be
         * interpreted as an unsigned 32 bit integer, which also wraps around periodically. This method
         * returns the playback head position as a long that will only wrap around if the value exceeds
         * {@link Long#MAX_VALUE} (which in practice will never happen).
         *
         * @return The playback head position, in frames.
         */
        long getPlaybackHeadPosition() {
            int state = audioTrack.getPlayState();
            if (state == AudioTrack.PLAYSTATE_STOPPED) {
                // The audio track hasn't been started.
                return 0;
            }

            long rawPlaybackHeadPosition = 0xFFFFFFFFL & audioTrack.getPlaybackHeadPosition();
            if (lastRawPlaybackHeadPosition > rawPlaybackHeadPosition) {
                // The value must have wrapped around.
                rawPlaybackHeadWrapCount++;
            }
            lastRawPlaybackHeadPosition = rawPlaybackHeadPosition;
            return rawPlaybackHeadPosition + (rawPlaybackHeadWrapCount << 32);
        }
    }
}
