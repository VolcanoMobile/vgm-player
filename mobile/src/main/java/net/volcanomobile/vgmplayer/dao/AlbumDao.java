package net.volcanomobile.vgmplayer.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

import io.reactivex.Flowable;

/**
 * Created by Philippe Simons on 5/31/17.
 */

@Dao
public interface AlbumDao {

    @Insert
    long insert(Album album);

    @Update
    void update(List<Album> albums);

    @Query("DELETE FROM albums WHERE uid = :id")
    void deleteById(long id);

    @Query("DELETE FROM albums WHERE uid IN (:ids)")
    void deleteByIds(List<Long> ids);

    @Query("DELETE FROM albums")
    void deleteAll();

    @Query("SELECT * FROM albums")
    Flowable<List<Album>> loadAll();

    @Query("SELECT * FROM albums WHERE uid = :id")
    Album loadById(long id);

    @Query("SELECT uid FROM albums WHERE folder = :folder")
    long loadAlbumIdByFolder(String folder);

    @Query("SELECT folder FROM albums WHERE folder LIKE :parent ORDER BY folder")
    Flowable<List<String>> loadAlbumFolderByParent(String parent);

    @Query("SELECT * FROM albums LIMIT :limit")
    Flowable<List<Album>> load(int limit);

    @Query("SELECT * FROM albums WHERE title LIKE :filter ORDER BY title")
    Flowable<List<Album>> load(String filter);

    @Query("SELECT * FROM albums WHERE title LIKE :filter ORDER BY title LIMIT :limit")
    Flowable<List<Album>> load(String filter, int limit);

    @Query("SELECT * FROM albums WHERE uid IN (:ids) ORDER BY :order")
    Flowable<List<Album>> load(String order, long... ids);

    @Query("SELECT * FROM albums WHERE uid IN (:ids) ORDER BY :order LIMIT :limit")
    Flowable<List<Album>> load(String order, int limit, long... ids);

    @Query("SELECT * FROM albums ORDER BY date_added DESC")
    Flowable<List<Album>> loadRecentlyScannedAlbums();

    @Query("SELECT * FROM albums ORDER BY date_added DESC LIMIT :limit")
    Flowable<List<Album>> loadRecentlyScannedAlbums(int limit);
}
