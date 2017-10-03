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

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.model.MusicProvider;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.MediaIDHelper;
import net.volcanomobile.vgmplayer.utils.QueueHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.ResourceSingleObserver;
import io.reactivex.schedulers.Schedulers;

import static net.volcanomobile.vgmplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_FROM_QUEUE;

/**
 * Simple data provider for queues. Keeps track of a current queue and a current index in the
 * queue. Also provides methods to set the current queue based on common queries, relying on a
 * given MusicProvider to provide the actual media metadata.
 */
public class QueueManager {
    private static final String TAG = LogHelper.makeLogTag(QueueManager.class);

    private static final String PLAYING_QUEUE_SAVE_FILE = "playingqueue.save";
    private static final String SHUFFLE_QUEUE_SAVE_FILE = "shufflequeue.save";

    private static final Type QUEUE_TOKEN = new TypeToken<ArrayList<String>>(){}.getType();

    private MusicProvider mMusicProvider;
    private MetadataUpdateListener mListener;
    private Context mContext;
    private Gson mGson;

    // "Now playing" queue:
    private List<MediaSessionCompat.QueueItem> mPlayingQueue;
    private List<MediaSessionCompat.QueueItem> mShuffleQueue;
    private int mCurrentIndex;
    private boolean mShuffledEnabled = false;

    public QueueManager(@NonNull MusicProvider musicProvider,
                        @NonNull Context context,
                        @NonNull MetadataUpdateListener listener) {
        this.mMusicProvider = musicProvider;
        this.mListener = listener;
        this.mContext = context;
        mGson = new GsonBuilder().create();

        mPlayingQueue = null;
        mShuffleQueue = null;

        mCurrentIndex = 0;
    }

    private List<MediaSessionCompat.QueueItem> getCurrentQueue() {
        return mShuffledEnabled ? mShuffleQueue : mPlayingQueue;
    }

    private boolean isSameBrowsingCategory(@NonNull String mediaId) {
        String[] newBrowseHierarchy = MediaIDHelper.getHierarchy(mediaId);
        MediaSessionCompat.QueueItem current = getCurrentMusic();
        if (current == null) {
            return false;
        }
        String[] currentBrowseHierarchy = MediaIDHelper.getHierarchy(
                current.getDescription().getMediaId());

        return Arrays.equals(newBrowseHierarchy, currentBrowseHierarchy);
    }

