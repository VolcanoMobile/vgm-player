package net.volcanomobile.vgmplayer.ui.player;

import android.support.v4.app.FragmentActivity;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import net.volcanomobile.vgmplayer.R;

/**
 * Created by philippesimons on 9/02/17.
 */

class MediaFolderViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener {

    private final MediaBrowserFragment.MediaFragmentListener mListener;
    private final FragmentActivity mActivity;

    private final View mRootView;
    private final TextView mTitleView;

    private MediaBrowserCompat.MediaItem mBoundItem;

    MediaFolderViewHolder(View itemView, FragmentActivity activity,
                          MediaBrowserFragment.MediaFragmentListener listener) {
        super(itemView);

        mActivity = activity;
        mListener = listener;

        mRootView = itemView;
        mTitleView = (TextView) itemView.findViewById(R.id.title);

        mRootView.setOnClickListener(this);
    }

    void bind(MediaBrowserCompat.MediaItem item) {
        mBoundItem = item;

        MediaDescriptionCompat description = item.getDescription();
        mTitleView.setText(description.getTitle());
    }

    @Override
    public void onClick(View v) {
        if (mBoundItem != null) {
            mListener.onMediaItemSelected(mBoundItem);
        }
    }
}
