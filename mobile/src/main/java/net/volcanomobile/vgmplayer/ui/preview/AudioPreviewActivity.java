package net.volcanomobile.vgmplayer.ui.preview;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.service.player.VgmPlayer;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippesimons on 27/01/17.
 */

public class AudioPreviewActivity extends FragmentActivity {

    private static final String TAG = LogHelper.makeLogTag(AudioPreviewActivity.class);

    private static final long PROGRESS_UPDATE_INTERNAL = 1000;
    private static final long PROGRESS_UPDATE_INITIAL_INTERVAL = 100;

    // we don't have audio focus, and can't duck (play at a low volume)
    private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
    // we have full audio focus
    private static final int AUDIO_FOCUSED = 1;

    private int mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK;
    private AudioManager mAudioManager;

    private VgmPlayer mPlayer;

    private final Handler mHandler = new Handler();
    private final Runnable mUpdateProgressTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();
        }
    };
    private final ScheduledExecutorService mExecutorService =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> mScheduleFuture;

    private ViewSwitcher mViewSwitcher;
    private ImageButton mPlayPause;
    private SeekBar mSeekbar;

    private long mLastTimestamp;
    private long mDuration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        super.onCreate(savedInstanceState);

        Uri data = getIntent().getData();
        if (data != null) {
            LogHelper.d(TAG, "onCreate(): data = ", data.toString());
        } else {
            LogHelper.d(TAG, "onCreate(): data == null");
            finish();
            return;
        }

        setContentView(R.layout.activity_preview);

        TextView filename = (TextView) findViewById(R.id.filename);
        filename.setText(data.getLastPathSegment());
        mDuration = 0;

        mPlayPause = (ImageButton) findViewById(R.id.play_pause);
        mPlayPause.setOnClickListener(v -> {
            if (mPlayer.getPlayWhenReady()) {
                mPlayer.setPlayWhenReady(false);
                stopSeekbarUpdate();
                mPlayPause.setImageDrawable(
                        ContextCompat.getDrawable(AudioPreviewActivity.this, R.drawable.ic_play_arrow_black_36dp));
            } else {
                scheduleSeekbarUpdate();
                mPlayer.setPlayWhenReady(true);
                mPlayPause.setImageDrawable(
                        ContextCompat.getDrawable(AudioPreviewActivity.this, R.drawable.ic_pause_black_36dp));
            }
        });

        mSeekbar = (SeekBar) findViewById(R.id.seekBar);

        mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopSeekbarUpdate();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mPlayer.seekTo(seekBar.getProgress());
            }
        });

        mViewSwitcher = (ViewSwitcher) findViewById(R.id.viewSwitcher);

        // setup player
        mPlayer = new VgmPlayer(this);
        mPlayer.addListener(new VgmPlayer.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                switch (playbackState) {
                    case VgmPlayer.STATE_BUFFERING:
                        tryToGetAudioFocus();
                        if (mAudioFocus == AUDIO_FOCUSED) {
                            mPlayer.setPlayWhenReady(true);
                        } else {
                            Toast.makeText(AudioPreviewActivity.this, "Couldn't gain Audio Focus", Toast.LENGTH_LONG).show();
                        }
                        break;
                    case VgmPlayer.STATE_READY:
                        if (playWhenReady) {
                            mLastTimestamp = SystemClock.elapsedRealtime();
                            scheduleSeekbarUpdate();
                        }

                        mViewSwitcher.setDisplayedChild(1);

                        if (mDuration == 0) {
                            mSeekbar.setVisibility(View.GONE);
                        } else {
                            mSeekbar.setEnabled(true);
                            mSeekbar.setProgress((int) mPlayer.getCurrentPosition());
                            mSeekbar.setMax((int) mDuration);
                        }

                        break;
                    case VgmPlayer.STATE_ENDED:
                        // The media player finished playing the current song.
                        finish();
                        break;
                }
            }

            @Override
            public void onAudioSessionId(int audioSessionId) {

            }

            @Override
            public void onPlayerError(Exception error) {
                Toast.makeText(AudioPreviewActivity.this, "Error", Toast.LENGTH_LONG).show();
                finish();
            }
        });

        mPlayer.prepare(data);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSeekbarUpdate();
        mExecutorService.shutdown();
        if (mPlayer != null) {
            mPlayer.release();
        }
        giveUpAudioFocus();
    }

    private void tryToGetAudioFocus() {
        LogHelper.d(TAG, "tryToGetAudioFocus");
        int result = mAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mAudioFocus = AUDIO_FOCUSED;
        } else {
            mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK;
        }
    }

    private void giveUpAudioFocus() {
        LogHelper.d(TAG, "giveUpAudioFocus");
        if (mAudioManager.abandonAudioFocus(null) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK;
        }
    }

    private void stopSeekbarUpdate() {
        if (mScheduleFuture != null) {
            mScheduleFuture.cancel(false);
        }
    }

    private void scheduleSeekbarUpdate() {
        stopSeekbarUpdate();
        if (!mExecutorService.isShutdown()) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
                            mHandler.post(mUpdateProgressTask);
                        }
                    }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS);
        }
    }

    private void updateProgress() {
        if (mLastTimestamp == 0) {
            return;
        }
        long currentPosition = (SystemClock.elapsedRealtime() - mLastTimestamp) + mSeekbar.getProgress();
        mLastTimestamp = SystemClock.elapsedRealtime();
        mSeekbar.setProgress((int) currentPosition);
    }
}
