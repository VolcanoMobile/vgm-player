package net.volcanomobile.vgmplayer.ui.player;

import android.content.ComponentName;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.content.Loader;
import android.support.v4.media.MediaBrowserCompat;

import net.volcanomobile.vgmplayer.service.MusicService;

import java.util.List;

/**
 * Created by philippesimons on 16/01/17.
 */

class MediaItemLoader extends Loader<List<MediaBrowserCompat.MediaItem>> {

    private final String mParentId;
    private MediaBrowserCompat mMediaBrowser;
    private List<MediaBrowserCompat.MediaItem> mLastChildren;

    private final MediaBrowserCompat.ConnectionCallback mConnectionCallback = new MediaBrowserCompat.ConnectionCallback() {

        @Override
        public void onConnected() {
            mMediaBrowser.subscribe(getParentId(), mSubscribtionCallback);
        }
    };

    private final MediaBrowserCompat.SubscriptionCallback mSubscribtionCallback = new MediaBrowserCompat.SubscriptionCallback() {
        @Override
        public void onChildrenLoaded(@NonNull String parentId, List<MediaBrowserCompat.MediaItem> children) {
            mLastChildren = children;
            // Deliver the children
            deliverResult(children);
        }
    };

    /**
     * Stores away the application context associated with context.
     * Since Loaders can be used across multiple activities it's dangerous to
     * store the context directly; always use {@link #getContext()} to retrieve
     * the Loader's Context, don't use the constructor argument directly.
     * The Context returned by {@link #getContext} is safe to use across
     * Activity instances.
     *
     * @param context used to retrieve the application context.
     */
    MediaItemLoader(Context context, String parentId) {
        super(context);
        mParentId = parentId;
    }

    private String getParentId() {
        return mParentId != null ? mParentId : mMediaBrowser.getRoot();
    }

    @Override
    protected void onStartLoading() {
        if (mLastChildren != null) {
            deliverResult(mLastChildren);
        }

        if (mMediaBrowser == null) {
            mMediaBrowser =
                    new MediaBrowserCompat(getContext(),
                            new ComponentName(getContext(),
                                    MusicService.class),
                            mConnectionCallback, null);
            mMediaBrowser.connect();
        } else if (mMediaBrowser.isConnected()) {
            // Subscribe to retrieve the list of children
            mMediaBrowser.subscribe(getParentId(), mSubscribtionCallback);
        }
    }

    @Override
    protected void onForceLoad() {
        // Resend the last children if we have one
        if (mLastChildren != null) {
            deliverResult(mLastChildren);
        }

        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
            mMediaBrowser.unsubscribe(getParentId());
            mMediaBrowser.subscribe(getParentId(), mSubscribtionCallback);
        }
    }

    @Override
    protected void onReset() {
        mLastChildren = null;

        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
            mMediaBrowser.unsubscribe(getParentId(), mSubscribtionCallback);
            mMediaBrowser.disconnect();
            mMediaBrowser = null;
        }
    }
}
