/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.volcanomobile.vgmplayer.ui.player;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.service.MusicService;
import net.volcanomobile.vgmplayer.ui.settings.SettingsActivity;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.MediaIDHelper;
import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static net.volcanomobile.vgmplayer.utils.MediaIDHelper.MEDIA_ID_ROOT;

/**
 * A Fragment that lists all the various browsable queues available
 * from a {@link android.service.media.MediaBrowserService}.
 * <p/>
 * It uses a {@link MediaBrowserCompat} to connect to the {@link MusicService}.
 * Once connected, the fragment subscribes to get all the children.
 * All {@link MediaBrowserCompat.MediaItem}'s that can be browsed are shown in a ListView.
 */
public class MediaBrowserFragment extends Fragment {

    private static final String TAG = LogHelper.makeLogTag(MediaBrowserFragment.class);

    private static final String ARG_MEDIA_ID = "media_id";

    private BrowserAdapter mBrowserAdapter;
    private String mMediaId;
    private TextView mFolder;
    private MediaFragmentListener mMediaFragmentListener;
    private Snackbar mErrorMessage;

    private boolean mCallbackRegistered = false;
    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private final MediaControllerCompat.Callback mMediaControllerCallback =
            new MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            if (metadata == null) {
                return;
            }

            String mediaId =  metadata.getDescription().getMediaId();
            LogHelper.d(TAG, "Received metadata change to media ",
                    mediaId);

