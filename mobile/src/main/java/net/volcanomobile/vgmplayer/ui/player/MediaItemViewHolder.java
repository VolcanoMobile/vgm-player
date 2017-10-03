package net.volcanomobile.vgmplayer.ui.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.model.MusicProvider;
import net.volcanomobile.vgmplayer.utils.MediaIDHelper;
import net.volcanomobile.vgmplayer.utils.QueueHelper;

import java.io.File;

/**
 * Created by philippesimons on 9/02/17.
 */

class MediaItemViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {

    private static final int STATE_INVALID = -1;
    private static final int STATE_NONE = 0;
    private static final int STATE_PLAYABLE = 1;
    private static final int STATE_PAUSED = 2;
    private static final int STATE_PLAYING = 3;

    private static int sColorStatePlaying = -1;
    private static int sColorStateNotPlaying = -1;

    private final MediaBrowserFragment.MediaFragmentListener mListener;
    private final FragmentActivity mActivity;

    private final View mRootView;
    private final ImageView mImageView;
    private final TextView mTitleView;
    private final TextView mDuration;
    private final ImageButton mMore;

    private MediaBrowserCompat.MediaItem mBoundItem;
    private int cachedState = STATE_INVALID;

    MediaItemViewHolder(View itemView, FragmentActivity activity,
                        MediaBrowserFragment.MediaFragmentListener listener) {
        super(itemView);

        mActivity = activity;
        mListener = listener;

        mRootView = itemView;
        mImageView = (ImageView) itemView.findViewById(R.id.play_eq);
        mTitleView = (TextView) itemView.findViewById(R.id.title);
        mDuration = (TextView) itemView.findViewById(R.id.duration);
        mMore = (ImageButton) itemView.findViewById(R.id.more);

        mMore.setOnClickListener(this);
        mRootView.setOnClickListener(this);
    }

    void bind(MediaBrowserCompat.MediaItem item) {
        mBoundItem = item;

        if (sColorStateNotPlaying == -1 || sColorStatePlaying == -1) {
            initializeColorStateLists(mActivity);
        }

        MediaDescriptionCompat description = item.getDescription();
        mTitleView.setText(description.getTitle());

        if (description.getExtras() != null) {
            mDuration.setVisibility(View.VISIBLE);
            mMore.setVisibility(View.VISIBLE);
            Bundle extras = description.getExtras();
            long duration = extras.getLong(MusicProvider.EXTRA_MEDIA_DURATION);
            if (duration != 0) {
                mDuration.setText(DateUtils.formatElapsedTime(duration));
            } else {
                mDuration.setText(mActivity.getString(R.string.no_duration_time));
            }
        } else {
            mDuration.setVisibility(View.GONE);
        }

        int state = getMediaItemState(mActivity, item);
        if (cachedState != state) {
            Drawable drawable = getDrawableByState(mActivity, state);
            if (drawable != null) {
                mImageView.setImageDrawable(drawable);
                mImageView.setVisibility(View.VISIBLE);
            } else {
                mImageView.setVisibility(View.GONE);
            }

            if (state == STATE_PAUSED || state == STATE_PLAYING) {
                mRootView.setBackgroundResource(R.drawable.selected_media_item);
            } else {
                mRootView.setBackground(null);
            }

            cachedState = state;
        }
    }

    private static void initializeColorStateLists(Context ctx) {
        sColorStateNotPlaying = ResourcesCompat.getColor(ctx.getResources(),
                R.color.media_item_icon_not_playing, null);
        sColorStatePlaying = ResourcesCompat.getColor(ctx.getResources(),
                R.color.media_item_icon_playing, null);
    }

    private static int getMediaItemState(FragmentActivity activity, MediaBrowserCompat.MediaItem mediaItem) {
        int state = STATE_NONE;
        // Set state to playable first, then override to playing or paused state if needed
        if (mediaItem.isPlayable()) {
            state = STATE_PLAYABLE;
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(activity);
            if (MediaIDHelper.isMediaItemPlaying(controller, mediaItem.getDescription())) {
                state = getStateFromController(activity);
            }
        }

        return state;
    }

    private static int getStateFromController(FragmentActivity activity) {
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(activity);
        PlaybackStateCompat pbState = controller.getPlaybackState();
        if (pbState == null ||
                pbState.getState() == PlaybackStateCompat.STATE_ERROR) {
            return STATE_NONE;
        } else if (pbState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            return  STATE_PLAYING;
        } else {
            return STATE_PAUSED;
        }
    }

    private static Drawable getDrawableByState(Context context, int state) {
        switch (state) {
            case STATE_PLAYABLE:
                Drawable pauseDrawable = ContextCompat.getDrawable(context,
                        R.drawable.ic_play_arrow_black_36dp);
                pauseDrawable = DrawableCompat.wrap(pauseDrawable);
                DrawableCompat.setTint(pauseDrawable.mutate(), sColorStateNotPlaying);
                return pauseDrawable;
            case STATE_PLAYING:
                AnimationDrawable animation = (AnimationDrawable)
                        ContextCompat.getDrawable(context, R.drawable.ic_equalizer_white_36dp);
                Drawable drawable = DrawableCompat.wrap(animation);
                DrawableCompat.setTint(drawable.mutate(), sColorStatePlaying);
                animation.start();
                return animation;
            case STATE_PAUSED:
                Drawable playDrawable = ContextCompat.getDrawable(context,
                        R.drawable.ic_equalizer1_white_36dp);
                playDrawable = DrawableCompat.wrap(playDrawable);
                DrawableCompat.setTint(playDrawable.mutate(), sColorStatePlaying);
                return playDrawable;
            case STATE_NONE:
                Drawable folderDrawable = ContextCompat.getDrawable(context,
                        R.drawable.ic_folder_black_36dp);
                folderDrawable = DrawableCompat.wrap(folderDrawable);
                DrawableCompat.setTint(folderDrawable.mutate(), sColorStateNotPlaying);
                return folderDrawable;
            default:
                return null;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.more:
                PopupMenu menu = new PopupMenu(mActivity, mMore);
                menu.inflate(R.menu.media_item);
                if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.KITKAT) {
                    // not possible on KitKat
                    menu.getMenu().removeItem(R.id.delete);
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP
                        && !deleteOptionEnabled()) {
                    // not possible on SD card
                    menu.getMenu().removeItem(R.id.delete);
                }
                menu.setOnMenuItemClickListener(this);
                menu.show();
                break;

            case R.id.root_view:
                if (mBoundItem != null) {
                    mListener.onMediaItemSelected(mBoundItem);
                }
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean deleteOptionEnabled() {
        Uri uri = mBoundItem.getDescription().getMediaUri();
        if (uri != null) {
            File media = new File(uri.getPath());
            try {
                return Environment.isExternalStorageEmulated(media);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(mActivity);

        if (controller != null && mBoundItem != null) {
            switch (item.getItemId()) {
                case R.id.play_next:
                    controller.addQueueItem(mBoundItem.getDescription(), QueueHelper.getPlayingIndex(controller) + 1);
                    return true;
                case R.id.add_to_queue:
                    controller.addQueueItem(mBoundItem.getDescription());
                    return true;
                case R.id.delete:
                    if (mListener != null) {
                        mListener.onDeleteMediaItem(mBoundItem);
                    }
                    return true;
            }
        }

        return false;
    }
}
