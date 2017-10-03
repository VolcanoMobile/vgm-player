package net.volcanomobile.vgmplayer.ui.equalizer;

import android.databinding.ObservableBoolean;
import android.databinding.ObservableInt;

/**
 * Created by Philippe Simons on 5/11/17.
 */

public final class EqualizerFragmentModel {

    private final ObservableBoolean mHasBassBoost = new ObservableBoolean();
    private final ObservableBoolean mBassBoostEnabled = new ObservableBoolean();
    private final ObservableInt mBassBoostStrength = new ObservableInt();

    private final ObservableBoolean mHasEqualizer = new ObservableBoolean();
    private final ObservableBoolean mEqualizerEnabled = new ObservableBoolean();

    public ObservableBoolean hasBassBoost() {
        return mHasBassBoost;
    }

    public void setHasBassBoost(final boolean hasBassBoost) {
        mHasBassBoost.set(hasBassBoost);
    }

    public ObservableBoolean isBassBoostEnabled() {
        return mBassBoostEnabled;
    }

    public void setBassBoostEnabled(final boolean enabled) {
        mBassBoostEnabled.set(enabled);
    }

    public ObservableInt getBassBoostStrength() {
        return mBassBoostStrength;
    }

    public void setBassBoostStrength(final int strength) {
        mBassBoostStrength.set(strength);
    }

    public ObservableBoolean hasEqualizer() {
        return mHasEqualizer;
    }

    public void setHasEqualizer(final boolean hasEqualizer) {
        mHasEqualizer.set(hasEqualizer);
    }

    public ObservableBoolean isEqualizerEnabled() {
        return mEqualizerEnabled;
    }

    public void setEqualizerEnabled(final boolean equalizerEnabled) {
        mEqualizerEnabled.set(equalizerEnabled);
    }

}