            mBrowserAdapter.mediaIdChanged(mediaId);
        }

        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            LogHelper.d(TAG, "Received state change: ", state);
            checkForUserVisibleErrors(false);
            mBrowserAdapter.notifyDataSetChanged();
        }
    };

    private final LoaderManager.LoaderCallbacks<List<MediaBrowserCompat.MediaItem>> mMediaItemLoaderCallback = new LoaderManager.LoaderCallbacks<List<MediaBrowserCompat.MediaItem>>() {
        @Override
        public Loader<List<MediaBrowserCompat.MediaItem>> onCreateLoader(int id, Bundle args) {
            return new MediaItemLoader(getContext(), mMediaId);
        }

        @Override
        public void onLoadFinished(Loader<List<MediaBrowserCompat.MediaItem>> loader, List<MediaBrowserCompat.MediaItem> data) {
            try {
                LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=" + mMediaId +
                        "  count=" + data.size());

                checkForUserVisibleErrors(data.isEmpty());
                mBrowserAdapter.setItems(data);

            } catch (Throwable t) {
                LogHelper.e(TAG, "Error on childrenloaded", t);
            }
        }

        @Override
        public void onLoaderReset(Loader<List<MediaBrowserCompat.MediaItem>> loader) {

        }
    };

    public static MediaBrowserFragment newInstance(String mediaId) {
        MediaBrowserFragment fragment = new MediaBrowserFragment();
        Bundle args = new Bundle();
        args.putString(MediaBrowserFragment.ARG_MEDIA_ID, mediaId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // If used on an activity that doesn't implement MediaFragmentListener, it
        // will throw an exception as expected:
        mMediaFragmentListener = (MediaFragmentListener) context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBrowserAdapter = new BrowserAdapter();
        mMediaId = getMediaId();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LogHelper.d(TAG, "fragment.onCreateView");
        View rootView = inflater.inflate(R.layout.fragment_list, container, false);

        RecyclerView recyclerView = (RecyclerView) rootView.findViewById(R.id.list_view);
        initRecyclerView(recyclerView);

        mFolder = (TextView) rootView.findViewById(R.id.folder);

        return rootView;
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

        String folder;
        if (TextUtils.isEmpty(mMediaId) || MEDIA_ID_ROOT.equals(mMediaId)) {
            folder = PreferencesHelper.getInstance(getContext()).getRootFolder();
        } else {
            String[] hierarchy = MediaIDHelper.getHierarchy(mMediaId);
            folder = hierarchy[1];
        }
        mFolder.setVisibility(TextUtils.isEmpty(folder) ? View.GONE : View.VISIBLE);
        mFolder.setText(folder);

        // fetch browsing information to fill the listview:
        MediaBrowserCompat mediaBrowser = mMediaFragmentListener.getMediaBrowser();

        LogHelper.d(TAG, "fragment.onStart, mediaId=", mMediaId,
                "  onConnected=" + mediaBrowser.isConnected());

        if (mediaBrowser.isConnected()) {
            onConnected(MediaControllerCompat.getMediaController(getActivity()));
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        MediaBrowserCompat mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        if (mediaBrowser != null && mediaBrowser.isConnected() && mMediaId != null) {
            mediaBrowser.unsubscribe(mMediaId);
        }
        if (mCallbackRegistered) {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
            LogHelper.d(TAG, "unregisterCallback");
            controller.unregisterCallback(mMediaControllerCallback);
            mCallbackRegistered = false;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mMediaFragmentListener = null;
    }

    public String getMediaId() {
        Bundle args = getArguments();
        if (args != null) {
            return args.getString(ARG_MEDIA_ID);
        }
        return null;
    }

    // Called when the MediaBrowser is connected. This method is either called by the
    // fragment.onStart() or explicitly by the activity in the case where the connection
    // completes after the onStart()
    public void onConnected(@NonNull MediaControllerCompat mediaController) {
        if (isDetached()) {
            return;
        }

        if (mMediaId == null) {
            mMediaId = mMediaFragmentListener.getMediaBrowser().getRoot();
        }

        getLoaderManager().initLoader(0, null, mMediaItemLoaderCallback);

        if (!mCallbackRegistered) {
            LogHelper.d(TAG, "registerCallback");
            mediaController.registerCallback(mMediaControllerCallback);
            mCallbackRegistered = true;
        }
    }

    private void checkForUserVisibleErrors(boolean forceError) {
        boolean showError = forceError;

        if(!isAdded() || !isVisible()) {
            return;
        }

        // if state is ERROR and metadata!=null, use playback state error message:
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(getActivity());
        View view = getView();

        if (controller != null
                && controller.getMetadata() != null
                && controller.getPlaybackState() != null
                && controller.getPlaybackState().getState() == PlaybackStateCompat.STATE_ERROR
                && controller.getPlaybackState().getErrorMessage() != null) {

            if(view != null) {
                mErrorMessage = Snackbar.make(view, controller.getPlaybackState().getErrorMessage(), Snackbar.LENGTH_INDEFINITE);
                mErrorMessage.show();
            }

            showError = true;
        } else if (forceError) {
            if(PreferencesHelper.getInstance(getContext()).getRootFolder() == null) {
                if(view != null) {
                    mErrorMessage = Snackbar.make(view, R.string.error_root_folder_not_set, Snackbar.LENGTH_INDEFINITE);
                    mErrorMessage.setAction(R.string.settings, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Bundle extras = ActivityOptions.makeCustomAnimation(
                                    getContext(), R.anim.fade_in, R.anim.fade_out).toBundle();
                            startActivity(new Intent(getContext(), SettingsActivity.class), extras);
                        }
                    });
                    mErrorMessage.show();
                }
            } else if(mBrowserAdapter.getItemCount() == 0) {
                if(view != null) {
                    mErrorMessage = Snackbar.make(view, R.string.error_no_vgm_files_found, Snackbar.LENGTH_INDEFINITE);
                    mErrorMessage.show();
                }
            } else {
                // Finally, if the caller requested to show error, show a generic message:
                if(view != null) {
                    mErrorMessage = Snackbar.make(view, R.string.error_loading_media, Snackbar.LENGTH_INDEFINITE);
                    mErrorMessage.show();
                }
            }
            showError = true;
        } else {
            if (mErrorMessage != null && mErrorMessage.isShown()) {
                mErrorMessage.dismiss();
            }
        }

        LogHelper.d(TAG, "checkForUserVisibleErrors. forceError=", forceError,
            " showError=", showError);
    }

    interface MediaFragmentListener extends MediaBrowserProvider {
        void onMediaItemSelected(MediaBrowserCompat.MediaItem item);
        void onDeleteMediaItem(MediaBrowserCompat.MediaItem item);
    }

    private class BrowserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int ITEM_VIEW_TYPE = 0;
        private static final int FOLDER_VIEW_TYPE = 1;

        private final LayoutInflater mInflater = LayoutInflater.from(getContext());
        private final List<MediaBrowserCompat.MediaItem> mItems = new ArrayList<>();
        private final HashMap<String, Integer> mMediaIdMap = new HashMap<>();


        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            switch (viewType) {
                case ITEM_VIEW_TYPE:
                    View itemView = mInflater.inflate(R.layout.media_list_item, parent, false);
                    return new MediaItemViewHolder(itemView, getActivity(), mMediaFragmentListener);
                case FOLDER_VIEW_TYPE:
                    View folderView = mInflater.inflate(R.layout.media_list_folder, parent, false);
                    return new MediaFolderViewHolder(folderView, getActivity(), mMediaFragmentListener);
            }

            return null;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

            switch (getItemViewType(position)) {
                case ITEM_VIEW_TYPE:
                    MediaItemViewHolder itemViewHolder = (MediaItemViewHolder) holder;
                    MediaBrowserCompat.MediaItem item = getItem(position);
                    itemViewHolder.bind(item);
                    break;
                case FOLDER_VIEW_TYPE:
                    MediaFolderViewHolder folderViewHolder = (MediaFolderViewHolder) holder;
                    MediaBrowserCompat.MediaItem folder = getItem(position);
                    folderViewHolder.bind(folder);
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            MediaBrowserCompat.MediaItem item = mItems.get(position);
            return item.isBrowsable() ? FOLDER_VIEW_TYPE : ITEM_VIEW_TYPE;
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        MediaBrowserCompat.MediaItem getItem(int position) {
            return mItems.get(position);
        }

        void setItems(@NonNull List<MediaBrowserCompat.MediaItem> items) {
            mItems.clear();
            mItems.addAll(items);
            buildMediaMap();
            notifyDataSetChanged();
        }

        void mediaIdChanged(String mediaId) {
            Integer position = mMediaIdMap.get(mediaId);
            if(position != null) {
                mBrowserAdapter.notifyItemChanged(position);
            }
        }

        private void buildMediaMap() {
            mMediaIdMap.clear();
            for(int i = 0; i < getItemCount(); i++) {
                MediaBrowserCompat.MediaItem item = getItem(i);
                if(item != null && !TextUtils.isEmpty(item.getDescription().getMediaId())) {
                    String tmp = MediaIDHelper.extractMusicIDFromMediaID(item.getDescription().getMediaId());
                    if(!TextUtils.isEmpty(tmp)) {
                        mMediaIdMap.put(tmp, i);
                    }
                }
            }
        }
    }
}
