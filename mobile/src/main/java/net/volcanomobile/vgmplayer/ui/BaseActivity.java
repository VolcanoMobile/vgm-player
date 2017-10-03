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
package net.volcanomobile.vgmplayer.ui;

import android.app.ActivityManager;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import net.volcanomobile.vgmplayer.BuildConfig;
import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.service.MusicService;
import net.volcanomobile.vgmplayer.ui.player.MediaBrowserProvider;
import net.volcanomobile.vgmplayer.ui.player.PlaybackControlsFragment;
import net.volcanomobile.vgmplayer.utils.DrawableUtils;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.ResourceHelper;

import java.util.List;

/**
 * Base activity for activities that need to show a playback control fragment when media is playing.
 */
public abstract class BaseActivity extends ActionBarActivity implements MediaBrowserProvider {

    private static final String TAG = LogHelper.makeLogTag(BaseActivity.class);

    public static final String EXTRA_START_FULLSCREEN =
            BuildConfig.APPLICATION_ID + ".EXTRA_START_FULLSCREEN";

    private MediaBrowserCompat mMediaBrowser;
    private PlaybackControlsFragment mControlsFragment;

    private boolean mBottomSheetInitialized;

    private View mBottomSheet;
    private BottomSheetBehavior mBottomSheetBehavior;
    private ImageView mBackgroundImage;
    private String mCurrentArtUrl;

    private Bundle mVoiceSearchParams;
    private Intent mPendingOnNewIntent = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LogHelper.d(TAG, "Activity onCreate");

