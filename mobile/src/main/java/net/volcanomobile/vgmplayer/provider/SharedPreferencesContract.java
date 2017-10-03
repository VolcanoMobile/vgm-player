package net.volcanomobile.vgmplayer.provider;

import android.net.Uri;
import android.provider.BaseColumns;

import net.volcanomobile.vgmplayer.BuildConfig;

/**
 * Created by loki on 2/3/17.
 */

public final class SharedPreferencesContract {

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID +
            ".shared_preferences";

    public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY);

    public static final class Preferences implements BaseColumns {

        public static final Uri CONTENT_URI = Uri.withAppendedPath(SharedPreferencesContract.CONTENT_URI, "preferences");

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.net.volcanomobile.preference";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.net.volcanomobile.preference";

        public static final String VALUE = "value";
    }
}
