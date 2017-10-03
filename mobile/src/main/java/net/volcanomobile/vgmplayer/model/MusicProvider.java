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

package net.volcanomobile.vgmplayer.model;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.text.TextUtils;
import android.widget.Toast;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.dao.AlbumDao;
import net.volcanomobile.vgmplayer.dao.AppDatabase;
import net.volcanomobile.vgmplayer.dao.MediaDao;
import net.volcanomobile.vgmplayer.dao.MediaWithAlbum;
import net.volcanomobile.vgmplayer.provider.AlbumArtsContract;
import net.volcanomobile.vgmplayer.utils.DrawableUtils;
import net.volcanomobile.vgmplayer.utils.Handlers;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.MediaIDHelper;
import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.observers.ResourceSingleObserver;
import io.reactivex.schedulers.Schedulers;

import static net.volcanomobile.vgmplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FOLDER;
import static net.volcanomobile.vgmplayer.utils.MediaIDHelper.MEDIA_ID_ROOT;
import static net.volcanomobile.vgmplayer.utils.MediaIDHelper.createMediaID;
/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
public class MusicProvider {

    public static final String CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__";

    public static final String EXTRA_MEDIA_DURATION = "__EXTRA_MEDIA_DURATION__";

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    private final Context mContext;
    private final AlbumDao mAlbumDao;
    private final MediaDao mMediaDao;

    private final Uri mFolderIconUri;
    private final String mNoAlbumArtIconUri;

    @NonNull
    private final Set<String> mMediaBrowserCallerPackageNames = new HashSet<>();

    public MusicProvider(Context context) {
        mContext = context;
        mAlbumDao = AppDatabase.getInstance(mContext).albumDao();
        mMediaDao = AppDatabase.getInstance(mContext).mediaDao();

        mFolderIconUri = DrawableUtils.getUriToDrawable(context, R.drawable.ic_folder_grey_500_48dp);
        mNoAlbumArtIconUri = DrawableUtils.getUriToDrawable(context, R.drawable.ic_albumart_cd_48dp).toString();
    }

    public void addClient(@NonNull final String clientPackageName) {
        mMediaBrowserCallerPackageNames.add(clientPackageName);
    }

    public void deleteMusic(@NonNull final String musicId) {
        Single.fromCallable(new Callable<MediaWithAlbum>() {
            @Override
            public MediaWithAlbum call() throws Exception {
                return mMediaDao.loadById(Long.valueOf(musicId));
            }
        }).subscribeOn(Schedulers.io()).subscribe(new ResourceSingleObserver<MediaWithAlbum>() {
            @Override
            public void onSuccess(@io.reactivex.annotations.NonNull MediaWithAlbum mediaWithAlbum) {
                final File file = new File(Uri.parse(mediaWithAlbum.getData()).getPath());

                if (file.delete()) {
                    Handlers.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, mContext.getString(R.string.delete_success, file.getName()), Toast.LENGTH_LONG).show();
                        }
                    });
                    mMediaDao.deleteById(mediaWithAlbum.getUid());
                    List<MediaWithAlbum> medias = mMediaDao.loadByAlbumIds(mediaWithAlbum.getAlbumId()).blockingFirst();
                    if(medias.size() == 0) {
                        mAlbumDao.deleteById(mediaWithAlbum.getAlbumId());
                    }
                } else {
                    Handlers.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, mContext.getString(R.string.delete_failed, file.getPath()), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                dispose();
            }

