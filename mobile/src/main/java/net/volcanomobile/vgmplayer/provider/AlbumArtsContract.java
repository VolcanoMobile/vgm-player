package net.volcanomobile.vgmplayer.provider;

import android.net.Uri;

import net.volcanomobile.vgmplayer.BuildConfig;

/**
 * Created by philippesimons on 16/01/17.
 */

public final class AlbumArtsContract {

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID +
            ".albumarts";

    public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY);

    public static final class Albums {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AlbumArtsContract.CONTENT_URI, "albums");

        public static Uri buildUri(String fileName) {
            return Uri.withAppendedPath(CONTENT_URI, fileName);
        }
    }
}
