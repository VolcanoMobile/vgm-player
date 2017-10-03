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

import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import net.volcanomobile.vgmplayer.Application;
import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

/**
 * Manage the interactions among the container service, the queue manager and the actual playback.
 */
public class PlaybackManager implements Playback.Callback {

    private static final String TAG = LogHelper.makeLogTag(PlaybackManager.class);

    private final QueueManager mQueueManager;
    private final Resources mResources;
    private Playback mPlayback;
    private final PlaybackServiceCallback mServiceCallback;
    private final MediaSessionCallback mMediaSessionCallback;

    private int mRepeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;

    private static final int RESTART_TRACK_ON_PREVIOUS_DURATION = 4000;

    public PlaybackManager(PlaybackServiceCallback serviceCallback, Resources resources,
                           QueueManager queueManager, Playback playback) {
        mServiceCallback = serviceCallback;
        mResources = resources;
        mQueueManager = queueManager;
        mMediaSessionCallback = new MediaSessionCallback();
        mPlayback = playback;
        mPlayback.setCallback(this);
    }

    public Playback getPlayback() {
        return mPlayback;
    }

    public MediaSessionCompat.Callback getMediaSessionCallback() {
        return mMediaSessionCallback;
    }

    /**
     * Handle a request to play music
     */
    void handlePlayRequest() {
        LogHelper.d(TAG, "handlePlayRequest: mState=" + mPlayback.getState());
        MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
        if (currentMusic != null) {
            mServiceCallback.onPlaybackStart();
            mPlayback.play(currentMusic);
        }
    }

    /**
     * Handle a request to pause music
     */
    public void handlePauseRequest() {
        LogHelper.d(TAG, "handlePauseRequest: mState=" + mPlayback.getState());
        if (mPlayback.isPlaying()) {
            mPlayback.pause();
            mServiceCallback.onPlaybackStop();
        }
        saveState();
    }