            @Override
            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                dispose();
            }
        });
    }

    /**
     * Get an iterator over a shuffled collection of all songs
     */
    public Single<List<MediaMetadataCompat>> getShuffledMusic() {
        return mMediaDao.loadRandom()
                .map(new Function<List<MediaWithAlbum>, List<MediaMetadataCompat>>() {
                    @Override
                    public List<MediaMetadataCompat> apply(@io.reactivex.annotations.NonNull List<MediaWithAlbum> mediaWithAlbumList) throws Exception {
                        List<MediaMetadataCompat> medias = new ArrayList<>();

                        for(MediaWithAlbum media : mediaWithAlbumList) {
                            medias.add(buildMediaMetadata(media));
                        }

                        return medias;
                    }
                })
                .first(new ArrayList<MediaMetadataCompat>(0));
    }

    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     *
     */
    public Single<List<MediaMetadataCompat>> searchMusicBySongTitle(@NonNull final String query) {
        return mMediaDao.loadByTitleSearch('%' + query + '%')
                .map(new Function<List<MediaWithAlbum>, List<MediaMetadataCompat>>() {
                    @Override
                    public List<MediaMetadataCompat> apply(@io.reactivex.annotations.NonNull List<MediaWithAlbum> mediaWithAlbumList) throws Exception {
                        List<MediaMetadataCompat> medias = new ArrayList<>();

                        for(MediaWithAlbum media : mediaWithAlbumList) {
                            medias.add(buildMediaMetadata(media));
                        }

                        return medias;
                    }
                })
                .first(new ArrayList<MediaMetadataCompat>(0));
    }

    public Single<List<MediaMetadataCompat>> searchMusicByFolder(@NonNull final String folder) {
        return mMediaDao.loadByFolder(folder)
                .map(new Function<List<MediaWithAlbum>, List<MediaMetadataCompat>>() {
                    @Override
                    public List<MediaMetadataCompat> apply(@io.reactivex.annotations.NonNull List<MediaWithAlbum> mediaWithAlbumList) throws Exception {
                        List<MediaMetadataCompat> musics = new ArrayList<>();

                        for(MediaWithAlbum media : mediaWithAlbumList) {
                            musics.add(buildMediaMetadata(media));
                        }

                        return musics;
                    }
                })
                .firstOrError();
    }

    /**
     * Return the MediaMetadataCompat for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
    @NonNull
    public Single<MediaMetadataCompat> getMusic(final @NonNull String musicId) {
        return Single.fromCallable(new Callable<MediaWithAlbum>() {
            @Override
            public MediaWithAlbum call() throws Exception {
                return mMediaDao.loadById(Long.valueOf(musicId));
            }
        }).map(new Function<MediaWithAlbum, MediaMetadataCompat>() {
            @Override
            public MediaMetadataCompat apply(@io.reactivex.annotations.NonNull MediaWithAlbum mediaWithAlbum) throws Exception {
                return buildMediaMetadata(mediaWithAlbum);
            }
        });
    }

    private Flowable<List<String>> getSubFolders(final String parent) {
        return mAlbumDao.loadAlbumFolderByParent(parent + "/%")
                .map(new Function<List<String>, List<String>>() {
                    @Override
                    public List<String> apply(@io.reactivex.annotations.NonNull List<String> folders) throws Exception {
                        List<String> result = new ArrayList<>();

                        for(String folder : folders) {
                            String relative = getDirectSubFolder(parent, folder);
                            if (!result.contains(relative)) {
                                result.add(relative);
                            }
                        }

                        return result;

                    }
                });
    }

    private String getDirectSubFolder(String parent, String folder) {
        String relative = folder.substring(parent.length() + 1);

        if(relative.indexOf('/') < 0) {
            return folder;
        }

        return folder.substring(0, parent.length() + relative.indexOf('/') + 1);
    }

    private Flowable<List<MediaWithAlbum>> getMusicsByFolder(final String folder) {
        return mMediaDao.loadByFolder(folder);
    }

    public Flowable<List<MediaBrowserCompat.MediaItem>> getChildren(String mediaId) {
        List<MediaBrowserCompat.MediaItem> empty = new ArrayList<>();

        if (!MediaIDHelper.isBrowseable(mediaId)) {
            return Flowable.just(empty);
        }

        if (MEDIA_ID_ROOT.equals(mediaId)) {
            final String folder = PreferencesHelper.getInstance(mContext).getRootFolder();

            if (TextUtils.isEmpty(folder)) {
                return Flowable.just(empty);
            } else {
                return Flowable.combineLatest(
                        getSubFolders(folder),
                        getMusicsByFolder(folder),
                        new BiFunction<List<String>, List<MediaWithAlbum>, List<MediaBrowserCompat.MediaItem>>() {
                            @Override
                            public List<MediaBrowserCompat.MediaItem> apply(
                                    @io.reactivex.annotations.NonNull List<String> strings,
                                    @io.reactivex.annotations.NonNull List<MediaWithAlbum> mediaWithAlbumList) throws Exception {
                                List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();

                                for (String folder : strings) {
                                    result.add(createBrowsableMediaItemForFolder(folder));
                                }

                                for (MediaWithAlbum media : mediaWithAlbumList) {
                                    result.add(createMediaItem(media, folder));
                                }

                                return result;
                            }
                        });
            }

        } else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_FOLDER)) {
            String[] hierarchy = MediaIDHelper.getHierarchy(mediaId);
            final String folder = hierarchy[1];

            return Flowable.combineLatest(
                    getSubFolders(folder),
                    getMusicsByFolder(folder),
                    new BiFunction<List<String>, List<MediaWithAlbum>, List<MediaBrowserCompat.MediaItem>>() {
                        @Override
                        public List<MediaBrowserCompat.MediaItem> apply(
                                @io.reactivex.annotations.NonNull List<String> strings,
                                @io.reactivex.annotations.NonNull List<MediaWithAlbum> mediaWithAlbumList) throws Exception {
                            List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();

                            for (String folder : strings) {
                                result.add(createBrowsableMediaItemForFolder(folder));
                            }

                            for (MediaWithAlbum media : mediaWithAlbumList) {
                                result.add(createMediaItem(media, folder));
                            }

                            return result;
                        }
                    });
        }

        return Flowable.just(empty);
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForFolder(String folder) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(createMediaID(null, MEDIA_ID_MUSICS_BY_FOLDER, folder))
                .setTitle(folder.substring(folder.lastIndexOf('/') + 1))
                .setIconUri(mFolderIconUri)
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createMediaItem(MediaWithAlbum media, String folder) {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)

        //noinspection ResourceType
        String hierarchyAwareMediaID = createMediaID(String.valueOf(media.getUid()), MEDIA_ID_MUSICS_BY_FOLDER, folder);
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_MEDIA_DURATION, media.getDuration() / 1000);

        MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                .setMediaId(hierarchyAwareMediaID)
                .setTitle(media.getTitle())
                .setSubtitle(media.getAlbum())
                .setMediaUri(Uri.parse(media.getData()))
                .setExtras(extras);

        String art = media.getAlbumArt();
        if (!TextUtils.isEmpty(art)) {
            final Uri uri = FileProvider.getUriForFile(mContext,
                    mContext.getPackageName().concat(".provider.album_thumbs"),
                    new File(new File(mContext.getFilesDir(), "albumthumbs"), String.valueOf(media.getAlbumId())));
            for (final String p : mMediaBrowserCallerPackageNames) {
                mContext.grantUriPermission(p, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            builder.setIconUri(uri);
        }

        return new MediaBrowserCompat.MediaItem(builder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    private MediaMetadataCompat buildMediaMetadata(@NonNull MediaWithAlbum media) {

        //noinspection ResourceType
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, String.valueOf(media.getUid()))
                .putString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE, media.getData())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, media.getDuration())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, media.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, media.getAlbum());

        String artwork = media.getAlbumArt();
        if(!TextUtils.isEmpty(artwork)) {
            Uri artUri = AlbumArtsContract.Albums.buildUri(artwork);
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri.toString());

            final Uri uri = FileProvider.getUriForFile(mContext,
                    mContext.getPackageName().concat(".provider.album_thumbs"),
                    new File(new File(mContext.getFilesDir(), "albumthumbs"), String.valueOf(media.getAlbumId())));
            for (final String p : mMediaBrowserCallerPackageNames) {
                mContext.grantUriPermission(p, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, uri.toString());
        } else {
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, mNoAlbumArtIconUri);
        }

        return builder.build();
    }
}
