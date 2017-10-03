package net.volcanomobile.vgmplayer.utils;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.volcanomobile.vgmplayer.provider.SharedPreferencesContract;
import net.volcanomobile.vgmplayer.theme.Theme;

/**
 * Created by loki on 8/16/16.
 */
public class PreferencesHelper {

    private static class InstanceHolder {
        @SuppressLint("StaticFieldLeak")
        private static PreferencesHelper instance = null;
    }

    public synchronized static PreferencesHelper getInstance(@NonNull Context context) {
        if(InstanceHolder.instance == null) {
            InstanceHolder.instance = new PreferencesHelper(context.getApplicationContext());
        }
        return InstanceHolder.instance;
    }

    private PreferencesHelper(@NonNull Context context) {
        mContentResolver = context.getContentResolver();
    }

    private static final String THEME_ID_KEY = "theme_id";
    private static final String ROOT_FOLDER_KEY = "root_folder";

    private static final String SHUFFLE_MODE_ENABLED_KEY = "shuffle_mode_enabled";
    private static final String REPEAT_MODE_KEY = "repeat_mode_key";
    private static final String PAUSE_ON_SONG_END_KEY = "pause_on_song_end";

    private static final String LATEST_MEDIA_ID_KEY = "latest_media_id";

    private final ContentResolver mContentResolver;

    public boolean shuffleModeEnabled() {
        return getBoolean(SHUFFLE_MODE_ENABLED_KEY, false);
    }

    public void setShuffleModeEnabled(boolean enabled) {
        setBoolean(SHUFFLE_MODE_ENABLED_KEY, enabled);
    }

    public int getRepeatMode() {
        return getInt(REPEAT_MODE_KEY, 0);
    }

    public void setRepeatMode(int mode) {
        setInt(REPEAT_MODE_KEY, mode);
    }

    public boolean isPauseOnSongEnd() {
        return getBoolean(PAUSE_ON_SONG_END_KEY, false);
    }

    @Nullable
    public String getRootFolder() {
        return getString(ROOT_FOLDER_KEY, null);
    }

    public void setRootFolder(String rootFolder) {
        setString(ROOT_FOLDER_KEY, rootFolder);
    }

    public int getThemeId() {
        return getInt(THEME_ID_KEY, Theme.getFallback().getMarshallingId());
    }

    public void setLatestMediaId(@NonNull String id) {
        setString(LATEST_MEDIA_ID_KEY, id);
    }

    public String getLatestMediaId() {
        return getString(LATEST_MEDIA_ID_KEY, null);
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        boolean result = defaultValue;
        Uri uri = Uri.withAppendedPath(SharedPreferencesContract.Preferences.CONTENT_URI, key);
        Cursor cursor = mContentResolver.query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    result = Boolean.parseBoolean(cursor.getString(0));
                }
            } catch (Exception e) {
            } finally {
                cursor.close();
            }
        }
        return result;
    }

    private int getInt(String key, int defaultValue) {
        int result = defaultValue;
        Uri uri = Uri.withAppendedPath(SharedPreferencesContract.Preferences.CONTENT_URI, key);
        Cursor cursor = mContentResolver.query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    result = cursor.getInt(0);
                }
            } catch (Exception e) {
            } finally {
                cursor.close();
            }
        }
        return result;
    }

    private String getString(String key, String defaultValue) {
        String result = defaultValue;
        Uri uri = Uri.withAppendedPath(SharedPreferencesContract.Preferences.CONTENT_URI, key);
        Cursor cursor = mContentResolver.query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    result = cursor.getString(0);
                }
            } catch (Exception e) {
            } finally {
                cursor.close();
            }
        }
        return result;
    }

    private long getLong(String key, long defaultValue) {
        long result = defaultValue;
        Uri uri = Uri.withAppendedPath(SharedPreferencesContract.Preferences.CONTENT_URI, key);
        Cursor cursor = mContentResolver.query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    result = cursor.getLong(0);
                }
            } catch (Exception ignored) {
            } finally {
                cursor.close();
            }
        }
        return result;
    }

    private void setString(String key, String value) {
        Uri uri = Uri.withAppendedPath(SharedPreferencesContract.Preferences.CONTENT_URI, key);
        ContentValues values = new ContentValues();
        values.put(SharedPreferencesContract.Preferences.VALUE, value);
        try {
            mContentResolver.update(uri, values, null, null);
        } catch (Exception ignored) {
        }
    }

    private void setInt(String key, int value) {
        Uri uri = Uri.withAppendedPath(SharedPreferencesContract.Preferences.CONTENT_URI, key);
        ContentValues values = new ContentValues();
        values.put(SharedPreferencesContract.Preferences.VALUE, value);
        try {
            mContentResolver.update(uri, values, null, null);
        } catch (Exception ignored) {
        }
    }

    private void setLong(String key, long value) {
        Uri uri = Uri.withAppendedPath(SharedPreferencesContract.Preferences.CONTENT_URI, key);
        ContentValues values = new ContentValues();
        values.put(SharedPreferencesContract.Preferences.VALUE, value);
        try {
            mContentResolver.update(uri, values, null, null);
        } catch (Exception ignored) {
        }
    }

    private void setBoolean(String key, boolean value) {
        Uri uri = Uri.withAppendedPath(SharedPreferencesContract.Preferences.CONTENT_URI, key);
        ContentValues values = new ContentValues();
        values.put(SharedPreferencesContract.Preferences.VALUE, value);
        try {
            mContentResolver.update(uri, values, null, null);
        } catch (Exception ignored) {
        }
    }
}
