package net.volcanomobile.vgmplayer.dao;

import android.annotation.SuppressLint;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;
import android.support.annotation.NonNull;

/**
 * Created by Philippe Simons on 5/31/17.
 */

@Database(entities = {Media.class, Album.class, Playlist.class, PlaylistMember.class}, version = 1)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "media_database";

    // Not a leak
    @SuppressLint("StaticFieldLeak")
    private static volatile AppDatabase sInstance;

    @NonNull
    public static AppDatabase getInstance(@NonNull final Context context) {
        if (sInstance == null) {
            synchronized (AppDatabase.class) {
                if (sInstance == null) {
                    sInstance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                            .fallbackToDestructiveMigration().build();
                }
            }
        }
        return sInstance;
    }


    public abstract MediaDao mediaDao();
    public abstract AlbumDao albumDao();
    public abstract PlaylistDao playlistDao();

}
