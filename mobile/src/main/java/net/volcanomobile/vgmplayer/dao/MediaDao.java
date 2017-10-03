package net.volcanomobile.vgmplayer.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

import io.reactivex.Flowable;

/**
 * Created by Philippe Simons on 5/31/17.
 */

@Dao
public interface MediaDao {

    @Insert
    long insert(Media media);

    @Insert
    void insert(List<Media> medias);

    @Delete
    void delete(Media media);

    @Delete
    void delete(Media... medias);

    @Query("DELETE FROM medias WHERE uid = :id")
    void deleteById(long id);

    @Query("DELETE FROM medias WHERE uid IN (:ids)")
    void deleteByIds(List<Long> ids);

    // Medias

    @Query("SELECT medias.uid, medias.title, medias.album_id AS albumId, medias.duration, medias.data, medias.album, medias.track, " +
            "albums.album_art AS albumArt FROM medias INNER JOIN albums ON medias.album_id = albums.uid " +
            "ORDER BY medias.album, medias.track, medias.title")
    Flowable<List<MediaWithAlbum>> loadAll();

    @Query("SELECT medias.uid, medias.title, medias.album_id AS albumId, medias.duration, medias.data, medias.album, medias.track, " +
            "albums.album_art AS albumArt FROM medias INNER JOIN albums ON medias.album_id = albums.uid " +
            "WHERE medias.uid = :id")
    MediaWithAlbum loadById(long id);

    @Query("SELECT medias.uid, medias.title, medias.album_id AS albumId, medias.duration, medias.data, medias.album, medias.track, " +
            "albums.album_art AS albumArt FROM medias INNER JOIN albums ON medias.album_id = albums.uid " +
            "WHERE medias.album_id IN (:ids) ORDER BY medias.album, medias.track, medias.title")
    Flowable<List<MediaWithAlbum>> loadByAlbumIds(long... ids);

    @Query("SELECT medias.uid, medias.title, medias.album_id AS albumId, medias.duration, medias.data, medias.album, medias.track, " +
            "albums.album_art AS albumArt FROM medias INNER JOIN albums ON medias.album_id = albums.uid " +
            "WHERE medias.album LIKE :query ORDER BY medias.album, medias.track, medias.title LIMIT 100")
    Flowable<List<MediaWithAlbum>> loadByAlbumSearch(String query);

    @Query("SELECT medias.uid, medias.title, medias.album_id AS albumId, medias.duration, medias.data, medias.album, medias.track, " +
            "albums.album_art AS albumArt FROM medias INNER JOIN albums ON medias.album_id = albums.uid " +
            "ORDER BY RANDOM() LIMIT 100")
    Flowable<List<MediaWithAlbum>> loadRandom();

    @Query("SELECT medias.uid, medias.title, medias.album_id AS albumId, medias.duration, medias.data, medias.album, medias.track, " +
            "albums.album_art AS albumArt FROM medias INNER JOIN albums ON medias.album_id = albums.uid " +
            "ORDER BY medias.date_added DESC LIMIT 100")
    Flowable<List<MediaWithAlbum>> loadRecentlyScanned();

    @Query("SELECT medias.uid, medias.title, medias.album_id AS albumId, medias.duration, medias.data, medias.album, medias.track, " +
            "albums.album_art AS albumArt FROM medias INNER JOIN albums ON medias.album_id = albums.uid " +
            "WHERE medias.title LIKE :query ORDER BY medias.album, medias.track, medias.title LIMIT 100")
    Flowable<List<MediaWithAlbum>> loadByTitleSearch(String query);

    @Query("SELECT medias.uid, medias.title, medias.album_id AS albumId, medias.duration, medias.data, medias.album, medias.track, " +
            "albums.album_art AS albumArt FROM medias INNER JOIN albums ON medias.album_id = albums.uid " +
            "WHERE albums.folder = :folder ORDER BY medias.title")
    Flowable<List<MediaWithAlbum>> loadByFolder(String folder);
}
