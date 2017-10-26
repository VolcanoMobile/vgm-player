package net.volcanomobile.vgmplayer.effects;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import net.volcanomobile.vgmplayer.effects.nano.EffectsProto;
import net.volcanomobile.vgmplayer.utils.Handlers;
import net.volcanomobile.vgmplayer.utils.ProtoUtils;

import java.util.Observable;
import java.util.UUID;


/**
 * Created by Philippe Simons on 5/11/17.
 */

public final class AudioEffects extends Observable {

    private static final String TAG = "AudioEffects";

    @SuppressLint("InlinedApi")
    private static final UUID EQUALIZER_UUID = AudioEffect.EFFECT_TYPE_EQUALIZER;
    @SuppressLint("InlinedApi")
    private static final UUID BASS_BOOST_UUID = AudioEffect.EFFECT_TYPE_BASS_BOOST;

    @SuppressLint("StaticFieldLeak") // Application Context won't leak
    private static volatile AudioEffects sInstance;

    @NonNull
    public static AudioEffects getInstance(@NonNull final Context context) {
        if (sInstance == null) {
            synchronized (AudioEffects.class) {
                if (sInstance == null) {
                    sInstance = new AudioEffects(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private final Object SETTINGS_LOCK = new Object();

    private static final String FILE_NAME_BASS_BOOST = "bass_boost_settings";
    private static final String FILE_NAME_EQUALIZER = "equalizer_settings";

    @NonNull
    private final Context mContext;

    @NonNull
    private final EffectsProto.BassBoostSettings mBassBoostSettings;

    @NonNull
    private final EffectsProto.EqualizerSettings mEqualizerSettings;

    private BassBoost mBassBoost;
    private Equalizer mEqualizer;

    private int mSessionId;

    private AudioEffects(@NonNull final Context context) {
        mContext = context;
        synchronized (SETTINGS_LOCK) {
            mBassBoostSettings = ProtoUtils.readFromFileNonNull(context, FILE_NAME_BASS_BOOST,
                    new EffectsProto.BassBoostSettings());
            mEqualizerSettings = ProtoUtils.readFromFileNonNull(context, FILE_NAME_EQUALIZER,
                    new EffectsProto.EqualizerSettings());
        }
    }

    public boolean isBassBoostEnabled() {
        synchronized (SETTINGS_LOCK) {
            return mBassBoostSettings.enabled;
        }
    }

    public boolean isEqualizerEnabled() {
        synchronized (SETTINGS_LOCK) {
            return mEqualizerSettings.enabled;
        }
    }

    public int getBassBoostStrength() {
        synchronized (SETTINGS_LOCK) {
            return mBassBoostSettings.strength;
        }
    }

    public int getSessionId() {
        return mSessionId;
    }

    @Nullable
    public Equalizer getEqualizer() {
        return mEqualizer;
    }

    public void create(final int sessionId) {
        if (mSessionId != sessionId) {
            release();
            mSessionId = sessionId;
            final boolean bassBoostEnabled;
            final boolean equalizerEnabled;
            synchronized (SETTINGS_LOCK) {
                bassBoostEnabled = mBassBoostSettings.enabled;
                equalizerEnabled = mEqualizerSettings.enabled;
            }
            if (bassBoostEnabled) {
                restoreBassBoost();
            }
            if (equalizerEnabled) {
                restoreEqualizer();
            }
            setChanged();
            notifyObservers();
        }
    }

    public void release() {
        mSessionId = 0;
        if (mBassBoost != null) {
            mBassBoost.setEnabled(false);
            mBassBoost.release();
            mBassBoost = null;
            setChanged();
        }
        if (mEqualizer != null) {
            mEqualizer.setEnabled(false);
            mEqualizer.release();
            mEqualizer = null;
            setChanged();
        }
        notifyObservers();
    }

    public void setBassBoostEnabled(final boolean enabled) {
        synchronized (SETTINGS_LOCK) {
            if (mBassBoostSettings.enabled != enabled) {
                mBassBoostSettings.enabled = enabled;
                if (enabled) {
                    if (mBassBoost == null && mSessionId != 0) {
                        restoreBassBoost();
                        setChanged();
                    }
                } else {
                    if (mBassBoost != null) {
                        mBassBoost.setEnabled(false);
                        mBassBoost.release();
                        mBassBoost = null;
                        setChanged();
                    }
                }
                persistBassBoostSettingsAsync();
            }
        }
        notifyObservers();
    }

    public void setEqualizerEnabled(final boolean enabled) {
        synchronized (SETTINGS_LOCK) {
            if (mEqualizerSettings.enabled != enabled) {
                mEqualizerSettings.enabled = enabled;
                if (enabled) {
                    if (mEqualizer == null && mSessionId != 0) {
                        restoreEqualizer();
                        setChanged();
                    }
                } else {
                    if (mEqualizer != null) {
                        mEqualizer.setEnabled(false);
                        mEqualizer.release();
                        mEqualizer = null;
                        setChanged();
                    }
                }
                persistEqualizerSettingsAsync();
            }
        }
        notifyObservers();
    }

    private void restoreBassBoost() {
        if (hasBassBoost()) {
            mBassBoost = new BassBoost(Integer.MAX_VALUE, mSessionId);

            synchronized (SETTINGS_LOCK) {
                mBassBoost.setStrength((short) mBassBoostSettings.strength);
            }

            mBassBoost.setEnabled(true);
        }
    }

    private void restoreEqualizer() {
        if (hasEqualizer()) {
            mEqualizer = new Equalizer(Integer.MAX_VALUE, mSessionId);

            synchronized (SETTINGS_LOCK) {
                final EffectsProto.EqualizerSettings proto = mEqualizerSettings;
                if (proto.curPreset != 0
                        || proto.numBands != 0
                        || proto.bandValues.length != 0) {
                    final Equalizer.Settings settings = new Equalizer.Settings();
                    settings.curPreset = (short) proto.curPreset;
                    settings.numBands = (short) proto.numBands;
                    settings.bandLevels = new short[proto.bandValues.length];
                    for (int i = 0; i < settings.bandLevels.length; i++) {
                        settings.bandLevels[i] = (short) proto.bandValues[i];
                    }

                    try {
                        mEqualizer.setProperties(settings);
                    } catch (IllegalArgumentException e) {
                        Log.wtf(TAG, "Failed restoring equalizer settings", e);
                    }
                }
            }

            mEqualizer.setEnabled(true);
        }
    }

    public void setBassBoostStrength(final int strength) {
        synchronized (SETTINGS_LOCK) {
            if (mBassBoostSettings.strength != strength) {
                mBassBoostSettings.strength = strength;
                if (mBassBoost != null) {
                    mBassBoost.setStrength((short) strength);
                }
                persistBassBoostSettingsAsync();
            }
        }
    }

    public void saveEqualizerSettings(@NonNull final Equalizer.Settings settings) {
        synchronized (SETTINGS_LOCK) {
            final EffectsProto.EqualizerSettings proto = mEqualizerSettings;
            proto.curPreset = settings.curPreset;
            proto.numBands = settings.numBands;

            final short[] bandLevels = settings.bandLevels;
            proto.bandValues = new int[bandLevels != null ? bandLevels.length : 0];
            if (bandLevels != null) {
                for (int i = 0; i < bandLevels.length; i++) {
                    proto.bandValues[i] = bandLevels[i];
                }
            }
        }

        persistEqualizerSettingsAsync();
    }

    private void persistBassBoostSettingsAsync() {
        Handlers.runOnIoThread(() -> persistBassBoostSettingsBlocking());
    }

    private void persistBassBoostSettingsBlocking() {
        synchronized (SETTINGS_LOCK) {
            ProtoUtils.writeToFile(mContext, FILE_NAME_BASS_BOOST, mBassBoostSettings);
        }
    }

    private void persistEqualizerSettingsAsync() {
        Handlers.runOnIoThread(() -> persistEqualizerSettingsBlocking());
    }

    private void persistEqualizerSettingsBlocking() {
        synchronized (SETTINGS_LOCK) {
            ProtoUtils.writeToFile(mContext, FILE_NAME_EQUALIZER, mEqualizerSettings);
        }
    }

    /**
     * Checks whether the current device is capable of instantiating and using an
     * {@link android.media.audiofx.Equalizer}
     * @return True if an Equalizer may be used at runtime
     */
    public static boolean hasEqualizer() {
        for (AudioEffect.Descriptor effect : AudioEffect.queryEffects()) {
            if (EQUALIZER_UUID.equals(effect.type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the current device is capable of instantiating and using an
     * {@link android.media.audiofx.Equalizer}
     * @return True if an Equalizer may be used at runtime
     */
    public static boolean hasBassBoost() {
        for (AudioEffect.Descriptor effect : AudioEffect.queryEffects()) {
            if (BASS_BOOST_UUID.equals(effect.type)) {
                return true;
            }
        }
        return false;
    }
}
