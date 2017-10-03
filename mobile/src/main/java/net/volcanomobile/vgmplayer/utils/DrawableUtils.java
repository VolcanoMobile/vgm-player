package net.volcanomobile.vgmplayer.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;

/**
 * Created by Philippe Simons on 8/3/17.
 */

public class DrawableUtils {
    @NonNull
    public static Uri getUriToDrawable(@NonNull Context context,
                                       @DrawableRes int drawableId) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + context.getPackageName() + "/drawable"
                + '/' + context.getResources().getResourceEntryName(drawableId) );
    }
}
