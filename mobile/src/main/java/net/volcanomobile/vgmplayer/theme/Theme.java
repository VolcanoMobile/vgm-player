package net.volcanomobile.vgmplayer.theme;

import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;

import net.volcanomobile.vgmplayer.R;

/**
 * Created by philippesimons on 2/03/16.
 */
public enum Theme {

    DEFAULT(0, R.style.AppTheme_Light, R.style.AppTheme_Light_OverlapSystemBar),
    DARK(1, R.style.AppTheme_Dark, R.style.AppTheme_Dark_OverlapSystemBar);

    private final int marshallingId;
    private final int resourceId;
    private final int overlapSystemBarId;

    public static Theme getFallback() {
        return DEFAULT;
    }

    @Nullable
    public static Theme ofMarshallingId(int id) {
        for (Theme theme : values()) {
            if (theme.getMarshallingId() == id) {
                return theme;
            }
        }
        return null;
    }

    public int getMarshallingId() {
        return marshallingId;
    }

    @StyleRes
    public int getResourceId() {
        return resourceId;
    }

    @StyleRes
    public int getOverlapSystemBarId() {
        return overlapSystemBarId;
    }

    Theme(int marshallingId, int resourceId, int overlapSystemBarId) {
        this.marshallingId = marshallingId;
        this.resourceId = resourceId;
        this.overlapSystemBarId = overlapSystemBarId;
    }
}
