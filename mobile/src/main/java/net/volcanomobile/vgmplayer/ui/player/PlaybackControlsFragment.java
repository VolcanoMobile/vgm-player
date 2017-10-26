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
package net.volcanomobile.vgmplayer.ui.player;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * A class that shows the Media Queue to the user.
 */
public class PlaybackControlsFragment extends Fragment {

    private static final String TAG = LogHelper.makeLogTag(PlaybackControlsFragment.class);

    private static final long PROGRESS_UPDATE_INTERNAL = 1000;
    private static final long PROGRESS_UPDATE_INITIAL_INTERVAL = 100;

    private ViewSwitcher mPlayPauseContainer;

    private static final int PLAY_PAUSE_CHILD_IDX = 0;
    private static final int QUEUE_CHILD_IDX = 1;

    private ImageButton mQueue;
    private ImageButton mPlayPause;
    private TextView mTitle;
    private TextView mSubtitle;
    private ImageView mAlbumArt;
    private String mArtUrl;

    private ProgressBar mLoading;
    private View mSkipNext;
    private View mSkipPrev;
    private SeekBar mSeekbar;
    private ImageButton mBottomPlayPause;

    private ImageButton mShuffle;
    private ImageButton mRepeat;

    private int mColorButtonAccent;
    private int mPlayingQueueResourceId;

    private MediaBrowserProvider mMediaBrowserProvider;

    private TextView mStart;
    private TextView mEnd;
    private TextView mLine3;
    private final Handler mHandler = new Handler();

    private final Runnable mUpdateProgressTask = () -> updateProgress();

    private final ScheduledExecutorService mExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> mScheduleFuture;
    private PlaybackStateCompat mLastPlaybackState;

    private boolean mCallbackRegistered = false;
    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private final MediaControllerCompat.Callback mCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            LogHelper.d(TAG, "Received playback state change to state ", state.getState());
            PlaybackControlsFragment.this.onPlaybackStateChanged(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            if (metadata == null) {
                return;
            }
            LogHelper.d(TAG, "Received metadata state change to mediaId=",
                    metadata.getDescription().getMediaId(),
                    " song=", metadata.getDescription().getTitle());
            PlaybackControlsFragment.this.onMetadataChanged(metadata);
        }

