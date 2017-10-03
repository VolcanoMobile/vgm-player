package net.volcanomobile.vgmplayer.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.volcanomobile.vgmplayer.provider.AlbumArtsContract.Albums;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Created by philippesimons on 16/01/17.
 */

public class AlbumContentProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("No external query");
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "application/octet-stream";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external delete");
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external updates");
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        String uriString = uri.toString();
        if(uriString.length() <= Albums.CONTENT_URI.toString().length() + 1) {
            throw new FileNotFoundException();
        }
        String path = uri.toString().substring(Albums.CONTENT_URI.toString().length() + 1);
        return ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
