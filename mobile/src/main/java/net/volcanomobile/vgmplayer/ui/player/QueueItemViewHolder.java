package net.volcanomobile.vgmplayer.ui.player;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import net.volcanomobile.vgmplayer.Application;
import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.model.MusicProvider;
import net.volcanomobile.vgmplayer.utils.QueueHelper;

/**
 * Created by philippesimons on 9/02/17.
 */

class QueueItemViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {

    private static final int STATE_INVALID = -1;
    private static final int STATE_NONE = 0;
    private static final int STATE_PLAYABLE = 1;
    private static final int STATE_PAUSED = 2;
    private static final int STATE_PLAYING = 3;

    private static int sColorStatePlaying = -1;
    private static int sColorStateNotPlaying = -1;

    private final FragmentActivity mActivity;
    private final RequestManager mGlide;

    private final View mRootView;
    private final ImageView mImageView;
    private final TextView mTitleView;
    private final TextView mSubtitleView;
    private final ImageButton mMore;

    private MediaSessionCompat.QueueItem mBoundItem;
    private int cachedState = STATE_INVALID;

    QueueItemViewHolder(View itemView, FragmentActivity activity) {
        super(itemView);

        mActivity = activity;
        mGlide = Glide.with(mActivity);

        mRootView = itemView;
        mImageView = (ImageView) itemView.findViewById(R.id.play_eq);
        mTitleView = (TextView) itemView.findViewById(R.id.title);
        mSubtitleView = (TextView) itemView.findViewById(R.id.subtitle);
        mMore = (ImageButton) itemView.findViewById(R.id.more);

        mMore.setOnClickListener(this);
        mRootView.setOnClickListener(this);
    }

    void bind(MediaSessionCompat.QueueItem item) {
        mBoundItem = item;

        if (sColorStateNotPlaying == -1 || sColorStatePlaying == -1) {
            initializeColorStateLists(mActivity);
        }

        MediaDescriptionCompat description = item.getDescription();
        mTitleView.setText(description.getTitle());

        long duration = 0;
        if (description.getExtras() != null) {
            Bundle extras = description.getExtras();
            duration = extras.getLong(MusicProvider.EXTRA_MEDIA_DURATION);
        }

        String subtitleString = mActivity.getString(R.string.queue_subtitle_format, description.getSubtitle(),
                duration != 0 ? DateUtils.formatElapsedTime(duration) : mActivity.getString(R.string.no_duration_time));
        mSubtitleView.setText(subtitleString);

        int state = getMediaItemState(mActivity, item);

        if (state == STATE_PLAYABLE) {
            mGlide.load(description.getIconUri())
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .error(R.drawable.ic_albumart_cd_48dp)
                    .into(mImageView);
        }

        if (cachedState != state) {

            if (state != STATE_PLAYABLE) {
                Drawable drawable = getDrawableByState(mActivity, state);
                if (drawable != null) {
                    mImageView.setImageDrawable(drawable);
                    mImageView.setVisibility(View.VISIBLE);
                } else {
                    mImageView.setVisibility(View.GONE);
                    Glide.clear(mImageView);
                }
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

    private static int getMediaItemState(FragmentActivity activity, MediaSessionCompat.QueueItem queueItem) {
        int state = STATE_PLAYABLE;
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(activity);
        if (QueueHelper.isQueueItemPlaying(controller, queueItem)) {
            state = getStateFromController(activity);
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
                menu.inflate(R.menu.queue_item);
                menu.setOnMenuItemClickListener(this);
                menu.show();
                break;

            case R.id.root_view:
                if (mBoundItem != null) {
                    MediaControllerCompat controller = MediaControllerCompat.getMediaController(mActivity);
                    if (controller != null) {
                        MediaControllerCompat.TransportControls transportControls = controller.getTransportControls();
                        transportControls.skipToQueueItem(mBoundItem.getQueueId());
                    }
                }
                break;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(mActivity);

        if (controller != null && mBoundItem != null) {
            switch (item.getItemId()) {
                case R.id.play_next:
                    controller.addQueueItem(mBoundItem.getDescription(), QueueHelper.getPlayingIndex(controller) + 1);
                    if (!QueueHelper.isQueueItemPlaying(controller, mBoundItem)) {
                        controller.removeQueueItem(mBoundItem.getDescription());
                    }
                    return true;
                case R.id.remove:
                    controller.removeQueueItem(mBoundItem.getDescription());
                    Toast.makeText(Application.getInstance(),
                            mActivity.getString(R.string.item_removed, mBoundItem.getDescription().getTitle()),
                            Toast.LENGTH_SHORT).show();
                    return true;
            }
        }

        return false;
    }
}
