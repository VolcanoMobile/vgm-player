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

package net.volcanomobile.vgmplayer.utils;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;

import net.volcanomobile.vgmplayer.VoiceSearchParams;
import net.volcanomobile.vgmplayer.model.MusicProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.reactivex.Single;
import io.reactivex.functions.Function;

import static net.volcanomobile.vgmplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FOLDER;
import static net.volcanomobile.vgmplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;

/**
 * Utility class to help on queue related tasks.
 */
public class QueueHelper {

    private static final String TAG = LogHelper.makeLogTag(QueueHelper.class);

    private static final int RANDOM_QUEUE_SIZE = 10;

    private static Random sRandom = new Random(System.currentTimeMillis());

    @NonNull
    public static Single<List<MediaSessionCompat.QueueItem>> getPlayingQueue(String mediaId,
            MusicProvider musicProvider) {

        // extract the browsing hierarchy from the media ID:
        final String[] hierarchy = MediaIDHelper.getHierarchy(mediaId);

        if (hierarchy.length != 2) {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId);
            return Single.error(new Exception("Could not build a playing queue for this mediaId: " + mediaId));
        }

        String categoryType = hierarchy[0];
        String categoryValue = hierarchy[1];
        LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categoryValue);

        Single<List<MediaMetadataCompat>> tracks = null;

        // This sample only supports folder and by_search category types.
        if (categoryType.equals(MEDIA_ID_MUSICS_BY_FOLDER)) {
            tracks = musicProvider.searchMusicByFolder(categoryValue);
        } else if (categoryType.equals(MEDIA_ID_MUSICS_BY_SEARCH)) {
            tracks = musicProvider.searchMusicBySongTitle(categoryValue);
        }

        if (tracks == null) {
            LogHelper.e(TAG, "Unrecognized category type: ", categoryType, " for media ", mediaId);
            return Single.error(new Exception("Unrecognized category type: " + categoryType + " for media " + mediaId));
        }

        return tracks.map(new Function<List<MediaMetadataCompat>, List<MediaSessionCompat.QueueItem>>() {
            @Override
            public List<MediaSessionCompat.QueueItem> apply(@io.reactivex.annotations.NonNull List<MediaMetadataCompat> mediaMetadataCompats) throws Exception {
                return convertToQueue(mediaMetadataCompats, hierarchy[0], hierarchy[1]);
            }
        });
    }

    public static Single<List<MediaSessionCompat.QueueItem>> getPlayingQueueFromSearch(final String query,
                                                                               Bundle queryParams, MusicProvider musicProvider) {

        LogHelper.d(TAG, "Creating playing queue for musics from search: ", query,
                " params=", queryParams);

        VoiceSearchParams params = new VoiceSearchParams(query, queryParams);

        LogHelper.d(TAG, "VoiceSearchParams: ", params);

        if (params.isAny) {
            // If isAny is true, we will play anything. This is app-dependent, and can be,
            // for example, favorite playlists, "I'm feeling lucky", most recent, etc.
            return getRandomQueue(musicProvider);
        }

        Single<List<MediaMetadataCompat>> result = null;

        // TODO handle structured searches

//        if (params.isAlbumFocus) {
//            result = musicProvider.searchMusicByAlbum(params.album);
//        } else if (params.isGenreFocus) {
//            result = musicProvider.getMusicsByGenre(params.genre);
//        } else if (params.isArtistFocus) {
//            result = musicProvider.searchMusicByArtist(params.artist);
//        } else if (params.isSongFocus) {
//            result = musicProvider.searchMusicBySongTitle(params.song);
//        }

        // If there was no results using media focus parameter, we do an unstructured query.
        // This is useful when the user is searching for something that looks like an artist
        // to Google, for example, but is not. For example, a user searching for Madonna on
        // a PodCast application wouldn't get results if we only looked at the
        // Artist (podcast author). Then, we can instead do an unstructured search.
        if (params.isUnstructured || result == null) {
            // To keep it simple for this example, we do unstructured searches on the
            // song title only. A real world application could search on other fields as well.
            result = musicProvider.searchMusicBySongTitle(query);
        }

        return result.map(new Function<List<MediaMetadataCompat>, List<MediaSessionCompat.QueueItem>>() {
            @Override
            public List<MediaSessionCompat.QueueItem> apply(@NonNull List<MediaMetadataCompat> mediaMetadataCompats) throws Exception {
                return convertToQueue(mediaMetadataCompats, MEDIA_ID_MUSICS_BY_SEARCH, query);
            }
        });
    }

    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
             String mediaId) {
        if (queue != null) {
            int index = 0;
            for (MediaSessionCompat.QueueItem item : queue) {
                if (mediaId.equals(item.getDescription().getMediaId())) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
             long queueId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (queueId == item.getQueueId()) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static MediaDescriptionCompat buildMediaDescription(MediaMetadataCompat metadata, String categoryType, String categoryValue) {
        // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
        // at the QueueItem media IDs.
        String mediaId = metadata.getDescription().getMediaId();
        String hierarchyAwareMediaID = MediaIDHelper.createMediaID(mediaId, categoryType, categoryValue);

        Bundle extras = new Bundle();
        extras.putLong(MusicProvider.EXTRA_MEDIA_DURATION, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000);

        return new MediaDescriptionCompat.Builder()
                .setMediaId(hierarchyAwareMediaID)
                .setTitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                .setSubtitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
                .setIconUri(Uri.parse(metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)))
                .setExtras(extras)
                .build();
    }

    private static List<MediaSessionCompat.QueueItem> convertToQueue(
            Iterable<MediaMetadataCompat> tracks, String categoryType, String categoryValue) {
        List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();

        for (MediaMetadataCompat track : tracks) {
            MediaDescriptionCompat description = buildMediaDescription(track, categoryType, categoryValue);

            MediaSessionCompat.QueueItem item = new MediaSessionCompat.QueueItem(
                    description, getNextQueueId());
            queue.add(item);
        }

        return queue;
    }

    public static long getNextQueueId() {
        return sRandom.nextLong();
    }

    /**
     * Create a random queue with at most {@link #RANDOM_QUEUE_SIZE} elements.
     *
     * @param musicProvider the provider used for fetching music.
     * @return list containing {@link MediaSessionCompat.QueueItem}'s
     */
    public static Single<List<MediaSessionCompat.QueueItem>> getRandomQueue(MusicProvider musicProvider) {
        return musicProvider.getShuffledMusic().map(new Function<Iterable<MediaMetadataCompat>, List<MediaSessionCompat.QueueItem>>() {
            @Override
            public List<MediaSessionCompat.QueueItem> apply(@NonNull Iterable<MediaMetadataCompat> shuffled) throws Exception {
                List<MediaMetadataCompat> result = new ArrayList<>(RANDOM_QUEUE_SIZE);
                for (MediaMetadataCompat metadata: shuffled) {
                    if (result.size() == RANDOM_QUEUE_SIZE) {
                        break;
                    }
                    result.add(metadata);
                }
                return convertToQueue(result, MEDIA_ID_MUSICS_BY_SEARCH, "random");
            }
        });
    }

    public static boolean isIndexPlayable(int index, List<MediaSessionCompat.QueueItem> queue) {
        return (queue != null && index >= 0 && index < queue.size());
    }

    /**
     * Determine if two queues contain identical media id's in order.
     *
     * @param list1 containing {@link MediaSessionCompat.QueueItem}'s
     * @param list2 containing {@link MediaSessionCompat.QueueItem}'s
     * @return boolean indicating whether the queue's match
     */
    public static boolean equals(List<MediaSessionCompat.QueueItem> list1,
                                 List<MediaSessionCompat.QueueItem> list2) {
        if (list1 == list2) {
            return true;
        }
        if (list1 == null || list2 == null) {
            return false;
        }
        if (list1.size() != list2.size()) {
            return false;
        }
        for (int i=0; i<list1.size(); i++) {
            if (list1.get(i).getQueueId() != list2.get(i).getQueueId()) {
                return false;
            }
            if (!TextUtils.equals(list1.get(i).getDescription().getMediaId(),
                    list2.get(i).getDescription().getMediaId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determine if queue item matches the currently playing queue item
     *
     * @param controller
     * @param queueItem to compare to currently playing {@link MediaSessionCompat.QueueItem}
     * @return boolean indicating whether queue item matches currently playing queue item
     */
    public static boolean isQueueItemPlaying(MediaControllerCompat controller,
                                             MediaSessionCompat.QueueItem queueItem) {
        // Queue item is considered to be playing or paused based on both the controller's
        // current media id and the controller's active queue item id
        if (controller != null && controller.getPlaybackState() != null) {
            long currentPlayingQueueId = controller.getPlaybackState().getActiveQueueItemId();
            String currentPlayingMediaId = controller.getMetadata().getDescription()
                    .getMediaId();
            String itemMusicId = MediaIDHelper.extractMusicIDFromMediaID(
                    queueItem.getDescription().getMediaId());
            if (queueItem.getQueueId() == currentPlayingQueueId
                    && currentPlayingMediaId != null
                    && TextUtils.equals(currentPlayingMediaId, itemMusicId)) {
                return true;
            }
        }
        return false;
    }

    public static int getPlayingIndex(MediaControllerCompat controller) {
        if (controller != null && controller.getPlaybackState() != null) {
            long currentPlayingQueueId = controller.getPlaybackState().getActiveQueueItemId();
            List<MediaSessionCompat.QueueItem> queue = controller.getQueue();
            if (queue != null) {
                for (int i = 0; i < queue.size(); i++) {
                    if (queue.get(i).getQueueId() == currentPlayingQueueId) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }
}