    private void setCurrentQueueIndex(int index) {
        if (index >= 0 && index < getCurrentQueue().size()) {
            mCurrentIndex = index;
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex);
        }
    }

    boolean setCurrentQueueItem(long queueId) {
        // set the current index on queue from the queue Id:
        int index = QueueHelper.getMusicIndexOnQueue(getCurrentQueue(), queueId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    boolean setCurrentQueueItem(String mediaId) {
        // set the current index on queue from the music Id:
        int index = QueueHelper.getMusicIndexOnQueue(getCurrentQueue(), mediaId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    int getPlayingQueueSize() {
        return getCurrentQueue() != null ? getCurrentQueue().size() : 0;
    }

    void savePlayingQueue() {
        // TODO use a background thread here
        if (mPlayingQueue != null && mPlayingQueue.size() > 0) {
            List<String> mediaIds = new ArrayList<>();

            for (MediaSessionCompat.QueueItem item : mPlayingQueue) {
                mediaIds.add(item.getDescription().getMediaId());
            }

            Writer writer = null;
            try {
                writer = new OutputStreamWriter(mContext.openFileOutput(PLAYING_QUEUE_SAVE_FILE, 0));
                mGson.toJson(mediaIds, writer);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            File shuffle = new File(mContext.getFilesDir(), PLAYING_QUEUE_SAVE_FILE);
            //noinspection ResultOfMethodCallIgnored
            shuffle.delete();
        }

        if (mShuffleQueue != null && mShuffleQueue.size() > 0) {
            List<String> mediaIds = new ArrayList<>();

            for (MediaSessionCompat.QueueItem item : mShuffleQueue) {
                mediaIds.add(item.getDescription().getMediaId());
            }

            Writer writer = null;
            try {
                writer = new OutputStreamWriter(mContext.openFileOutput(SHUFFLE_QUEUE_SAVE_FILE, 0));
                mGson.toJson(mediaIds, writer);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            File shuffle = new File(mContext.getFilesDir(), SHUFFLE_QUEUE_SAVE_FILE);
            //noinspection ResultOfMethodCallIgnored
            shuffle.delete();
        }
    }

    void restorePlayingQueue() {
        // TODO use a background thread here
        Reader reader = null;
        try {
            reader = new InputStreamReader(mContext.openFileInput(PLAYING_QUEUE_SAVE_FILE));
            ArrayList<String> mediaIds = mGson.fromJson(reader, QUEUE_TOKEN);

            if (mediaIds != null) {
                mPlayingQueue = new ArrayList<>();
                for (String mediaId : mediaIds) {
                    String musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId);

                    try {
                        MediaMetadataCompat metadata = mMusicProvider.getMusic(musicId)
                                .subscribeOn(Schedulers.io()).blockingGet();

                        if (metadata != null) {
                            Bundle extras = new Bundle();
                            extras.putLong(MusicProvider.EXTRA_MEDIA_DURATION, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000);

                            MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                                    .setMediaId(mediaId)
                                    .setTitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                                    .setSubtitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
                                    .setIconUri(Uri.parse(metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)))
                                    .setExtras(extras)
                                    .build();

                            mPlayingQueue.add(new MediaSessionCompat.QueueItem(description, QueueHelper.getNextQueueId()));
                        }
                    }  catch (Exception e) {
                        // noop
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            reader = new InputStreamReader(mContext.openFileInput(SHUFFLE_QUEUE_SAVE_FILE));
            ArrayList<String> mediaIds = mGson.fromJson(reader, QUEUE_TOKEN);

            if (mediaIds != null) {
                mShuffleQueue = new ArrayList<>();
                for (String mediaId : mediaIds) {
                    String musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId);

                    try {
                        MediaMetadataCompat metadata = mMusicProvider.getMusic(musicId)
                                .subscribeOn(Schedulers.io()).blockingGet();

                        if (metadata != null) {
                            Bundle extras = new Bundle();
                            extras.putLong(MusicProvider.EXTRA_MEDIA_DURATION, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000);

                            MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                                    .setMediaId(mediaId)
                                    .setTitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                                    .setSubtitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
                                    .setIconUri(Uri.parse(metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)))
                                    .setExtras(extras)
                                    .build();

                            mShuffleQueue.add(new MediaSessionCompat.QueueItem(description, QueueHelper.getNextQueueId()));
                        }
                    } catch (Exception e) {
                        // noop
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        mListener.onQueueUpdated(mContext.getString(R.string.queue_title), getCurrentQueue());
    }

    boolean removeQueueItem(String mediaId) {
        boolean result = false;
        MediaSessionCompat.QueueItem currentMusic = getCurrentMusic();

        int index = QueueHelper.getMusicIndexOnQueue(getCurrentQueue(), mediaId);
        if (index == -1) {
            return false;
        }
        getCurrentQueue().remove(index);

        if (getCurrentQueue().size() == 0) {
            mListener.onMetadataChanged(null);
            mListener.onQueueCleared();
            mCurrentIndex = 0;

            mPlayingQueue = null;
            mShuffleQueue = null;

            return false;
        }

        if (mCurrentIndex >= getCurrentQueue().size()) {
            mCurrentIndex = getCurrentQueue().size() - 1;
        }

        mListener.onQueueUpdated(mContext.getString(R.string.queue_title), getCurrentQueue());

        if (index < mCurrentIndex) {
            mCurrentIndex--;
        }

        if (currentMusic.getDescription().getMediaId().equals(mediaId)) {
            currentMusic = getCurrentMusic();
            final String musicId = MediaIDHelper.extractMusicIDFromMediaID(
                    currentMusic.getDescription().getMediaId());

            if (musicId != null) {
                mMusicProvider.getMusic(musicId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new ResourceSingleObserver<MediaMetadataCompat>() {
                            @Override
                            public void onSuccess(@io.reactivex.annotations.NonNull MediaMetadataCompat mediaMetadataCompat) {
                                mListener.onMetadataChanged(mediaMetadataCompat);
                                dispose();
                            }

                            @Override
                            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                mListener.onMetadataChanged(null);
                                dispose();
                            }
                        });
            }

            result = true;
        }

        if (mShuffledEnabled && mPlayingQueue != null) {
            index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId);
            mPlayingQueue.remove(index);
        }

        return result;
    }

    boolean skipQueuePosition(int amount, boolean loop) {
        if(getCurrentQueue() == null || getCurrentQueue().size() == 0) {
            return false;
        }

        int index = mCurrentIndex + amount;

        if (index < 0) {
            // skip backwards before the first song will keep you on the first song
            index = 0;
        } else {
            if (loop) {
                // skip forwards when in last song will cycle back to start of the queue
                index %= getCurrentQueue().size();
            } else if (index >= getCurrentQueue().size()) {
                return false;
            }
        }

        if (!QueueHelper.isIndexPlayable(index, getCurrentQueue())) {
            LogHelper.e(TAG, "Cannot increment queue index by ", amount,
                    ". Current=", mCurrentIndex, " queue length=", getCurrentQueue().size());
            return false;
        }

        mCurrentIndex = index;
        return true;
    }

    void setQueueFromSearch(String query, Bundle extras, @NonNull final PlaybackManager playbackManager) {
        QueueHelper.getPlayingQueueFromSearch(query, extras, mMusicProvider)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new ResourceSingleObserver<List<MediaSessionCompat.QueueItem>>() {
                    @Override
                    public void onSuccess(@io.reactivex.annotations.NonNull List<MediaSessionCompat.QueueItem> queueItems) {
                        setCurrentQueue(mContext.getString(R.string.search_queue_title), queueItems);
                        updateMetadata();
                        if (!queueItems.isEmpty()) {
                            playbackManager.handlePlayRequest();
                        } else {
                            playbackManager.updatePlaybackState(mContext.getString(R.string.no_search_results));
                        }
                        dispose();
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        setCurrentQueue(null, null);
                        updateMetadata();
                        playbackManager.handleStopRequest(e.getMessage());
                        dispose();
                    }
                });
    }

    void setRandomQueue(@NonNull final PlaybackManager playbackManager) {
        QueueHelper.getRandomQueue(mMusicProvider)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new ResourceSingleObserver<List<MediaSessionCompat.QueueItem>>() {
                    @Override
                    public void onSuccess(@io.reactivex.annotations.NonNull List<MediaSessionCompat.QueueItem> queueItems) {
                        setCurrentQueue(mContext.getString(R.string.random_queue_title), queueItems);
                        updateMetadata();
                        playbackManager.handlePlayRequest();
                        dispose();
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        setCurrentQueue(null, null);
                        updateMetadata();
                        playbackManager.handleStopRequest(e.getMessage());
                        dispose();
                    }
                });
    }

    void setShuffleModeEnabled(boolean enabled) {

        if (!mShuffledEnabled && enabled) {
            if (mPlayingQueue != null) {
                mShuffleQueue = new ArrayList<>(mPlayingQueue);
                MediaSessionCompat.QueueItem item = getCurrentMusic();
                if (item != null) {
                    mShuffleQueue.remove(item);
                    Collections.shuffle(mShuffleQueue);
                    mShuffleQueue.add(0, item);
                    mCurrentIndex = 0;
                }
            } else {
                mShuffleQueue = new ArrayList<>();
                mCurrentIndex = 0;
            }
            mListener.onQueueUpdated(mContext.getString(R.string.queue_title), mShuffleQueue);
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex);
        }

        if (mShuffledEnabled && !enabled) {
            if (mPlayingQueue != null) {
                MediaSessionCompat.QueueItem item = getCurrentMusic();
                if (item != null) {
                    String mediaId = item.getDescription().getMediaId();
                    int index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId);
                    mCurrentIndex = Math.max(index, 0);
                    mShuffleQueue = null;
                }
            } else {
                mPlayingQueue = mShuffleQueue;
                mShuffleQueue = null;
            }
            mListener.onQueueUpdated(mContext.getString(R.string.queue_title), mPlayingQueue);
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex);
        }

        mShuffledEnabled = enabled;
    }

    boolean enqueueEnd(@NonNull MediaDescriptionCompat description) {
        if (mShuffledEnabled) {
            if (mShuffleQueue == null) {
                mShuffleQueue = new ArrayList<>();
                mCurrentIndex = 0;
            }
        } else {
            if (mPlayingQueue == null) {
                mPlayingQueue = new ArrayList<>();
                mCurrentIndex = 0;
            }
        }

        long id = QueueHelper.getNextQueueId();
        String musicId = MediaIDHelper.extractMusicIDFromMediaID(description.getMediaId());
        String mediaId = MediaIDHelper.createMediaID(musicId, MEDIA_ID_MUSICS_FROM_QUEUE, String.valueOf(id));

        MediaDescriptionCompat queueDescription = new MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(description.getTitle())
                .setSubtitle(description.getSubtitle())
                .setIconUri(description.getIconUri())
                .setExtras(description.getExtras())
                .build();

        MediaSessionCompat.QueueItem item = new MediaSessionCompat.QueueItem(
                queueDescription, id);

        if (mPlayingQueue != null) {
            mPlayingQueue.add(item);
        }

        if (mShuffleQueue != null) {
            mShuffleQueue.add(item);
        }

        mListener.onQueueUpdated(mContext.getString(R.string.queue_title), getCurrentQueue());
        if (getCurrentQueue().size() == 1) {
            updateMetadata();
        }

        return getCurrentQueue().size() == 1;
    }

    boolean enqueueAt(@NonNull MediaDescriptionCompat description, int index) {
        if (mShuffledEnabled) {
            if (mShuffleQueue == null) {
                mShuffleQueue = new ArrayList<>();
                mCurrentIndex = 0;
            }
        } else {
            if (mPlayingQueue == null) {
                mPlayingQueue = new ArrayList<>();
                mCurrentIndex = 0;
            }
        }

        long id = QueueHelper.getNextQueueId();
        String musicId = MediaIDHelper.extractMusicIDFromMediaID(description.getMediaId());
        String mediaId = MediaIDHelper.createMediaID(musicId, MEDIA_ID_MUSICS_FROM_QUEUE, String.valueOf(id));

        MediaDescriptionCompat queueDescription = new MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(description.getTitle())
                .setSubtitle(description.getSubtitle())
                .setIconUri(description.getIconUri())
                .setExtras(description.getExtras())
                .build();

        MediaSessionCompat.QueueItem item = new MediaSessionCompat.QueueItem(
                queueDescription, id);

        index = Math.min(index, getCurrentQueue().size());
        if (mPlayingQueue != null) {
            // TODO insert after corresponding item
            mPlayingQueue.add(index, item);
        }
        if (mShuffleQueue != null) {
            mShuffleQueue.add(index, item);
        }

        mListener.onQueueUpdated(mContext.getString(R.string.queue_title), getCurrentQueue());
        if(index <= mCurrentIndex) {
            mCurrentIndex = Math.min(getCurrentQueue().size() - 1, mCurrentIndex + 1);
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex);
        }

        if (getCurrentQueue().size() == 1) {
            updateMetadata();
        }
        return getCurrentQueue().size() == 1;
    }

    void setQueueFromMusic(final String mediaId, @NonNull final PlaybackManager playbackManager) {
        LogHelper.d(TAG, "setQueueFromMusic", mediaId);

        // The mediaId used here is not the unique musicId. This one comes from the
        // MediaBrowser, and is actually a "hierarchy-aware mediaID": a concatenation of
        // the hierarchy in MediaBrowser and the actual unique musicID. This is necessary
        // so we can build the correct playing queue, based on where the track was
        // selected from.
        QueueHelper.getPlayingQueue(mediaId, mMusicProvider)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new ResourceSingleObserver<List<MediaSessionCompat.QueueItem>>() {
                    @Override
                    public void onSuccess(@io.reactivex.annotations.NonNull List<MediaSessionCompat.QueueItem> queueItems) {
                        setCurrentQueue(mContext.getString(R.string.queue_title), queueItems, mediaId);
                        playbackManager.handlePlayRequest();
                        updateMetadata();
                        dispose();
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        setCurrentQueue(null, null);
                        playbackManager.handleStopRequest(e.getMessage());
                        updateMetadata();
                        dispose();
                    }
                });
    }

    MediaSessionCompat.QueueItem getCurrentMusic() {
        if (!QueueHelper.isIndexPlayable(mCurrentIndex, getCurrentQueue())) {
            return null;
        }
        return getCurrentQueue().get(mCurrentIndex);
    }

    private void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue) {
        setCurrentQueue(title, newQueue, null);
    }

    private void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue,
                                 String initialMediaId) {
        if (!mShuffledEnabled) {
            mPlayingQueue = newQueue;
        } else  {
            if (mPlayingQueue != null) {
                mPlayingQueue = newQueue;
            }

            mShuffleQueue = new ArrayList<>(newQueue);
            MediaSessionCompat.QueueItem first = null;
            if (initialMediaId != null) {
                int index = QueueHelper.getMusicIndexOnQueue(mShuffleQueue, initialMediaId);
                first = mShuffleQueue.remove(index);
            }
            Collections.shuffle(mShuffleQueue);
            if (first != null) {
                mShuffleQueue.add(0, first);
            }
        }

        int index = 0;
        if (initialMediaId != null) {
            index = QueueHelper.getMusicIndexOnQueue(getCurrentQueue(), initialMediaId);
        }

        mCurrentIndex = Math.max(index, 0);
        mListener.onQueueUpdated(title, getCurrentQueue());
    }

    void updateMetadata() {
        MediaSessionCompat.QueueItem currentMusic = getCurrentMusic();
        if (currentMusic == null) {
            mListener.onMetadataRetrieveError();
            return;
        }
        final String musicId = MediaIDHelper.extractMusicIDFromMediaID(
                currentMusic.getDescription().getMediaId());

        mMusicProvider.getMusic(musicId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new ResourceSingleObserver<MediaMetadataCompat>() {
                    @Override
                    public void onSuccess(@io.reactivex.annotations.NonNull MediaMetadataCompat mediaMetadataCompat) {
                        mListener.onMetadataChanged(mediaMetadataCompat);
                        dispose();
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        mListener.onMetadataChanged(null);
                        dispose();
                    }
                });
    }

    public interface MetadataUpdateListener {
        void onMetadataChanged(MediaMetadataCompat metadata);
        void onMetadataRetrieveError();
        void onCurrentQueueIndexUpdated(int queueIndex);
        void onQueueUpdated(@Nullable String title, @Nullable List<MediaSessionCompat.QueueItem> newQueue);
        void onQueueCleared();
    }
}
