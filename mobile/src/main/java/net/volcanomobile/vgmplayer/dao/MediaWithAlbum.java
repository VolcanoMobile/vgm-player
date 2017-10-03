package net.volcanomobile.vgmplayer.dao;

/**
 * Created by Philippe Simons on 5/31/17.
 */

public class MediaWithAlbum {

    private long uid;
    private String title;
    private int track;
    private long albumId;
    private long duration;
    private String data;
    private String album;
    private String albumArt;

    public long getUid() {
        return uid;
    }

    void setUid(long uid) {
        this.uid = uid;
    }

    public String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    public int getTrack() {
        return track;
    }

    void setTrack(int track) {
        this.track = track;
    }

    public long getAlbumId() {
        return albumId;
    }

    void setAlbumId(long albumId) {
        this.albumId = albumId;
    }

    public long getDuration() {
        return duration;
    }

    void setDuration(long duration) {
        this.duration = duration;
    }

    public String getData() {
        return data;
    }

    void setData(String data) {
        this.data = data;
    }

    public String getAlbum() {
        return album;
    }

    void setAlbum(String album) {
        this.album = album;
    }

    public String getAlbumArt() {
        return albumArt;
    }

    void setAlbumArt(String albumArt) {
        this.albumArt = albumArt;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final MediaWithAlbum media = (MediaWithAlbum) o;

        return uid == media.uid;

    }

    @Override
    public int hashCode() {
        return (int) (uid ^ (uid >>> 32));
    }
}
