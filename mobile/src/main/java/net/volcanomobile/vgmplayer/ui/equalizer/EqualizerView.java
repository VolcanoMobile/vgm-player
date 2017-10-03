package net.volcanomobile.vgmplayer.ui.equalizer;

import android.content.Context;
import android.content.res.Resources;
import android.media.audiofx.Equalizer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.effects.AudioEffects;
import net.volcanomobile.vgmplayer.widget.BaseRecyclerAdapter;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by Philippe Simons on 5/11/17.
 */

public class EqualizerView extends RecyclerView {

    public interface OnBandChangeListener {
        void onBandChange(short band, short value);
    }

    private AudioEffects mAudioEffects;

    private Equalizer mEqualizer;
    private AdapterImpl mAdapter;

    private int mEqLevelRange;
    private int mMinEqLevel;

    private OnBandChangeListener mOnBandChangeListener;

    public EqualizerView(final Context context) {
        super(context);
        init(context);
    }

    public EqualizerView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EqualizerView(final Context context, final AttributeSet attrs,
                         final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull final Context context) {
        mAudioEffects = AudioEffects.getInstance(context);
        setLayoutManager(new LinearLayoutManager(context));
    }

    public void setOnBandChangeListener(@Nullable final OnBandChangeListener onBandChangeListener) {
        mOnBandChangeListener = onBandChangeListener;
    }

    public void setEqualizer(@Nullable final Equalizer equalizer) {
        if (mEqualizer != equalizer) {
            mEqualizer = equalizer;
            rebuild();
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        if (mAdapter != null) {
            mAdapter.setEnabled(enabled);
            mAdapter.notifyDataSetChanged();
        }
    }

    public void rebuild() {
        if (mEqualizer == null) {
            if (mAdapter != null) {
                mAdapter.clear();
            }
        } else {
            final List<Item> items = buildItems(mEqualizer);
            if (mAdapter == null) {
                mAdapter = new AdapterImpl(getContext(), items);
                mAdapter.setMinEqLevel(mMinEqLevel);
                mAdapter.setEqLevelRange(mEqLevelRange);
                mAdapter.setOnBandChangeListener(mInternalBandChangeListener);
                mAdapter.setEnabled(isEnabled());
                setAdapter(mAdapter);
            } else {
                mAdapter.setMinEqLevel(mMinEqLevel);
                mAdapter.setEqLevelRange(mEqLevelRange);
                mAdapter.setItems(items);
            }
        }
    }

    private List<Item> buildItems(@NonNull final Equalizer equalizer) {
        final List<Item> items = new ArrayList<>(equalizer.getNumberOfBands());

        final short[] bandLevelRange = equalizer.getBandLevelRange();
        final short minEQLevel = bandLevelRange[0];
        final short maxEQLevel = bandLevelRange[1];

        mEqLevelRange = maxEQLevel - minEQLevel;
        mMinEqLevel = minEQLevel;
        final Resources res = getResources();
        for (short i = 0; i < equalizer.getNumberOfBands(); i++) {
            final Item item = new Item();
            final int herz = equalizer.getCenterFreq(i) / 1000;
            final String herzText;
            if (herz > 1000) {
                final NumberFormat formatter = NumberFormat.getInstance(Locale.US);
                formatter.setMaximumFractionDigits(1);
                formatter.setMinimumFractionDigits(0);
                formatter.setRoundingMode(RoundingMode.HALF_UP);
                herzText = res.getString(R.string.s_kHz, formatter.format((float) herz / 1000f));
            } else {
                herzText = res.getString(R.string.d_Hz, herz);
            }
            item.title = herzText;
            item.seekBarValue = equalizer.getBandLevel(i) - minEQLevel;
            items.add(item);
        }
        return items;
    }

    private final OnBandChangeListener mInternalBandChangeListener = new OnBandChangeListener() {
        @Override
        public void onBandChange(short band, short value) {
            mEqualizer.setBandLevel(band, value);
            mAudioEffects.saveEqualizerSettings(mEqualizer.getProperties());
            if (mOnBandChangeListener != null) {
                mOnBandChangeListener.onBandChange(band, value);
            }
        }
    };

    private static final class Item {

        int seekBarValue;
        String title;

    }

    static final class VH extends ViewHolder {

        TextView bandName;
        SeekBar bandValue;

        VH(@NonNull final View itemView) {
            super(itemView);
            bandName = (TextView) itemView.findViewById(R.id.bandText);
            bandValue = (SeekBar) itemView.findViewById(R.id.bandSeekBar);
        }
    }

    private static final class AdapterImpl extends BaseRecyclerAdapter<Item, VH> {

        private int mEqLevelRange;
        private int mMinEqLevel;
        private boolean mEnabled;

        private OnBandChangeListener mOnBandChangeListener;

        AdapterImpl(@NonNull final Context context,
                    @Nullable final List<Item> items) {
            super(context, items);
        }

        void setEnabled(final boolean enabled) {
            mEnabled = enabled;
        }

        void setEqLevelRange(final int range) {
            mEqLevelRange = range;
        }

        void setMinEqLevel(final int minEqLevel) {
            mMinEqLevel = minEqLevel;
        }

        void setOnBandChangeListener(@Nullable final OnBandChangeListener onBandChangeListener) {
            mOnBandChangeListener = onBandChangeListener;
        }

        @Override
        public VH onCreateViewHolder(final ViewGroup parent, final int viewType) {
            final VH vh = new VH(getLayoutInflater().inflate(
                    R.layout.equalizer_band, parent, false));
            vh.bandValue.setMax(mEqLevelRange);
            vh.bandValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mOnBandChangeListener != null) {
                        final int position = vh.getAdapterPosition();
                        final Item item = getItem(position);
                        item.seekBarValue = progress;
                        mOnBandChangeListener.onBandChange((short) position,
                                (short) (progress + mMinEqLevel));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(final VH holder, final int position) {
            final Item item = getItem(position);
            holder.bandName.setText(item.title);
            holder.bandValue.setProgress(item.seekBarValue);

            holder.bandName.setEnabled(mEnabled);
            holder.bandValue.setEnabled(mEnabled);
        }
    }
}
