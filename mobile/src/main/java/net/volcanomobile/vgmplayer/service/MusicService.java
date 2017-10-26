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

package net.volcanomobile.vgmplayer.service;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.media.MediaRouter;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import net.volcanomobile.vgmplayer.Application;
import net.volcanomobile.vgmplayer.BuildConfig;
import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.model.MusicProvider;
import net.volcanomobile.vgmplayer.service.playback.LocalPlayback;
import net.volcanomobile.vgmplayer.service.playback.Playback;
import net.volcanomobile.vgmplayer.service.playback.PlaybackManager;
import net.volcanomobile.vgmplayer.service.playback.QueueManager;
import net.volcanomobile.vgmplayer.service.player.CastPlayback;
import net.volcanomobile.vgmplayer.ui.player.MusicPlayerActivity;
import net.volcanomobile.vgmplayer.utils.CarHelper;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.PackageValidator;
import net.volcanomobile.vgmplayer.utils.WearHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DisposableSubscriber;

import static net.volcanomobile.vgmplayer.utils.MediaIDHelper.MEDIA_ID_EMPTY_ROOT;
import static net.volcanomobile.vgmplayer.utils.MediaIDHelper.MEDIA_ID_ROOT;

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 * <p>
 * To implement a MediaBrowserService, you need to:
 * <p>
 * <ul>
 * <p>
 * <li> Extend {@link android.service.media.MediaBrowserService}, implementing the media browsing
 * related methods {@link android.service.media.MediaBrowserService#onGetRoot} and
 * {@link android.service.media.MediaBrowserService#onLoadChildren};
 * <li> In onCreate, start a new {@link android.media.session.MediaSession} and notify its parent
 * with the session's token {@link android.service.media.MediaBrowserService#setSessionToken};
 * <p>
 * <li> Set a callback on the
 * {@link android.media.session.MediaSession#setCallback(android.media.session.MediaSession.Callback)}.
 * The callback will receive all the user's actions, like play, pause, etc;
 * <p>
 * <li> Handle all the actual music playing using any method your app prefers (for example,
 * {@link android.media.MediaPlayer})
 * <p>
 * <li> Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * {@link android.media.session.MediaSession#setPlaybackState(android.media.session.PlaybackState)}
 * {@link android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)} and
 * {@link android.media.session.MediaSession#setQueue(java.util.List)})
 * <p>
 * <li> Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 * <p>
 * </ul>
 * <p>
 * To make your app compatible with Android Auto, you also need to:
 * <p>
 * <ul>
 * <p>
 * <li> Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 * <p>
 * </ul>
 *
 * @see <a href="README.md">README.md</a> for more details.
 */
public class MusicService extends MediaBrowserServiceCompat implements
        PlaybackManager.PlaybackServiceCallback {

    private static final String TAG = LogHelper.makeLogTag(MusicService.class);

    // Extra on MediaSession that contains the Cast device name currently connected to
    public static final String EXTRA_CONNECTED_CAST = BuildConfig.APPLICATION_ID + ".CAST_NAME";
    // The action of the incoming Intent indicating that it contains a command
    // to be executed (see {@link #onStartCommand})
    public static final String ACTION_CMD = BuildConfig.APPLICATION_ID + ".ACTION_CMD";
    // The key in the extras of the incoming Intent indicating the command that
    // should be executed (see {@link #onStartCommand})
    public static final String CMD_NAME = "CMD_NAME";
    // A value of a CMD_NAME key in the extras of the incoming Intent that
    // indicates that the music playback should be paused (see {@link #onStartCommand})
    public static final String CMD_PAUSE = "CMD_PAUSE";
    // A value of a CMD_NAME key that indicates that the music playback should switch
    // to local playback from cast playback.
    public static final String CMD_STOP_CASTING = "CMD_STOP_CASTING";

    // Delay stopSelf by using a handler.
    private static final int STOP_DELAY = 120000;

    public static final String CUSTOM_ACTION_DELETE_ITEM = "CUSTOM_ACTION_DELETE_ITEM";
    public static final String ARG_MUSIC_ID = "ARG_MUSIC_ID";

    private static final String ACTION_ROOT_CHANGED = "ACTION_ROOT_CHANGED";
    private static final IntentFilter ROOT_CHANGED_FILTER = new IntentFilter(ACTION_ROOT_CHANGED);

    private MusicProvider mMusicProvider;
    private PlaybackManager mPlaybackManager;

    private MediaSessionCompat mSession;
    private MediaNotificationManager mMediaNotificationManager;
    private Bundle mSessionExtras;
    private final DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);
    private MediaRouter mMediaRouter;
    private PackageValidator mPackageValidator;
    private SessionManager mCastSessionManager;
    private SessionManagerListener<CastSession> mCastSessionManagerListener;

    private MediaMetadataCompat mCurrentMetadata;
    private Bitmap mCurrentArtBitmap;
    private String mCurrentArtUrl;

    private DisposableSubscriber<List<MediaBrowserCompat.MediaItem>> disposableSubscriber;

    private final BroadcastReceiver mRootChangedBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            notifyChildrenChanged(MEDIA_ID_ROOT);
        }
    };

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mCurrentArtBitmap != null) {
            mCurrentArtBitmap.recycle();
            mCurrentArtBitmap = null;
        }
        mCurrentArtUrl = null;
    }

    public static void rootChanged(@NonNull Context context) {
        LocalBroadcastManager.getInstance(context)
                .sendBroadcast(
                        new Intent(ACTION_ROOT_CHANGED)
                );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogHelper.d(TAG, "onCreate");

        mMusicProvider = new MusicProvider(this);

        mPackageValidator = new PackageValidator(this);

        QueueManager queueManager = new QueueManager(mMusicProvider, this,
                new QueueManager.MetadataUpdateListener() {
                    @Override
                    public void onMetadataChanged(MediaMetadataCompat metadata) {
                        mCurrentMetadata = metadata;

                        if (metadata != null) {
                            String artUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
                            if (!TextUtils.isEmpty(artUri)) {
                                if (mCurrentArtUrl != null && TextUtils.equals(mCurrentArtUrl, artUri)) {
                                    mCurrentMetadata = new MediaMetadataCompat.Builder(metadata)
                                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mCurrentArtBitmap)
                                            .build();
                                    mSession.setMetadata(mCurrentMetadata);
                                } else {
                                    fetchBitmapFromURLAsync(artUri);
                                }
                            } else {
                                mSession.setMetadata(mCurrentMetadata);
                            }
                        } else {
                            mSession.setMetadata(null);
                        }
                    }

                    @Override
                    public void onMetadataRetrieveError() {
                        mPlaybackManager.updatePlaybackState(
                                getString(R.string.error_no_metadata));
                    }

                    @Override
                    public void onCurrentQueueIndexUpdated(int queueIndex) {
                    }

                    @Override
                    public void onQueueUpdated(@Nullable String title,
                                               @Nullable List<MediaSessionCompat.QueueItem> newQueue) {
                        mSession.setQueue(newQueue);
                        mSession.setQueueTitle(title);
                    }

                    @Override
                    public void onQueueCleared() {
                        mSession.setQueue(null);
                        mSession.setQueueTitle(null);
                    }
                });

        LocalPlayback playback = new LocalPlayback(this, mMusicProvider);
        mPlaybackManager = new PlaybackManager(this, getResources(), queueManager, playback);

        // Start a new MediaSession
        mSession = new MediaSessionCompat(this, "MusicService");

        setSessionToken(mSession.getSessionToken());
        mSession.setCallback(mPlaybackManager.getMediaSessionCallback());
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS |
                MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

        Context context = getApplicationContext();
        Intent intent = new Intent(context, MusicPlayerActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mSession.setSessionActivity(pi);

        mSessionExtras = new Bundle();
        CarHelper.setSlotReservationFlags(mSessionExtras, true, true, true);
        WearHelper.setSlotReservationFlags(mSessionExtras, true, true);
        WearHelper.setUseBackgroundFromTheme(mSessionExtras, true);
        mSession.setExtras(mSessionExtras);

        mPlaybackManager.updatePlaybackState(null);

        // restore saved session if any
        mPlaybackManager.restoreState();

        try {
            mMediaNotificationManager = new MediaNotificationManager(this);
        } catch (RemoteException e) {
            throw new IllegalStateException("Could not create a MediaNotificationManager", e);
        }

        int playServicesAvailable =
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);

        if (playServicesAvailable == ConnectionResult.SUCCESS) {
            mCastSessionManager = CastContext.getSharedInstance(this).getSessionManager();
            mCastSessionManagerListener = new CastSessionManagerListener();
            mCastSessionManager.addSessionManagerListener(mCastSessionManagerListener,
                    CastSession.class);
        }

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mRootChangedBroadcastReceiver, ROOT_CHANGED_FILTER);
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        if (startIntent != null) {
            String action = startIntent.getAction();
            String command = startIntent.getStringExtra(CMD_NAME);
            if (ACTION_CMD.equals(action)) {
                if (CMD_PAUSE.equals(command)) {
                    mPlaybackManager.handlePauseRequest();
                } else if (CMD_STOP_CASTING.equals(command)) {
                }
            } else {
                // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mSession, startIntent);
            }
        }
        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);

        return START_NOT_STICKY;
    }

    /*
     * Handle case when user swipes the app away from the recents apps list by
     * stopping the service (and any ongoing playback).
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        LogHelper.d(TAG, "onDestroy");

        // Service is being killed, so make sure we release our resources
        mPlaybackManager.handleStopRequest(null);
        mMediaNotificationManager.stopNotification();

        if (mCastSessionManager != null) {
            mCastSessionManager.removeSessionManagerListener(mCastSessionManagerListener,
                    CastSession.class);
        }

        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mSession.release();

        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(mRootChangedBroadcastReceiver);

        if (disposableSubscriber != null && !disposableSubscriber.isDisposed()) {
            disposableSubscriber.dispose();
            disposableSubscriber = null;
        }
    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid,
                                 Bundle rootHints) {
        LogHelper.d(TAG, "OnGetRoot: clientPackageName=" + clientPackageName,
                "; clientUid=" + clientUid + " ; rootHints=", rootHints);
        // To ensure you are not allowing any arbitrary app to browse your app's contents, you
        // need to check the origin:
        if (!mPackageValidator.isCallerAllowed(this, clientPackageName, clientUid)) {
            // If the request comes from an untrusted package, return an empty browser root.
            // If you return null, then the media browser will not be able to connect and
            // no further calls will be made to other media browsing methods.
            LogHelper.i(TAG, "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
                    + "Returning empty browser root so all apps can use MediaController."
                    + clientPackageName);
            return new MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_EMPTY_ROOT, null);
        }
        //noinspection StatementWithEmptyBody
        if (CarHelper.isValidCarPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library to show a different subset
            // when connected to the car, this is where you should handle it.
            // If you want to adapt other runtime behaviors, like tweak ads or change some behavior
            // that should be different on cars, you should instead use the boolean flag
            // set by the BroadcastReceiver mCarConnectionReceiver (mIsConnectedToCar).
        }
        //noinspection StatementWithEmptyBody
        if (WearHelper.isValidWearCompanionPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library for when browsing from a
            // Wear device, you should return a different MEDIA ROOT here, and then,
            // on onLoadChildren, handle it accordingly.
        }

        mMusicProvider.addClient(clientPackageName);
        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentMediaId,
                               @NonNull final Result<List<MediaItem>> result) {
        LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentMediaId);

        if (MEDIA_ID_EMPTY_ROOT.equals(parentMediaId)) {
            result.sendResult(new ArrayList<MediaItem>());
            return;
        }

        result.detach();

        if (disposableSubscriber != null && !disposableSubscriber.isDisposed()) {
            disposableSubscriber.dispose();
        }

        disposableSubscriber = new DisposableSubscriber<List<MediaItem>>() {
            private boolean mResultSent = false;

            @Override
            protected void onStart() {
                request(1);
            }

            @Override
            public void onNext(List<MediaItem> mediaItems) {
                if (!mResultSent) {
                    result.sendResult(mediaItems);
                    mResultSent = true;
                    request(1);
                } else {
                    notifyChildrenChanged(parentMediaId);
                    cancel();
                }
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        };

        mMusicProvider.getChildren(parentMediaId)
                .subscribeOn(Schedulers.io())
                .subscribe(disposableSubscriber);
    }

    @Override
    public void onCustomAction(@NonNull String action, Bundle extras, @NonNull Result<Bundle> result) {
        switch (action) {
            case CUSTOM_ACTION_DELETE_ITEM: {
                if (extras != null && extras.containsKey(ARG_MUSIC_ID)) {
                    String musicId = extras.getString(ARG_MUSIC_ID);
                    mMusicProvider.deleteMusic(musicId);
                }
                result.sendResult(null);
                break;
            }
        }
    }

    @Override
    public void onSetShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
        mSession.setShuffleMode(shuffleMode);
    }

    @Override
    public void onSetRepeatMode(@PlaybackStateCompat.RepeatMode int mode) {
        mSession.setRepeatMode(mode);
    }

    /**
     * Callback method called from PlaybackManager whenever the music is about to play.
     */
    @Override
    public void onPlaybackStart() {
        mSession.setActive(true);

        mDelayedStopHandler.removeCallbacksAndMessages(null);

        // The service needs to continue running even after the bound client (usually a
        // MediaController) disconnects, otherwise the music playback will stop.
        // Calling startService(Intent) will keep the service running until it is explicitly killed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getApplicationContext(), MusicService.class));
        } else {
            startService(new Intent(getApplicationContext(), MusicService.class));
        }
    }

    /**
     * Callback method called from PlaybackManager whenever the music stops playing.
     */
    @Override
    public void onPlaybackStop() {
        // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
        // potentially stopping the service.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
    }

    @Override
    public void onNotificationRequired() {
        mMediaNotificationManager.startNotification();
    }

    @Override
    public void onPlaybackStateUpdated(PlaybackStateCompat newState) {
        mSession.setPlaybackState(newState);
    }

    private void fetchBitmapFromURLAsync(@NonNull final String bitmapUrl) {
        LogHelper.d(TAG, "fetchBitmapFromURLAsync");
        Glide.with(Application.getInstance())
                .load(bitmapUrl)
                .asBitmap()
                .dontAnimate()
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .into(new SimpleTarget<Bitmap>(480, 800) {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                        if (mCurrentMetadata != null) {
                            String artUrl = mCurrentMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
                            if (TextUtils.equals(bitmapUrl, artUrl) && !TextUtils.equals(bitmapUrl, mCurrentArtUrl)) {
                                LogHelper.d(TAG, "fetchBitmapFromURLAsync: set bitmap to ", bitmapUrl);

                                mCurrentArtUrl = bitmapUrl;
                                if (mCurrentArtBitmap != null) {
                                    mCurrentArtBitmap.recycle();
                                }
                                mCurrentArtBitmap = resource.copy(Bitmap.Config.RGB_565, false);

                                MediaMetadataCompat newMeta = new MediaMetadataCompat.Builder(mCurrentMetadata)
                                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mCurrentArtBitmap)
                                        .build();
                                mSession.setMetadata(newMeta);
                            }
                        }
                        Glide.clear(this);
                    }

                    @Override
                    public void onLoadFailed(Exception e, Drawable errorDrawable) {
                        mCurrentArtUrl = null;
                        if (mCurrentArtBitmap != null) {
                            mCurrentArtBitmap.recycle();
                            mCurrentArtBitmap = null;
                        }
                        mSession.setMetadata(mCurrentMetadata);
                    }
                });
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<MusicService> mWeakReference;

        private DelayedStopHandler(MusicService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MusicService service = mWeakReference.get();
            if (service != null && service.mPlaybackManager.getPlayback() != null) {
                if (service.mPlaybackManager.getPlayback().isPlaying()) {
                    LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.");
                    return;
                }
                LogHelper.d(TAG, "Stopping service with delay handler.");
                service.stopSelf();
            }
        }
    }

    /**
     * Session Manager Listener responsible for switching the Playback instances
     * depending on whether it is connected to a remote player.
     */
    private class CastSessionManagerListener implements SessionManagerListener<CastSession> {

        @Override
        public void onSessionEnded(CastSession session, int error) {
            LogHelper.d(TAG, "onSessionEnded");
            mSessionExtras.remove(EXTRA_CONNECTED_CAST);
            mSession.setExtras(mSessionExtras);
            Playback playback = new LocalPlayback(MusicService.this, mMusicProvider);
            mMediaRouter.setMediaSessionCompat(null);
            mPlaybackManager.switchToPlayback(playback, false);
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            LogHelper.d(TAG, "onSessionResumed");
            if (!wasSuspended) {
                // In case we are casting, send the device name as an extra on MediaSession metadata.
                mSessionExtras.putString(EXTRA_CONNECTED_CAST,
                        session.getCastDevice().getFriendlyName());
                mSession.setExtras(mSessionExtras);
                // Now we can switch to CastPlayback
                Playback playback = new CastPlayback(mMusicProvider, MusicService.this);
                mMediaRouter.setMediaSessionCompat(mSession);
                mPlaybackManager.switchToPlayback(playback, true);
            }
        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            LogHelper.d(TAG, "onSessionStarted");
            // In case we are casting, send the device name as an extra on MediaSession metadata.
            mSessionExtras.putString(EXTRA_CONNECTED_CAST,
                    session.getCastDevice().getFriendlyName());
            mSession.setExtras(mSessionExtras);
            // Now we can switch to CastPlayback
            Playback playback = new CastPlayback(mMusicProvider, MusicService.this);
            mMediaRouter.setMediaSessionCompat(mSession);
            mPlaybackManager.switchToPlayback(playback, true);
        }

        @Override
        public void onSessionStarting(CastSession session) {
            LogHelper.d(TAG, "onSessionStarting");
        }

        @Override
        public void onSessionStartFailed(CastSession session, int error) {
            LogHelper.d(TAG, "onSessionStartFailed");
        }

        @Override
        public void onSessionEnding(CastSession session) {
            LogHelper.d(TAG, "onSessionEnding");
            // This is our final chance to update the underlying stream position
            // In onSessionEnded(), the underlying CastPlayback#mRemoteMediaClient
            // is disconnected and hence we update our local value of stream position
            // to the latest position.
            mPlaybackManager.getPlayback().updateLastKnownStreamPosition();
        }

        @Override
        public void onSessionResuming(CastSession session, String sessionId) {
            LogHelper.d(TAG, "onSessionResuming");
        }

        @Override
        public void onSessionResumeFailed(CastSession session, int error) {
            LogHelper.d(TAG, "onSessionResumeFailed");
        }

        @Override
        public void onSessionSuspended(CastSession session, int reason) {
            LogHelper.d(TAG, "onSessionSuspended");
        }
    }
}
