package net.volcanomobile.vgmplayer.dao;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

import java.util.Date;

/**
 * Created by Philippe Simons on 5/31/17.
 */


@Entity(tableName = "albums",
        indices = {@Index("title"), @Index("date_added")})

public class Album {

    @PrimaryKey(autoGenerate = true)
    private long uid;
    private String title;
    @ColumnInfo(name = "album_art")
    private String albumArt;
    @ColumnInfo(name = "date_added")
    private Date dateAdded = new Date();
    private String folder;

    public long getUid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbumArt() {
        return albumArt;
    }

    public void setAlbumArt(String albumArt) {
        this.albumArt = albumArt;
    }

    public Date getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(Date dateAdded) {
        this.dateAdded = dateAdded;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }
}