    /**
     * Handle a request to stop music
     *
     * @param withError Error message in case the stop has an unexpected cause. The error
     *                  message will be set in the PlaybackState and will be visible to
     *                  MediaController clients.
     */
    public void handleStopRequest(String withError) {
        LogHelper.d(TAG, "handleStopRequest: mState=" + mPlayback.getState() + " error=", withError);
        mPlayback.stop(true);
        mServiceCallback.onPlaybackStop();
        updatePlaybackState(withError);
        saveState();
    }

    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    public void updatePlaybackState(String error) {
        LogHelper.d(TAG, "updatePlaybackState, playback state=" + mPlayback.getState());
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (mPlayback != null && mPlayback.isConnected()) {
            position = mPlayback.getCurrentStreamPosition();
        }

        //noinspection ResourceType
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(getAvailableActions());

        int state = mPlayback.getState();

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, error);
            state = PlaybackStateCompat.STATE_ERROR;
        }
        //noinspection ResourceType
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());

        // Set the activeQueueItemId if the current index is valid.
        MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
        if (currentMusic != null) {
            stateBuilder.setActiveQueueItemId(currentMusic.getQueueId());
        }

        mServiceCallback.onPlaybackStateUpdated(stateBuilder.build());

        if (state == PlaybackStateCompat.STATE_PLAYING ||
                state == PlaybackStateCompat.STATE_PAUSED) {
            mServiceCallback.onNotificationRequired();
        }
    }

    private long getAvailableActions() {
        return PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_STOP |
                ( mPlayback.isPlaying() ? PlaybackStateCompat.ACTION_PAUSE : PlaybackStateCompat.ACTION_PLAY ) |
                PlaybackStateCompat.ACTION_SEEK_TO |
                PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED |
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE;
    }

    /**
     * Implementation of the Playback.Callback interface
     */
    @Override
    public void onCompletion() {
        // The media player finished playing the current song, so we go ahead
        // and start the next.

        if (PreferencesHelper.getInstance(Application.getInstance()).isPauseOnSongEnd()) {
            handlePauseRequest();
            mPlayback.setCurrentMediaId(null);
            updatePlaybackState(null);
            return;
        }

        if (mRepeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
            mPlayback.setCurrentMediaId(null);
            handlePlayRequest();
        } else {
            if (mQueueManager.skipQueuePosition(1, mRepeatMode == PlaybackStateCompat.REPEAT_MODE_ALL)) {
                handlePlayRequest();
                try {
                    mQueueManager.updateMetadata();
                } catch (IllegalStateException e) {
                    handleStopRequest(mResources.getString(R.string.error_loading_media));
                }
            } else {
                // If skipping was not possible, we stop and release the resources:
                handleStopRequest(null);
            }
        }
    }

    @Override
    public void onPlaybackStatusChanged(int state) {
        updatePlaybackState(null);
    }

    @Override
    public void onError(String error) {
        updatePlaybackState(error);
    }

    @Override
    public void setCurrentMediaId(String mediaId) {
        LogHelper.d(TAG, "setCurrentMediaId", mediaId);
        mQueueManager.setQueueFromMusic(mediaId, this);
    }

    private void saveState() {
        MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
        if(currentMusic != null) {
            String mediaId = currentMusic.getDescription().getMediaId();
            LogHelper.d(TAG, "saving latest mediaId" + mediaId);
            PreferencesHelper.getInstance(Application.getInstance()).setLatestMediaId(mediaId);
        }
        mQueueManager.savePlayingQueue();
    }

    public void restoreState() {
        boolean shuffleModeEnabled = PreferencesHelper.getInstance(Application.getInstance()).shuffleModeEnabled();
        mServiceCallback.onSetShuffleMode(shuffleModeEnabled
                ? PlaybackStateCompat.SHUFFLE_MODE_ALL
                : PlaybackStateCompat.SHUFFLE_MODE_NONE);

        mQueueManager.setShuffleModeEnabled(shuffleModeEnabled);

        mRepeatMode = PreferencesHelper.getInstance(Application.getInstance()).getRepeatMode();
        mServiceCallback.onSetRepeatMode(mRepeatMode);

        mQueueManager.restorePlayingQueue();

        String mediaId = PreferencesHelper.getInstance(Application.getInstance()).getLatestMediaId();
        if (!TextUtils.isEmpty(mediaId)) {
            mQueueManager.setCurrentQueueItem(mediaId);
            mQueueManager.updateMetadata();

            //noinspection ResourceType
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                    .setActions(getAvailableActions());

            stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f, SystemClock.elapsedRealtime());

            // Set the activeQueueItemId if the current index is valid.
            MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
            if (currentMusic != null) {
                stateBuilder.setActiveQueueItemId(currentMusic.getQueueId());
            }

            mServiceCallback.onPlaybackStateUpdated(stateBuilder.build());
        }
    }

    /**
     * Switch to a different Playback instance, maintaining all playback state, if possible.
     *
     * @param playback switch to this playback
     */
    public void switchToPlayback(Playback playback, boolean resumePlaying) {
        if (playback == null) {
            throw new IllegalArgumentException("Playback cannot be null");
        }
        // suspend the current one.
        int oldState = mPlayback.getState();
        String currentMediaId = mPlayback.getCurrentMediaId();
        mPlayback.stop(false);
        playback.setCallback(this);
        playback.setCurrentMediaId(currentMediaId);
        playback.start();
        // finally swap the instance
        mPlayback = playback;
        switch (oldState) {
            case PlaybackStateCompat.STATE_BUFFERING:
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_PAUSED:
                mPlayback.pause();
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
                if (resumePlaying && currentMusic != null) {
                    mPlayback.play(currentMusic);
                } else if (!resumePlaying) {
                    mPlayback.pause();
                } else {
                    mPlayback.stop(true);
                }
                break;
            case PlaybackStateCompat.STATE_NONE:
                break;
            default:
                LogHelper.d(TAG, "Default called. Old state is ", oldState);
        }
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPlay() {
            LogHelper.d(TAG, "play");
            if (mQueueManager.getCurrentMusic() == null) {
                mQueueManager.setRandomQueue(PlaybackManager.this);
            } else {
                handlePlayRequest();
            }
        }

        @Override
        public void onSkipToQueueItem(final long queueId) {
            LogHelper.d(TAG, "OnSkipToQueueItem:" + queueId);

            mQueueManager.setCurrentQueueItem(queueId);
            handlePlayRequest();
            try {
                mQueueManager.updateMetadata();
            } catch (IllegalStateException e) {
                handleStopRequest(mResources.getString(R.string.error_loading_media));
            }
        }

        @Override
        public void onSeekTo(final long position) {
            LogHelper.d(TAG, "onSeekTo:", position);
            mPlayback.seekTo(position);
        }

        @Override
        public void onPlayFromMediaId(final String mediaId, Bundle extras) {
            LogHelper.d(TAG, "playFromMediaId mediaId:", mediaId, "  extras=", extras);
            mQueueManager.setQueueFromMusic(mediaId, PlaybackManager.this);
        }

        @Override
        public void onPause() {
            LogHelper.d(TAG, "pause. current state=" + mPlayback.getState());
            handlePauseRequest();
        }

        @Override
        public void onStop() {
            LogHelper.d(TAG, "stop. current state=" + mPlayback.getState());

            handleStopRequest(null);
        }

        @Override
        public void onSkipToNext() {
            LogHelper.d(TAG, "skipToNext");

            if (mQueueManager.skipQueuePosition(1, true)) {
                handlePlayRequest();
            } else {
                handleStopRequest("Cannot skip");
            }
            try {
                mQueueManager.updateMetadata();
            } catch (IllegalStateException e) {
                handleStopRequest(mResources.getString(R.string.error_loading_media));
            }
        }

        @Override
        public void onSkipToPrevious() {
            LogHelper.d(TAG, "skipToPrevious");

            if (mPlayback.getCurrentStreamPosition() > RESTART_TRACK_ON_PREVIOUS_DURATION) {
                // start from beginning of current track
                mPlayback.setCurrentMediaId(null);
                handlePlayRequest();
            } else {
                if (mQueueManager.skipQueuePosition(-1, true)) {
                    handlePlayRequest();
                } else {
                    handleStopRequest("Cannot skip");
                }
                try {
                    mQueueManager.updateMetadata();
                } catch (IllegalStateException e) {
                    handleStopRequest(mResources.getString(R.string.error_loading_media));
                }
            }
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            LogHelper.d(TAG, "onSetShuffleMode: ", shuffleMode);

            mServiceCallback.onSetShuffleMode(shuffleMode);
            mQueueManager.setShuffleModeEnabled(shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE);
            PreferencesHelper.getInstance(Application.getInstance())
                    .setShuffleModeEnabled(shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE);
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            LogHelper.d(TAG, "onSetRepeatMode: ", repeatMode);

            mRepeatMode = repeatMode;
            mServiceCallback.onSetRepeatMode(repeatMode);
            PreferencesHelper.getInstance(Application.getInstance()).setRepeatMode(repeatMode);
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            LogHelper.d(TAG, "onRemoveQueueItem: ", description.toString());

            if (mQueueManager.removeQueueItem(description.getMediaId())) {
                if(mPlayback.isPlaying()) {
                    handlePlayRequest();
                } else {
                    updatePlaybackState(null);
                }
            }

            if (mQueueManager.getPlayingQueueSize() == 0) {
                handleStopRequest(null);
            }
        }

        /**
         * Handle free and contextual searches.
         * <p/>
         * All voice searches on Android Auto are sent to this method through a connected
         * {@link android.support.v4.media.session.MediaControllerCompat}.
         * <p/>
         * Threads and async handling:
         * Search, as a potentially slow operation, should run in another thread.
         * <p/>
         * Since this method runs on the main thread, most apps with non-trivial metadata
         * should defer the actual search to another thread (for example, by using
         * an {@link android.os.AsyncTask} as we do here).
         **/
        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            LogHelper.d(TAG, "playFromSearch  query=", query, " extras=", extras);

            mQueueManager.setQueueFromSearch(query, extras, PlaybackManager.this);
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            LogHelper.d(TAG, "onAddQueueItem");
            if(mQueueManager.enqueueEnd(description)) {
                updatePlaybackState(null);
            }
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description, int index) {
            LogHelper.d(TAG, "onAddQueueItem at ", index);
            if(mQueueManager.enqueueAt(description, index)) {
                updatePlaybackState(null);
            }
        }
    }

    public interface PlaybackServiceCallback {
        void onPlaybackStart();

        void onNotificationRequired();

        void onPlaybackStop();

        void onPlaybackStateUpdated(PlaybackStateCompat newState);

        void onSetShuffleMode(int shuffleMode);

        void onSetRepeatMode(int mode);
    }
}