        if (Build.VERSION.SDK_INT >= 21) {
            // Since our app icon has the same color as colorPrimary, our entry in the Recent Apps
            // list gets weird. We need to change either the icon or the color
            // of the TaskDescription.
            ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(
                    getTitle().toString(),
                    BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_white),
                    ResourceHelper.getThemeColor(this, R.attr.colorPrimary,
                            android.R.color.darker_gray));
            setTaskDescription(taskDesc);
        }

        // Connect a media browser just to get the media session token. There are other ways
        // this can be done, for example by sharing the session token directly.
        mMediaBrowser = new MediaBrowserCompat(this,
                new ComponentName(this, MusicService.class), mConnectionCallback, null);
    }

    protected void initializeBottomSheet() {

        mBackgroundImage = (ImageView) findViewById(R.id.background_image);

        mBottomSheet = findViewById(R.id.bottom_sheet);
        mBottomSheetBehavior = BottomSheetBehavior.from(mBottomSheet);
        mBottomSheetBehavior.setBottomSheetCallback(mBottomSheetCallback);

        View collapsedView = findViewById(R.id.collapsed_view);
        collapsedView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                    mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                } else {
                    mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }
        });

        mBottomSheetInitialized = true;
    }

    protected void startFullScreenActivityIfNeeded(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_START_FULLSCREEN, false)) {
            mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogHelper.d(TAG, "Activity onStart");

        if (!mBottomSheetInitialized) {
            throw new IllegalStateException("You must run super.initializeBottomSheet at " +
                    "the end of your onCreate method");
        }

        mControlsFragment = (PlaybackControlsFragment) getSupportFragmentManager()
            .findFragmentById(R.id.fragment_playback_controls);
        if (mControlsFragment == null) {
            throw new IllegalStateException("Missing fragment with id 'controls'. Cannot continue.");
        }

        mMediaBrowser.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mPendingOnNewIntent != null) {
            initializeFromParams(mPendingOnNewIntent);
            startFullScreenActivityIfNeeded(mPendingOnNewIntent);
            mPendingOnNewIntent = null;
        }

        if (mControlsFragment != null) {
            mControlsFragment.onBottomSheetStateChanged(mBottomSheetBehavior.getState());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogHelper.d(TAG, "Activity onStop");

        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(this);
        if (mediaController != null) {
            LogHelper.d(TAG, "unregisterCallback");
            mediaController.unregisterCallback(mMediaControllerCallback);
        }
        mMediaBrowser.disconnect();
    }

    @Override
    public void onBackPressed() {
        if(mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LogHelper.d(TAG, "onNewIntent, intent=" + intent);
        mPendingOnNewIntent = intent;
    }

    @Override
    public MediaBrowserCompat getMediaBrowser() {
        return mMediaBrowser;
    }

    protected void onMediaControllerConnected(@NonNull MediaControllerCompat mediaController) {
        if (mVoiceSearchParams != null) {
            // If there is a bootstrap parameter to start from a search query, we
            // send it to the media session and set it to null, so it won't play again
            // when the activity is stopped/started or recreated:
            String query = mVoiceSearchParams.getString(SearchManager.QUERY);
            mediaController.getTransportControls().playFromSearch(query, mVoiceSearchParams);
            mVoiceSearchParams = null;
        }
    }

    protected void showPlaybackControls() {
        LogHelper.d(TAG, "showPlaybackControls");
        findViewById(R.id.bottom_sheet).setVisibility(View.VISIBLE);
        findViewById(R.id.bottom_space).setVisibility(View.VISIBLE);
    }

    protected void hidePlaybackControls() {
        LogHelper.d(TAG, "hidePlaybackControls");
        if(!isDestroyed()) {
            if (mBottomSheetBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED) {
                mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
            findViewById(R.id.bottom_sheet).setVisibility(View.GONE);
            findViewById(R.id.bottom_space).setVisibility(View.GONE);
        }
    }

    /**
     * Check if the MediaSession is active and in a "playback-able" state
     * (not NONE and not STOPPED).
     *
     * @return true if the MediaSession's state requires playback controls to be visible.
     */
    protected boolean shouldShowControls() {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(this);
        if (mediaController == null ||
            mediaController.getMetadata() == null ||
            mediaController.getPlaybackState() == null) {
            return false;
        }

        if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_ERROR) {
            return false;
        }

        List<MediaSessionCompat.QueueItem> queue = mediaController.getQueue();
        return queue != null && queue.size() > 0;
    }

    private void connectToSession(MediaSessionCompat.Token token) throws RemoteException {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        MediaControllerCompat mediaController = new MediaControllerCompat(this, token);
        MediaControllerCompat.setMediaController(this, mediaController);
        LogHelper.d(TAG, "registerCallback");
        mediaController.registerCallback(mMediaControllerCallback);

        if (shouldShowControls()) {
            showPlaybackControls();
        } else {
            LogHelper.d(TAG, "connectionCallback.onConnected: " +
                "hiding controls because metadata is null");
            hidePlaybackControls();
        }

        if (mControlsFragment != null) {
            mControlsFragment.onConnected(mediaController);
        }

        MediaMetadataCompat metadata = mediaController.getMetadata();
        if(metadata != null) {
            fetchImageAsync(metadata);
        }

        onMediaControllerConnected(mediaController);
    }

    private void fetchImageAsync(@NonNull MediaMetadataCompat metadata) {
        if (isDestroyed()) {
            return;
        }

        String artUrl = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
        if(TextUtils.isEmpty(artUrl)) {
            artUrl = DrawableUtils.getUriToDrawable(this, R.drawable.albumart_cd).toString();
        }
        if(!TextUtils.equals(artUrl, mCurrentArtUrl)) {
            mCurrentArtUrl = artUrl;
            Glide.with(this)
                    .load(artUrl)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.RESULT)
                    .error(R.drawable.albumart_cd)
                    .into(mBackgroundImage);
        }
    }

    protected void initializeFromParams(Intent intent) {
        // check if we were started from a "Play XYZ" voice search. If so, we save the extras
        // (which contain the query details) in a parameter, so we can reuse it later, when the
        // MediaSession is connected.
        if (intent.getAction() != null
                && intent.getAction().equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)) {
            mVoiceSearchParams = intent.getExtras();
            LogHelper.d(TAG, "Starting from voice search query=",
                    mVoiceSearchParams.getString(SearchManager.QUERY));
        }
    }

    // Callback that ensures that we are showing the controls
    private final MediaControllerCompat.Callback mMediaControllerCallback =
        new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
                if (shouldShowControls()) {
                    showPlaybackControls();
                } else {
                    LogHelper.d(TAG, "mediaControllerCallback.onPlaybackStateChanged: " +
                            "hiding controls because state is ", state.getState());
                    hidePlaybackControls();
                }
            }

            @Override
            public void onMetadataChanged(MediaMetadataCompat metadata) {
                if (shouldShowControls()) {
                    showPlaybackControls();
                } else {
                    LogHelper.d(TAG, "mediaControllerCallback.onMetadataChanged: " +
                        "hiding controls because metadata is null");
                    hidePlaybackControls();
                }

                if (metadata != null) {
                    LogHelper.d(TAG, "Received metadata state change to mediaId=",
                            metadata.getDescription().getMediaId(),
                            " song=", metadata.getDescription().getTitle());
                    fetchImageAsync(metadata);
                }
            }

            @Override
            public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
                if (shouldShowControls()) {
                    showPlaybackControls();
                } else {
                    LogHelper.d(TAG, "mediaControllerCallback.onMetadataChanged: " +
                            "hiding controls because metadata is null");
                    hidePlaybackControls();
                }
            }
        };

    private final MediaBrowserCompat.ConnectionCallback mConnectionCallback =
        new MediaBrowserCompat.ConnectionCallback() {
            @Override
            public void onConnected() {
                LogHelper.d(TAG, "onConnected");
                if (!isDestroyed()) {
                    try {
                        connectToSession(mMediaBrowser.getSessionToken());
                    } catch (RemoteException e) {
                        LogHelper.e(TAG, e, "could not connect media controller");
                        hidePlaybackControls();
                    }
                }
            }
        };

    private final BottomSheetBehavior.BottomSheetCallback mBottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState) {
            PlaybackControlsFragment fragment = (PlaybackControlsFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_playback_controls);
            if (fragment != null) {
                fragment.onBottomSheetStateChanged(newState);
            }
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            mBackgroundImage.setAlpha(slideOffset);
        }
    };
}
