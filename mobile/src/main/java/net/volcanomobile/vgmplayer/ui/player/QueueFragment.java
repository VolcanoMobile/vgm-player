package net.volcanomobile.vgmplayer.ui.player;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.utils.LogHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by loki on 3/13/17.
 */

public class QueueFragment extends Fragment {

    private static final String TAG = LogHelper.makeLogTag(QueueFragment.class);

    private BrowserAdapter mBrowserAdapter;
    private MediaBrowserProvider mMediaBrowserProvider;

    private boolean mCallbackRegistered = false;
    private final MediaControllerCompat.Callback mCallback = new MediaControllerCompat.Callback() {

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            mBrowserAdapter.setItems(queue);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            if (metadata == null) {
                return;
            }

            mBrowserAdapter.notifyDataSetChanged();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            LogHelper.d(TAG, "Received state change: ", state);
            mBrowserAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // If used on an activity that doesn't implement MediaFragmentListener, it
        // will throw an exception as expected:
        mMediaBrowserProvider = (MediaBrowserProvider) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mMediaBrowserProvider = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBrowserAdapter = new BrowserAdapter();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.queue_fragment, container, false);

        RecyclerView recyclerView = (RecyclerView) root.findViewById(R.id.list_view);
        recyclerView.setNestedScrollingEnabled(true);
        initRecyclerView(recyclerView);

        return root;
    }

    private void initRecyclerView(RecyclerView recyclerView) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(mBrowserAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        LogHelper.d(TAG, "fragment.onStart");

        // fetch browsing information to fill the listview:
        MediaBrowserCompat mediaBrowser = mMediaBrowserProvider.getMediaBrowser();

        LogHelper.d(TAG, "fragment.onStart, onConnected=" + mediaBrowser.isConnected());

        if (mediaBrowser.isConnected()) {
            onConnected(MediaControllerCompat.getMediaController(getActivity()));
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        LogHelper.d(TAG, "fragment.onStop");
        if  (mCallbackRegistered) {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
            LogHelper.d(TAG, "unregisterCallback");
            controller.unregisterCallback(mCallback);
            mCallbackRegistered = false;
        }
    }

    public void onConnected(@NonNull MediaControllerCompat mediaController) {
        LogHelper.d(TAG, "onConnected");
        if (isDetached()) {
            return;
        }

        List<MediaSessionCompat.QueueItem> queue = mediaController.getQueue();
        mBrowserAdapter.setItems(queue);
        if (!mCallbackRegistered) {
            LogHelper.d(TAG, "registerCallback");
            mediaController.registerCallback(mCallback);
            mCallbackRegistered = true;
        }
    }

    private class BrowserAdapter extends RecyclerView.Adapter<QueueItemViewHolder> {

        private final LayoutInflater mInflater = LayoutInflater.from(getContext());
        private final List<MediaSessionCompat.QueueItem> mItems = new ArrayList<>();

        @Override
        public QueueItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = mInflater.inflate(R.layout.queue_list_item, parent, false);
            return new QueueItemViewHolder(itemView, getActivity());
        }

        @Override
        public void onBindViewHolder(QueueItemViewHolder holder, int position) {
            holder.bind(mItems.get(position));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        void setItems(List<MediaSessionCompat.QueueItem> items) {
            mItems.clear();
            if (items != null && items.size() >= 1) {
                mItems.addAll(items);
            }
            notifyDataSetChanged();
        }
    }
}
