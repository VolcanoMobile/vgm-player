package net.volcanomobile.vgmplayer.dao;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;

import static android.arch.persistence.room.ForeignKey.CASCADE;

@Entity(tableName = "playlists_members",
        primaryKeys = {"playlist_id", "media_id"},
        indices = {@Index("playlist_id"), @Index("media_id")},
        foreignKeys = {@ForeignKey(entity = Media.class, parentColumns = "uid", childColumns = "media_id", onDelete = CASCADE),
                @ForeignKey(entity = Playlist.class, parentColumns = "uid", childColumns = "playlist_id", onDelete = CASCADE)}
)

public class PlaylistMember {

    @ColumnInfo(name = "playlist_id")
    private long playlistId;
    @ColumnInfo(name = "media_id")
    private long mediaId;

    public long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(long playlistId) {
        this.playlistId = playlistId;
    }

    public long getMediaId() {
        return mediaId;
    }

    public void setMediaId(long mediaId) {
        this.mediaId = mediaId;
    }
}
