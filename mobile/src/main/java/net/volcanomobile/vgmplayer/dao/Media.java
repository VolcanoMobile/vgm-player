package net.volcanomobile.vgmplayer.dao;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

import java.util.Date;

import static android.arch.persistence.room.ForeignKey.CASCADE;

/**
 * Created by Philippe Simons on 5/31/17.
 */

@Entity(tableName = "medias",
        indices = {@Index("album_id"), @Index("title"), @Index("album"), @Index("track"), @Index("data"), @Index("date_added")},
        foreignKeys = @ForeignKey(entity = Album.class, parentColumns = "uid", childColumns = "album_id", onDelete = CASCADE))

public class Media {

    @PrimaryKey(autoGenerate = true)
    private long uid;
    private String title;
    private int track;
    @ColumnInfo(name = "album_id")
    private long albumId;
    private long duration;
    private String album;
    @ColumnInfo(name = "date_added")
    private Date dateAdded = new Date();
    private String data;

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

    public int getTrack() {
        return track;
    }

    public void setTrack(int track) {
        this.track = track;
    }

    public long getAlbumId() {
        return albumId;
    }

    public void setAlbumId(long albumId) {
        this.albumId = albumId;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public Date getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(Date dateAdded) {
        this.dateAdded = dateAdded;
    }
}
