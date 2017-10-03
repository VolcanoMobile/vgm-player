package net.volcanomobile.vgmplayer.ui.equalizer;

import android.os.Bundle;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.ui.SingleFragmentActivity;

/**
 * Created by Philippe Simons on 5/11/17.
 */

public class EqualizerActivity extends SingleFragmentActivity<EqualizerFragment> {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isFragmentCreated()) {
            addFragment(createFragment());
        }
    }

    @Override
    protected EqualizerFragment createFragment() {
        return EqualizerFragment.newInstance();
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
