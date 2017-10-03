package net.volcanomobile.vgmplayer.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

import io.reactivex.Flowable;

/**
 * Created by Philippe Simons on 6/1/17.
 */

@Dao
public interface PlaylistDao {

    @Insert
    long insert(Playlist playlist);

    @Delete
    void delete(Playlist playlist);

    @Query("DELETE FROM playlists")
    void deleteAll();

    @Query("DELETE FROM playlists WHERE uid = :id")
    void deleteById(long id);

    @Query("SELECT * FROM playlists ORDER BY name")
    Flowable<List<Playlist>> loadAll();

    @Query("SELECT * FROM playlists WHERE name LIKE :filter ORDER BY name")
    Flowable<List<Playlist>> load(String filter);

    @Query("SELECT media_id FROM playlists_members WHERE playlist_id = :id")
    Flowable<long[]> loadMediaIds(long id);
}
