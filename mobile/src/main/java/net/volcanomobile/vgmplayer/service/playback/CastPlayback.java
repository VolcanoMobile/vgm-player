/*
 * Copyright (C) 2014 The Android Open Source Project
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
package net.volcanomobile.vgmplayer.service.playback;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import net.volcanomobile.vgmplayer.Application;
import net.volcanomobile.vgmplayer.model.MusicProvider;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.MediaIDHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import fi.iki.elonen.NanoHTTPD;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.ResourceSingleObserver;
import io.reactivex.schedulers.Schedulers;

import static android.support.v4.media.session.MediaSessionCompat.QueueItem;

/**
 * An implementation of Playback that talks to Cast.
 */
public class CastPlayback implements Playback {

    static {
        System.loadLibrary("vgmplay");
        System.loadLibrary("VGMPlayer_JNI");
    }

    private static final String TAG = LogHelper.makeLogTag(CastPlayback.class);

    private static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";
    private static final String ITEM_ID = "itemId";

    private static final int HTTP_PORT = 18888;

    private final MusicProvider mMusicProvider;
    private final Context mAppContext;
    private final RemoteMediaClient mRemoteMediaClient;
    private final RemoteMediaClient.Listener mRemoteMediaClientListener;

    private final WifiManager.WifiLock mWifiLock;
    private HttpServer mHttpServer;

    /** The current PlaybackState */
    private int mPlaybackState;

    /** Callback for making completion/error calls on */
    private Callback mCallback;
    private long mCurrentPosition;
    private String mCurrentMediaId;

    @SuppressLint("WifiManagerPotentialLeak")
    public CastPlayback(MusicProvider musicProvider, Context context) {
        mMusicProvider = musicProvider;
        mAppContext = context.getApplicationContext();

        CastSession castSession = CastContext.getSharedInstance(mAppContext).getSessionManager()
                .getCurrentCastSession();
        mRemoteMediaClient = castSession.getRemoteMediaClient();
        mRemoteMediaClientListener = new CastMediaClientListener();
        mWifiLock = ((WifiManager) context.getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "vgmplayer_lock");
    }

