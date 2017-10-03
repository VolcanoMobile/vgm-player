package net.volcanomobile.vgmplayer.dao;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

@Entity(tableName = "playlists", indices = {@Index("name")})

public class Playlist {

    @PrimaryKey(autoGenerate = true)
    private long uid;
    private String name;

    public void setUid(long uid) {
        this.uid = uid;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getUid() {
        return uid;
    }

    public String getName() {
        return name;
    }
}
