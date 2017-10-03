package net.volcanomobile.vgmplayer.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceManager;

import java.util.Map;

/**
 * Created by loki on 2/3/17.
 */

public class SharedPreferencesContentProvider extends ContentProvider {

    private static final int PREFERENCES = 1;
    private static final int PREFERENCE_ITEM = 2;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(SharedPreferencesContract.AUTHORITY, "preferences", PREFERENCES);
        sUriMatcher.addURI(SharedPreferencesContract.AUTHORITY, "preferences/*", PREFERENCE_ITEM);
    }

    private SharedPreferences mPreferences;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) {
            mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        if (sUriMatcher.match(uri) != PREFERENCE_ITEM) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        String[] columns = new String[] { SharedPreferencesContract.Preferences.VALUE };

        MatrixCursor matrixCursor = new MatrixCursor(columns, 1);

        String key = uri.getLastPathSegment();
        Map map = mPreferences.getAll();

        if (map.containsKey(key)) {
            Object value = map.get(key);
            matrixCursor.addRow(new Object[] {value});
        }

        return matrixCursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {

        switch (sUriMatcher.match(uri)) {

            case PREFERENCES:
                return SharedPreferencesContract.Preferences.CONTENT_TYPE;

            case PREFERENCE_ITEM:
                return SharedPreferencesContract.Preferences.CONTENT_ITEM_TYPE;

            // If the URI pattern doesn't match any permitted patterns, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {

        if (sUriMatcher.match(uri) != PREFERENCE_ITEM) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        String key = uri.getLastPathSegment();

        if(mPreferences.contains(key)) {
            mPreferences.edit().remove(key).apply();
            return 1;
        }

        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        if (sUriMatcher.match(uri) != PREFERENCE_ITEM) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (!values.containsKey(SharedPreferencesContract.Preferences.VALUE)) {
            throw new SQLException("Failed to update row into " + uri);
        }

        String key = uri.getLastPathSegment();
        String value = values.getAsString(SharedPreferencesContract.Preferences.VALUE);

        mPreferences.edit().putString(key, value).apply();

        return 1;
    }
}
