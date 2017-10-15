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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import net.volcanomobile.vgmplayer.Application;
import net.volcanomobile.vgmplayer.BuildConfig;
import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.ui.player.MusicPlayerActivity;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.ResourceHelper;

/**
 * Keeps track of a notification and updates it automatically for a given
 * MediaSession. Maintaining a visible notification (usually) guarantees that the music service
 * won't be killed during playback.
 */
public class MediaNotificationManager extends BroadcastReceiver {
    private static final String TAG = LogHelper.makeLogTag(MediaNotificationManager.class);

    private static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".MUSIC_CHANNEL_ID";

    private static final int NOTIFICATION_ID = 412;
    private static final int REQUEST_CODE = 100;

    public static final String ACTION_PAUSE = BuildConfig.APPLICATION_ID + ".pause";
    public static final String ACTION_PLAY = BuildConfig.APPLICATION_ID + ".play";
    public static final String ACTION_PREV = BuildConfig.APPLICATION_ID + ".prev";
    public static final String ACTION_NEXT = BuildConfig.APPLICATION_ID + ".next";
    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".stop";

    private final MusicService mService;
    private MediaSessionCompat.Token mSessionToken;
    private MediaControllerCompat mController;
    private MediaControllerCompat.TransportControls mTransportControls;

    private PlaybackStateCompat mPlaybackState;
    private MediaMetadataCompat mCurrentMetadata;
    private Bitmap mCurrentIconBitmap;
    private String mCurrentIconUrl;
    private final Bitmap mDefaultAlbumArtIcon;

    private final NotificationManager mNotificationManager;

    private final PendingIntent mPauseIntent;
    private final PendingIntent mPlayIntent;
    private final PendingIntent mPreviousIntent;
    private final PendingIntent mNextIntent;
    private final PendingIntent mStopIntent;

    private final int mNotificationColor;

    private boolean mStarted = false;

    private static final int IDLE_DELAY = 5 * 60 * 1000;

    private static final int NOTIFY_MODE_NONE = 0;
    private static final int NOTIFY_MODE_FOREGROUND = 1;
    private static final int NOTIFY_MODE_BACKGROUND = 2;

    private int mNotifyMode = NOTIFY_MODE_NONE;
    private long mLastPlayedTime;

    public MediaNotificationManager(MusicService service) throws RemoteException {
        mService = service;
        updateSessionToken();

        mNotificationColor = ResourceHelper.getThemeColor(mService, R.attr.colorPrimary,
                Color.DKGRAY);

        mNotificationManager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);