    @Override
    public void start() {
        mRemoteMediaClient.addListener(mRemoteMediaClientListener);

        mWifiLock.acquire();

        try {
            mHttpServer = new HttpServer(mAppContext);
            mHttpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop(boolean notifyListeners) {
        mRemoteMediaClient.removeListener(mRemoteMediaClientListener);
        mPlaybackState = PlaybackStateCompat.STATE_STOPPED;
        if (notifyListeners && mCallback != null) {
            mCallback.onPlaybackStatusChanged(mPlaybackState);
        }

        if(mHttpServer != null && mHttpServer.wasStarted()) {
            mHttpServer.stop();
            mHttpServer = null;
        }

        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    @Override
    public void setState(int state) {
        this.mPlaybackState = state;
    }

    @Override
    public long getCurrentStreamPosition() {
        if (!isConnected()) {
            return mCurrentPosition;
        }
        return (int) mRemoteMediaClient.getApproximateStreamPosition();
    }

    @Override
    public void updateLastKnownStreamPosition() {
        mCurrentPosition = getCurrentStreamPosition();
    }

    @Override
    public void play(QueueItem item) {
        String mediaId = item.getDescription().getMediaId();
        try {
            if(mPlaybackState == PlaybackStateCompat.STATE_PAUSED && TextUtils.equals(mediaId, mCurrentMediaId)) {
                mRemoteMediaClient.play();
            } else {
                loadMedia(mediaId, true);
                mPlaybackState = PlaybackStateCompat.STATE_BUFFERING;
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mPlaybackState);
                }
            }
        } catch (JSONException e) {
            LogHelper.e(TAG, "Exception loading media ", e, null);
            if (mCallback != null) {
                mCallback.onError(e.getMessage());
            }
        }
    }

    @Override
    public void pause() {
        try {
            if (mRemoteMediaClient.hasMediaSession()) {
                mRemoteMediaClient.pause();
                mCurrentPosition = (int) mRemoteMediaClient.getApproximateStreamPosition();
            } else {
                loadMedia(mCurrentMediaId, false);
            }
        } catch (JSONException e) {
            LogHelper.e(TAG, e, "Exception pausing cast playback");
            if (mCallback != null) {
                mCallback.onError(e.getMessage());
            }
        }
    }

    @Override
    public void seekTo(long position) {
        if (mCurrentMediaId == null) {
            if (mCallback != null) {
                mCallback.onError("seekTo cannot be calling in the absence of mediaId.");
            }
            return;
        }
        try {
            if (mRemoteMediaClient.hasMediaSession()) {
                mRemoteMediaClient.seek(position);
                mCurrentPosition = position;
            } else {
                mCurrentPosition = position;
                loadMedia(mCurrentMediaId, false);
            }
        } catch (JSONException e) {
            LogHelper.e(TAG, e, "Exception pausing cast playback");
            if (mCallback != null) {
                mCallback.onError(e.getMessage());
            }
        }
    }

    @Override
    public void setCurrentMediaId(String mediaId) {
        this.mCurrentMediaId = mediaId;
    }

    @Override
    public String getCurrentMediaId() {
        return mCurrentMediaId;
    }

    @Override
    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    @Override
    public boolean isConnected() {
        CastSession castSession = CastContext.getSharedInstance(mAppContext).getSessionManager()
                .getCurrentCastSession();
        return (castSession != null && castSession.isConnected());
    }

    @Override
    public boolean isPlaying() {
        return isConnected() && mRemoteMediaClient.isPlaying();
    }

    @Override
    public int getState() {
        return mPlaybackState;
    }

    private void loadMedia(String mediaId, boolean autoPlay) throws JSONException {
        String musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId);
        mMusicProvider.getMusic(musicId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new ResourceSingleObserver<MediaMetadataCompat>() {

                    @Override
                    public void onSuccess(MediaMetadataCompat mediaMetadataCompat) {
                        if (!TextUtils.equals(mediaId, mCurrentMediaId)) {
                            mCurrentMediaId = mediaId;
                            mCurrentPosition = 0;
                        }
                        JSONObject customData = new JSONObject();
                        try {
                            customData.put(ITEM_ID, mediaId);
                            MediaInfo media = toCastMediaMetadata(mediaMetadataCompat, customData);
                            mRemoteMediaClient.load(media, autoPlay, mCurrentPosition, customData);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        dispose();
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (mCallback != null) {
                            mCallback.onError("VgmPlayer error: media not found");
                        }
                        dispose();
                    }
                });
    }

    /**
     * Helper method to convert a {@link android.media.MediaMetadata} to a
     * {@link MediaInfo} used for sending media to the receiver app.
     *
     * @param track {@link MediaMetadata}
     * @param customData custom data specifies the local mediaId used by the player.
     * @return mediaInfo {@link MediaInfo}
     */
    private static MediaInfo toCastMediaMetadata(MediaMetadataCompat track,
                                                 JSONObject customData) {
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE,
                track.getDescription().getTitle() == null ? "" :
                        track.getDescription().getTitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE,
                track.getDescription().getSubtitle() == null ? "" :
                        track.getDescription().getSubtitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST,
                track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST));
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE,
                track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));

        WebImage image = new WebImage(
                new Uri.Builder()
                        .scheme("http")
                        .encodedAuthority(Application.getWifiIpAddress() + ":" + HTTP_PORT)
                        .path("/image")
                        .appendQueryParameter("src", track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI))
                        .build());
        // First image is used by the receiver for showing the audio album art.
        mediaMetadata.addImage(image);
        // Second image is used by Cast Companion Library on the full screen activity that is shown
        // when the cast dialog is clicked.
        mediaMetadata.addImage(image);

        //noinspection WrongConstant
        Uri uri = new Uri.Builder()
                .scheme("http")
                .encodedAuthority(Application.getWifiIpAddress() + ":" + HTTP_PORT)
                .path("/track")
                .appendQueryParameter("src", track.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE))
                .build();

        LogHelper.d(TAG, "toCastMediaMetadata uri = ", uri.toString());

        return new MediaInfo.Builder(uri.toString())
                .setContentType(MIME_TYPE_AUDIO)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .setCustomData(customData)
                .setStreamDuration(MediaInfo.UNKNOWN_DURATION)
                .build();
    }

    private void setMetadataFromRemote() {
        // Sync: We get the customData from the remote media information and update the local
        // metadata if it happens to be different from the one we are currently using.
        // This can happen when the app was either restarted/disconnected + connected, or if the
        // app joins an existing session while the Chromecast was playing a queue.
        try {
            MediaInfo mediaInfo = mRemoteMediaClient.getMediaInfo();
            if (mediaInfo == null) {
                return;
            }
            JSONObject customData = mediaInfo.getCustomData();

            if (customData != null && customData.has(ITEM_ID)) {
                String remoteMediaId = customData.getString(ITEM_ID);
                if (!TextUtils.equals(mCurrentMediaId, remoteMediaId)) {
                    mCurrentMediaId = remoteMediaId;
                    if (mCallback != null) {
                        mCallback.setCurrentMediaId(remoteMediaId);
                    }
                    updateLastKnownStreamPosition();
                }
            }
        } catch (JSONException e) {
            LogHelper.e(TAG, e, "Exception processing update metadata");
        }
    }

    private void updatePlaybackState() {
        int status = mRemoteMediaClient.getPlayerState();
        int idleReason = mRemoteMediaClient.getIdleReason();

        LogHelper.d(TAG, "onRemoteMediaPlayerStatusUpdated ", status);

        // Convert the remote playback states to media playback states.
        switch (status) {
            case MediaStatus.PLAYER_STATE_IDLE:
                if (idleReason == MediaStatus.IDLE_REASON_FINISHED
                        && mPlaybackState == PlaybackStateCompat.STATE_PLAYING) {
                    if (mCallback != null) {
                        mCallback.onCompletion();
                    }
                }
                break;
            case MediaStatus.PLAYER_STATE_BUFFERING:
                mPlaybackState = PlaybackStateCompat.STATE_BUFFERING;
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mPlaybackState);
                }
                break;
            case MediaStatus.PLAYER_STATE_PLAYING:
                mPlaybackState = PlaybackStateCompat.STATE_PLAYING;
                setMetadataFromRemote();
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mPlaybackState);
                }
                break;
            case MediaStatus.PLAYER_STATE_PAUSED:
                mPlaybackState = PlaybackStateCompat.STATE_PAUSED;
                setMetadataFromRemote();
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mPlaybackState);
                }
                break;
            default: // case unknown
                LogHelper.d(TAG, "State default : ", status);
                break;
        }
    }

    private class CastMediaClientListener implements RemoteMediaClient.Listener {

        @Override
        public void onMetadataUpdated() {
            LogHelper.d(TAG, "RemoteMediaClient.onMetadataUpdated");
        }

        @Override
        public void onStatusUpdated() {
            LogHelper.d(TAG, "RemoteMediaClient.onStatusUpdated");
            updatePlaybackState();
        }

        @Override
        public void onSendingRemoteMediaRequest() {
        }

        @Override
        public void onAdBreakStatusUpdated() {
        }

        @Override
        public void onQueueStatusUpdated() {
            LogHelper.d(TAG, "RemoteMediaClient.onQueueStatusUpdated");
        }

        @Override
        public void onPreloadStatusUpdated() {
        }
    }

    private static class HttpServer extends NanoHTTPD {

        private final Context mContext;

        HttpServer(Context context) {
            super(HTTP_PORT);
            mContext = context;
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            Map<String, String> params = session.getParms();
            String source = params.get("src");

            if(uri.equals("/track")) {
                try {
                    Uri file = Uri.parse(source);
                    return newChunkedResponse(Response.Status.OK, MIME_TYPE_AUDIO, new EncoderInputStream(file.getPath()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if(uri.equals("/image")) {
                try {
                    Uri fileUri = Uri.parse(source);
                    InputStream inputStream = mContext.getContentResolver().openInputStream(fileUri);
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileUri.getPath());
                    return newChunkedResponse(Response.Status.OK, mimeType, new BufferedInputStream(inputStream));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }

    private static final Lock _mutex = new ReentrantLock(true);

    private static class EncoderInputStream extends InputStream {

        private final MediaCodec mMediaCodec;
        private static final long TIMEOUT_US = 0;
        private static final int PCM_BUFFER_SIZE = 8192;

        private final byte[] pcmBuffer = new byte[PCM_BUFFER_SIZE];
        private int pcmBufferPosition = PCM_BUFFER_SIZE;
        private int pcmBufferSize = 0;
        private boolean endOfFile = false;

        private int profile;
        private int freqIdx;
        private int chanCfg;
        private byte[] adtsBuffer;
        private int adtsBufferPosition;
        private boolean endOfStream = false;

        EncoderInputStream(String filename) throws IOException {
            _mutex.lock();
            nativePrepare(filename);
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
            MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, 44100, 2);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 320000);
            mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        }

        @Override
        public int read() throws IOException {
            byte[] buffer = new byte[1];
            return read(buffer, 0, 1);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {

            int outputLeft = len;

            // leftover of previous encoded buffer
            if(adtsBuffer != null && adtsBufferPosition < adtsBuffer.length) {
                int length = Math.min(outputLeft, adtsBuffer.length - adtsBufferPosition);
                System.arraycopy(adtsBuffer, adtsBufferPosition, b, off, length);
                adtsBufferPosition += length;
                off += length;
                outputLeft -= length;
            }

            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();

            while (!endOfStream && outputLeft != 0) {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                int outputBufferId = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferId];

                    if((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        short val = (short) (((outputBuffer.get(bufferInfo.offset) & 0xff) << 8) | (outputBuffer.get(bufferInfo.offset + 1) & 0xff));
                        profile = (val >> 11) & 0x1f;
                        freqIdx = (val >> 7) & 0x0f;
                        chanCfg = (val >> 3) & 0x0f;
                        LogHelper.d(TAG, "got codec config: profile = ", profile, " freqIdx = ", freqIdx, " chanCfg = ", chanCfg);
                    } else {
                        int outBitsSize   = bufferInfo.size;
                        int outPacketSize = outBitsSize + 7;

                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + outBitsSize);

                        adtsBuffer = new byte[outPacketSize];
                        adtsBufferPosition = 0;
                        addADTStoPacket(adtsBuffer, outPacketSize);
                        outputBuffer.get(adtsBuffer, 7, outBitsSize);

                        int length = Math.min(outputLeft, adtsBuffer.length - adtsBufferPosition);
                        System.arraycopy(adtsBuffer, adtsBufferPosition, b, off, length);
                        adtsBufferPosition += length;
                        off += length;
                        outputLeft -= length;

                        endOfStream = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0);
                    }

                    mMediaCodec.releaseOutputBuffer(outputBufferId, false);
                }

                if(!endOfFile) {
                    // fill in encoder
                    int inputBufferId = mMediaCodec.dequeueInputBuffer(TIMEOUT_US);
                    while (!endOfFile && inputBufferId >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[inputBufferId];
                        inputBuffer.clear();

                        int inputBufferSize = 0;
                        int inputLeft = inputBuffer.limit();

                        if(pcmBufferPosition < pcmBufferSize) {
                            // we have some PCM leftover we can push to the encoder
                            int length = Math.min(inputLeft, pcmBufferSize - pcmBufferPosition);
                            inputBuffer.put(pcmBuffer, pcmBufferPosition, length);
                            pcmBufferPosition += length;
                            inputLeft -= length;
                            inputBufferSize += length;
                        }

                        while (!endOfFile && inputLeft != 0) {
                            pcmBufferSize = nativeFillBuffer(pcmBuffer);
                            endOfFile = (pcmBufferSize == 0);
                            pcmBufferPosition = 0;

                            int length = Math.min(inputLeft, pcmBufferSize);
                            inputBuffer.put(pcmBuffer, pcmBufferPosition, length);
                            pcmBufferPosition += length;
                            inputLeft -= length;
                            inputBufferSize += length;
                        }

                        mMediaCodec.queueInputBuffer(inputBufferId, 0, inputBufferSize, 0,
                                endOfFile ?  MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                        if(!endOfFile) {
                            inputBufferId = mMediaCodec.dequeueInputBuffer(TIMEOUT_US);
                        }
                    }
                }
            }

            return len - outputLeft;
        }

        private void addADTStoPacket(byte[] packet, int packetLen) {
            // fill in ADTS data
            packet[0] = (byte)0xFF;
            packet[1] = (byte)0xF9;
            packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
            packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
            packet[4] = (byte)((packetLen&0x7FF) >> 3);
            packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
            packet[6] = (byte)0xFC;
        }

        @Override
        public void close() throws IOException {
            mMediaCodec.stop();
            mMediaCodec.release();
            nativeRelease();
            _mutex.unlock();
            super.close();
        }
    }

    private static native void nativePrepare(String filename);
    private static native void nativeRelease();
    private static native int nativeFillBuffer(byte[] buffer);
}