        @Override
        public void onShuffleModeChanged(int shuffleMode) {
            PlaybackControlsFragment.this.onShuffleModeChanged(shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE);
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            PlaybackControlsFragment.this.onRepeatModeChanged(repeatMode);
        }
    };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // If used on an activity that doesn't implement MediaFragmentListener, it
        // will throw an exception as expected:
        mMediaBrowserProvider = (MediaBrowserProvider) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mMediaBrowserProvider = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mColorButtonAccent = ResourcesCompat.getColor(getResources(), R.color.bt_accent, null);
        TypedValue outValue = new TypedValue();
        getContext().getTheme().resolveAttribute(R.attr.playingQueueButton, outValue, false);
        mPlayingQueueResourceId = outValue.data;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopSeekbarUpdate();
        mExecutorService.shutdown();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_playback_controls, container, false);

        mPlayPauseContainer = (ViewSwitcher) rootView.findViewById(R.id.play_pause_container);

        mQueue = (ImageButton) rootView.findViewById(R.id.queue);
        mPlayPause = (ImageButton) rootView.findViewById(R.id.play_pause);
        mPlayPause.setEnabled(true);
        mPlayPause.setOnClickListener(mButtonListener);

        mTitle = (TextView) rootView.findViewById(R.id.title_view);
        mSubtitle = (TextView) rootView.findViewById(R.id.subtitle_view);
        mAlbumArt = (ImageView) rootView.findViewById(R.id.album_art);

        ViewGroup controls = (ViewGroup) rootView.findViewById(R.id.controls);
        mSkipNext = controls.findViewById(R.id.next);
        mSkipPrev = controls.findViewById(R.id.prev);
        mBottomPlayPause = (ImageButton) controls.findViewById(R.id.bottom_play_pause);
        mBottomPlayPause.setOnClickListener(mButtonListener);
        mEnd = (TextView) controls.findViewById(R.id.endText);
        mStart = (TextView) controls.findViewById(R.id.startText);
        mSeekbar = (SeekBar) rootView.findViewById(R.id.seekBar);
        mLine3 = (TextView) rootView.findViewById(R.id.line3);
        mLoading = (ProgressBar) rootView.findViewById(R.id.progressBar);
        mShuffle = (ImageButton) rootView.findViewById(R.id.shuffle);
        mRepeat = (ImageButton) rootView.findViewById(R.id.repeat);

        mQueue.setOnClickListener(v -> {
            Drawable queueDrawable = ContextCompat.getDrawable(getContext(), mPlayingQueueResourceId);
            Fragment queueFragment = getChildFragmentManager().findFragmentById(R.id.queue_container);

            if (queueFragment == null) {
                queueFragment = new QueueFragment();
                getChildFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.queue_fade_in, R.anim.queue_fade_out)
                        .replace(R.id.queue_container, queueFragment).commit();
                queueDrawable = DrawableCompat.wrap(queueDrawable);
                DrawableCompat.setTint(queueDrawable.mutate(), mColorButtonAccent);
            } else {
                getChildFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.queue_fade_in, R.anim.queue_fade_out)
                        .remove(queueFragment).commit();
            }

            mQueue.setImageDrawable(queueDrawable);
        });

        mShuffle.setOnClickListener(v -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
            if (controller != null) {
                MediaControllerCompat.TransportControls controls13 = controller.getTransportControls();
                int currentMode = controller.getShuffleMode();
                controls13.setShuffleMode(currentMode != PlaybackStateCompat.SHUFFLE_MODE_NONE
                        ? PlaybackStateCompat.SHUFFLE_MODE_NONE
                        : PlaybackStateCompat.SHUFFLE_MODE_ALL);
            }
        });

        mRepeat.setOnClickListener(v -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
            if (controller != null) {
                MediaControllerCompat.TransportControls controls12 = controller.getTransportControls();
                int repeatMode = (controller.getRepeatMode() + 1) % 3;
                controls12.setRepeatMode(repeatMode);
            }
        });

        mSkipNext.setOnClickListener(v -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
            if (controller != null) {
                MediaControllerCompat.TransportControls transportControls = controller.getTransportControls();
                transportControls.skipToNext();
            }
        });

        mSkipPrev.setOnClickListener(v -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
            if (controller != null) {
                MediaControllerCompat.TransportControls controls1 = controller.getTransportControls();
                controls1.skipToPrevious();
            }
        });

        mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mStart.setText(DateUtils.formatElapsedTime(progress / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopSeekbarUpdate();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (getActivity() != null) {
                    MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
                    if (controller != null) {
                        MediaControllerCompat.TransportControls controls = controller.getTransportControls();
                        controls.seekTo(seekBar.getProgress());
                    }
                }
                scheduleSeekbarUpdate();
            }
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        MediaBrowserCompat mediaBrowser = mMediaBrowserProvider.getMediaBrowser();

        LogHelper.d(TAG, "fragment.onStart, onConnected=" + mediaBrowser.isConnected());

        if (mediaBrowser.isConnected()) {
            onConnected(MediaControllerCompat.getMediaController(getActivity()));
        }

        Fragment queueFragment = getChildFragmentManager().findFragmentById(R.id.queue_container);
        if (queueFragment != null) {
            Drawable queueDrawable = ContextCompat.getDrawable(getContext(), mPlayingQueueResourceId);
            queueDrawable = DrawableCompat.wrap(queueDrawable);
            DrawableCompat.setTint(queueDrawable.mutate(), mColorButtonAccent);
            mQueue.setImageDrawable(queueDrawable);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        LogHelper.d(TAG, "fragment.onStop");
        if (mCallbackRegistered) {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
            LogHelper.d(TAG, "unregisterCallback");
            controller.unregisterCallback(mCallback);
            mCallbackRegistered = false;
        }
    }

    public void onConnected(@NonNull MediaControllerCompat mediaController) {
        LogHelper.d(TAG, "onConnected");
        if (isDetached()) {
            return;
        }

        onMetadataChanged(mediaController.getMetadata());
        onPlaybackStateChanged(mediaController.getPlaybackState());

        if (!mCallbackRegistered) {
            LogHelper.d(TAG, "registerCallback");
            mediaController.registerCallback(mCallback);
            mCallbackRegistered = true;
        }
        updateProgress();

        QueueFragment queueFragment = (QueueFragment) getChildFragmentManager().findFragmentById(R.id.queue_container);
        if (queueFragment != null) {
            queueFragment.onConnected(mediaController);
        }

        onRepeatModeChanged(mediaController.getRepeatMode());
        onShuffleModeChanged(mediaController.getShuffleMode() != PlaybackStateCompat.SHUFFLE_MODE_NONE);
    }

    private void onShuffleModeChanged(boolean shuffleEnabled) {
        if (getContext() != null) {
            Drawable shuffleDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_shuffle_white_36dp);
            if (shuffleEnabled) {
                shuffleDrawable = DrawableCompat.wrap(shuffleDrawable);
                DrawableCompat.setTint(shuffleDrawable.mutate(), mColorButtonAccent);
            }
            mShuffle.setImageDrawable(shuffleDrawable);
        }
    }

    private void onRepeatModeChanged(int mode) {
        if (getContext() != null) {
            Drawable repeatDrawable = null;
            switch (mode) {
                case PlaybackStateCompat.REPEAT_MODE_NONE:
                    repeatDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_repeat_white_36dp);
                    break;
                case PlaybackStateCompat.REPEAT_MODE_ALL:
                    repeatDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_repeat_white_36dp);
                    repeatDrawable = DrawableCompat.wrap(repeatDrawable);
                    DrawableCompat.setTint(repeatDrawable.mutate(), mColorButtonAccent);
                    break;
                case PlaybackStateCompat.REPEAT_MODE_ONE:
                    repeatDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_repeat_one_white_36dp);
                    repeatDrawable = DrawableCompat.wrap(repeatDrawable);
                    DrawableCompat.setTint(repeatDrawable.mutate(), mColorButtonAccent);
                    break;
            }
            mRepeat.setImageDrawable(repeatDrawable);
        }
    }

    public void onBottomSheetStateChanged(int state) {
        switch (state) {
            case BottomSheetBehavior.STATE_COLLAPSED:
                mPlayPauseContainer.setDisplayedChild(PLAY_PAUSE_CHILD_IDX);
                Fragment queueFragment = getChildFragmentManager().findFragmentById(R.id.queue_container);
                if (queueFragment != null) {
                    getChildFragmentManager().beginTransaction()
                            .remove(queueFragment).commitAllowingStateLoss();
                    Drawable queueDrawable = ContextCompat.getDrawable(getContext(), mPlayingQueueResourceId);
                    mQueue.setImageDrawable(queueDrawable);
                }
                break;
            default:
                mPlayPauseContainer.setDisplayedChild(QUEUE_CHILD_IDX);
                break;
        }
    }

    private void onMetadataChanged(MediaMetadataCompat metadata) {
        LogHelper.d(TAG, "onMetadataChanged ", metadata);
        if (getActivity() == null) {
            LogHelper.w(TAG, "onMetadataChanged called when getActivity null," +
                    "this should not happen if the callback was properly unregistered. Ignoring.");
            return;
        }
        if (metadata == null) {
            return;
        }

        updateDuration(metadata);

        mTitle.setText(metadata.getDescription().getTitle());
        mSubtitle.setText(metadata.getDescription().getSubtitle());

        String artUrl = null;
        if (metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI) != null) {
            artUrl = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI);
        }

        if (!TextUtils.equals(artUrl, mArtUrl)) {
            mArtUrl = artUrl;
            Glide.with(this)
                    .load(mArtUrl)
                    .dontAnimate()
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .error(R.drawable.ic_albumart_cd_48dp)
                    .into(mAlbumArt);
        }
    }

    private void onPlaybackStateChanged(PlaybackStateCompat state) {
        LogHelper.d(TAG, "onPlaybackStateChanged ", state);
        if (getActivity() == null) {
            LogHelper.w(TAG, "onPlaybackStateChanged called when getActivity null," +
                    "this should not happen if the callback was properly unregistered. Ignoring.");
            return;
        }
        if (state == null) {
            return;
        }

        if (state.getState() == PlaybackStateCompat.STATE_PLAYING ||
                state.getState() == PlaybackStateCompat.STATE_BUFFERING) {
            scheduleSeekbarUpdate();
        }

        mLastPlaybackState = state;
        mLine3.setText(null);

        boolean enablePlay = false;
        switch (state.getState()) {
            case PlaybackStateCompat.STATE_PLAYING:
                scheduleSeekbarUpdate();
                mLoading.setVisibility(View.INVISIBLE);
                mBottomPlayPause.setVisibility(View.VISIBLE);
                break;
            case PlaybackStateCompat.STATE_BUFFERING:
                mLoading.setVisibility(View.VISIBLE);
                mBottomPlayPause.setVisibility(View.INVISIBLE);
                mLine3.setText(R.string.loading);
                stopSeekbarUpdate();
                break;
            case PlaybackStateCompat.STATE_PAUSED:
            case PlaybackStateCompat.STATE_STOPPED:
                mLoading.setVisibility(View.INVISIBLE);
                mBottomPlayPause.setVisibility(View.VISIBLE);
                stopSeekbarUpdate();
                enablePlay = true;
                break;
            case PlaybackStateCompat.STATE_ERROR:
                LogHelper.e(TAG, "error playbackstate: ", state.getErrorMessage());
                Toast.makeText(getActivity(), state.getErrorMessage(), Toast.LENGTH_LONG).show();
                break;
        }

        if (enablePlay) {
            mPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(getActivity(), R.drawable.ic_play_arrow_black_36dp));
            mBottomPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(getActivity(), R.drawable.ic_play_arrow_black_48dp));
        } else {
            mPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(getActivity(), R.drawable.ic_pause_black_36dp));
            mBottomPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(getActivity(), R.drawable.ic_pause_black_48dp));
        }

        mSkipNext.setVisibility((state.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) == 0
                ? INVISIBLE : VISIBLE );
        mSkipPrev.setVisibility((state.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) == 0
                ? INVISIBLE : VISIBLE );
    }

    private final View.OnClickListener mButtonListener = v -> {
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
        PlaybackStateCompat stateObj = controller.getPlaybackState();
        final int state = stateObj == null ?
                PlaybackStateCompat.STATE_NONE : stateObj.getState();
        LogHelper.d(TAG, "Button pressed, in state " + state);
        switch (v.getId()) {
            case R.id.play_pause:
            case R.id.bottom_play_pause:
                LogHelper.d(TAG, "Play button pressed, in state " + state);
                if (state == PlaybackStateCompat.STATE_PAUSED ||
                        state == PlaybackStateCompat.STATE_STOPPED ||
                        state == PlaybackStateCompat.STATE_NONE) {
                    playMedia();
                } else if (state == PlaybackStateCompat.STATE_PLAYING ||
                        state == PlaybackStateCompat.STATE_BUFFERING ||
                        state == PlaybackStateCompat.STATE_CONNECTING) {
                    pauseMedia();
                }
                break;
        }
    };

    private void playMedia() {
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
        if (controller != null) {
            controller.getTransportControls().play();
        }
    }

    private void pauseMedia() {
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
        if (controller != null) {
            controller.getTransportControls().pause();
        }
    }

    private void updateDuration(MediaMetadataCompat metadata) {
        if (metadata == null) {
            return;
        }
        LogHelper.d(TAG, "updateDuration called ");
        int duration = (int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);

        if (duration == 0) {
            mEnd.setVisibility(View.GONE);
            mStart.setVisibility(View.GONE);
            mSeekbar.setVisibility(View.GONE);
        } else {
            mEnd.setVisibility(View.VISIBLE);
            mStart.setVisibility(View.VISIBLE);
            mSeekbar.setVisibility(VISIBLE);
        }

        mEnd.setText(DateUtils.formatElapsedTime(duration/1000));
        mSeekbar.setEnabled(true);
        mSeekbar.setProgress(0);
        mSeekbar.setMax(duration);
    }

    private void stopSeekbarUpdate() {
        if (mScheduleFuture != null) {
            mScheduleFuture.cancel(false);
        }
    }

    private void scheduleSeekbarUpdate() {
        stopSeekbarUpdate();
        if (!mExecutorService.isShutdown()) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate(
                    () -> mHandler.post(mUpdateProgressTask), PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS);
        }
    }

    private void updateProgress() {
        if (mLastPlaybackState == null) {
            return;
        }
        long currentPosition = mLastPlaybackState.getPosition();
        if (mLastPlaybackState.getState() != PlaybackStateCompat.STATE_PAUSED) {
            // Calculate the elapsed time between the last position update and now and unless
            // paused, we can assume (delta * speed) + current position is approximately the
            // latest position. This ensure that we do not repeatedly call the getPlaybackState()
            // on MediaControllerCompat.
            long timeDelta = SystemClock.elapsedRealtime() -
                    mLastPlaybackState.getLastPositionUpdateTime();
            currentPosition += (int) timeDelta * mLastPlaybackState.getPlaybackSpeed();
        }
        mSeekbar.setProgress((int) currentPosition);
    }
}