        String pkg = mService.getPackageName();
        mPauseIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_PAUSE).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mPlayIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_PLAY).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mPreviousIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_PREV).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mNextIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_NEXT).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mStopIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_STOP).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);

        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        mNotificationManager.cancelAll();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        mDefaultAlbumArtIcon = BitmapFactory.decodeResource(mService.getResources(),
                R.drawable.ic_albumart_cd_48dp, options);
    }

    /**
     * Posts the notification and starts tracking the session to keep it
     * updated. The notification will automatically be removed if the session is
     * destroyed before {@link #stopNotification} is called.
     */
    public void startNotification() {
        if (!mStarted) {
            mCurrentMetadata = mController.getMetadata();
            mPlaybackState = mController.getPlaybackState();
            mController.registerCallback(mCb);
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_NEXT);
            filter.addAction(ACTION_PAUSE);
            filter.addAction(ACTION_PLAY);
            filter.addAction(ACTION_PREV);
            mService.registerReceiver(this, filter);

            mStarted = true;
            // The notification must be updated after setting started to true
            updateNotification();
        }
    }

    /**
     * Removes the notification and stops tracking the session. If the session
     * was destroyed this has no effect.
     */
    public void stopNotification() {
        if (mStarted) {
            mStarted = false;
            mController.unregisterCallback(mCb);
            try {
                mNotificationManager.cancel(NOTIFICATION_ID);
                mService.unregisterReceiver(this);
            } catch (IllegalArgumentException ex) {
                // ignore if the receiver is not registered.
            }
            mService.stopForeground(true);
        }
    }

    private boolean isPlaying() {
        return mPlaybackState != null &&
                (mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING ||
                        mPlaybackState.getState() == PlaybackStateCompat.STATE_BUFFERING);

    }

    private boolean recentlyPlayed() {
        return isPlaying() || System.currentTimeMillis() - mLastPlayedTime < IDLE_DELAY;
    }

    private void updateNotification() {
        final int newNotifyMode;

        if (isPlaying()) {
            newNotifyMode = NOTIFY_MODE_FOREGROUND;
        } else if (recentlyPlayed()) {
            newNotifyMode = NOTIFY_MODE_BACKGROUND;
        } else {
            newNotifyMode = NOTIFY_MODE_NONE;
        }

        LogHelper.d(TAG, "mNotifyMode: ", mNotifyMode, " newNotifyMode: ", newNotifyMode);

        if (mNotifyMode != newNotifyMode) {
            if (mNotifyMode == NOTIFY_MODE_FOREGROUND) {
                mService.stopForeground(newNotifyMode == NOTIFY_MODE_NONE);
            } else if (newNotifyMode == NOTIFY_MODE_NONE) {
                mNotificationManager.cancel(NOTIFICATION_ID);
            }
        }

        Notification notification = createNotification();

        if (notification != null) {
            if (newNotifyMode == NOTIFY_MODE_FOREGROUND) {
                mService.startForeground(NOTIFICATION_ID, createNotification());
            } else if (newNotifyMode == NOTIFY_MODE_BACKGROUND) {
                mNotificationManager.notify(NOTIFICATION_ID, createNotification());
            }
        } else {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }


        mNotifyMode = newNotifyMode;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        LogHelper.d(TAG, "Received intent with action " + action);
        switch (action) {
            case ACTION_PAUSE:
                mTransportControls.pause();
                break;
            case ACTION_PLAY:
                mTransportControls.play();
                break;
            case ACTION_NEXT:
                mTransportControls.skipToNext();
                break;
            case ACTION_PREV:
                mTransportControls.skipToPrevious();
                break;
            default:
                LogHelper.w(TAG, "Unknown intent ignored. Action=", action);
        }
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see {@link android.media.session.MediaController.Callback#onSessionDestroyed()})
     */
    private void updateSessionToken() throws RemoteException {
        MediaSessionCompat.Token freshToken = mService.getSessionToken();
        if (mSessionToken == null && freshToken != null ||
                mSessionToken != null && !mSessionToken.equals(freshToken)) {
            if (mController != null) {
                mController.unregisterCallback(mCb);
            }
            mSessionToken = freshToken;
            if (mSessionToken != null) {
                mController = new MediaControllerCompat(mService, mSessionToken);
                mTransportControls = mController.getTransportControls();
                if (mStarted) {
                    mController.registerCallback(mCb);
                }
            }
        }
    }

    private PendingIntent createContentIntent() {
        Intent openUI = new Intent(mService, MusicPlayerActivity.class);
        openUI.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openUI.putExtra(MusicPlayerActivity.EXTRA_START_FULLSCREEN, true);
        return PendingIntent.getActivity(mService, REQUEST_CODE, openUI,
                PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private final MediaControllerCompat.Callback mCb = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            LogHelper.d(TAG, "Received new playback state", state);
            if (mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING
                    && state.getState() != PlaybackStateCompat.STATE_PLAYING) {
                mLastPlayedTime = System.currentTimeMillis();
            }
            mPlaybackState = state;
            updateNotification();
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            mCurrentMetadata = metadata;
            LogHelper.d(TAG, "Received new metadata ", metadata);
            updateNotification();
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
            LogHelper.d(TAG, "Session was destroyed, resetting to the new session token");
            try {
                updateSessionToken();
            } catch (RemoteException e) {
                LogHelper.e(TAG, e, "could not connect media controller");
            }
        }
    };

    @Nullable
    private Notification createNotification() {
        LogHelper.d(TAG, "updateNotificationMetadata. mCurrentMetadata=" + mCurrentMetadata);
        if (mCurrentMetadata == null || mPlaybackState == null) {
            return null;
        }

        String fetchArtUrl = null;
        // use a placeholder art while the remote art is being downloaded
        Bitmap art = mDefaultAlbumArtIcon;
        if (mCurrentMetadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI) != null) {
            // This sample assumes the iconUri will be a valid URL formatted String, but
            // it can actually be any valid Android Uri formatted String.
            // async fetch the album art icon
            fetchArtUrl = mCurrentMetadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI);

            if(mCurrentIconUrl != null && TextUtils.equals(mCurrentIconUrl, fetchArtUrl)) {
                art = mCurrentIconBitmap;
                fetchArtUrl = null;
            }
        }

        // Notification channels are only supported on Android O+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        final NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(mService, CHANNEL_ID);

        final int playPauseButtonPosition = addActions(notificationBuilder);
        notificationBuilder
                .setStyle(new MediaStyle()
                        // show only play/pause in compact view
                        .setShowActionsInCompactView(playPauseButtonPosition)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(mStopIntent)
                        .setMediaSession(mSessionToken))
                .setDeleteIntent(mStopIntent)
                .setColor(mNotificationColor)
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setContentIntent(createContentIntent())
                .setContentTitle(mCurrentMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                .setContentText(mCurrentMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
                .setLargeIcon(art);

        setNotificationPlaybackState(notificationBuilder);

        if (fetchArtUrl != null) {
            fetchBitmapFromURLAsync(fetchArtUrl, notificationBuilder);
        }

        return notificationBuilder.build();
    }

    private int addActions(final NotificationCompat.Builder notificationBuilder) {
        LogHelper.d(TAG, "updatePlayPauseAction");

        int playPauseButtonPosition = 0;
        // If skip to previous action is enabled
        if ((mPlaybackState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            notificationBuilder.addAction(R.drawable.ic_skip_previous_white_24dp,
                    mService.getString(R.string.label_previous), mPreviousIntent);

            // If there is a "skip to previous" button, the play/pause button will
            // be the second one. We need to keep track of it, because the MediaStyle notification
            // requires to specify the index of the buttons (actions) that should be visible
            // when in compact view.
            playPauseButtonPosition = 1;
        }

        // Play or pause button, depending on the current state.
        final String label;
        final int icon;
        final PendingIntent intent;
        if (mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            label = mService.getString(R.string.label_pause);
            icon = R.drawable.uamp_ic_pause_white_24dp;
            intent = mPauseIntent;
        } else {
            label = mService.getString(R.string.label_play);
            icon = R.drawable.uamp_ic_play_arrow_white_24dp;
            intent = mPlayIntent;
        }
        notificationBuilder.addAction(new NotificationCompat.Action(icon, label, intent));

        // If skip to next action is enabled
        if ((mPlaybackState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            notificationBuilder.addAction(R.drawable.ic_skip_next_white_24dp,
                    mService.getString(R.string.label_next), mNextIntent);
        }

        return playPauseButtonPosition;
    }

    private void setNotificationPlaybackState(NotificationCompat.Builder builder) {
        LogHelper.d(TAG, "updateNotificationPlaybackState. mPlaybackState=" + mPlaybackState);
        if (mPlaybackState == null || !mStarted) {
            LogHelper.d(TAG, "updateNotificationPlaybackState. cancelling notification!");
            mService.stopForeground(true);
            return;
        }
        if (mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING
                && mPlaybackState.getPosition() >= 0) {
            LogHelper.d(TAG, "updateNotificationPlaybackState. updating playback position to ",
                    (System.currentTimeMillis() - mPlaybackState.getPosition()) / 1000, " seconds");
            builder
                .setWhen(System.currentTimeMillis() - mPlaybackState.getPosition())
                .setShowWhen(true)
                .setUsesChronometer(true);
        } else {
            LogHelper.d(TAG, "updateNotificationPlaybackState. hiding playback position");
            builder
                .setWhen(0)
                .setShowWhen(false)
                .setUsesChronometer(false);
        }
    }

    private void fetchBitmapFromURLAsync(@NonNull final String bitmapUrl,
                                         @NonNull final NotificationCompat.Builder builder) {
        LogHelper.d(TAG, "fetchBitmapFromURLAsync");
        Glide.with(Application.getInstance())
                .load(bitmapUrl)
                .asBitmap()
                .dontAnimate()
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(new SimpleTarget<Bitmap>(128, 128) {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                        if (mCurrentMetadata != null) {
                            String iconUrl = mCurrentMetadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI);
                            if (TextUtils.equals(bitmapUrl, iconUrl) && !TextUtils.equals(bitmapUrl, mCurrentIconUrl)) {
                                LogHelper.d(TAG, "fetchBitmapFromURLAsync: set bitmap to ", bitmapUrl);

                                mCurrentIconUrl = bitmapUrl;
                                if(mCurrentIconBitmap != null) {
                                    mCurrentIconBitmap.recycle();
                                }
                                mCurrentIconBitmap = resource.copy(Bitmap.Config.RGB_565, false);

                                builder.setLargeIcon(mCurrentIconBitmap);
                                mNotificationManager.notify(NOTIFICATION_ID, builder.build());
                            }
                        }
                        Glide.clear(this);
                    }
                });
    }

    /**
     * Creates Notification Channel. This is required in Android O+ to display notifications.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel notificationChannel =
                    new NotificationChannel(CHANNEL_ID,
                            mService.getString(R.string.notification_channel),
                            NotificationManager.IMPORTANCE_LOW);

            notificationChannel.setDescription(
                    mService.getString(R.string.notification_channel_description));

            mNotificationManager.createNotificationChannel(notificationChannel);
        }
    }
}
