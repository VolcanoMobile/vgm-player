package net.volcanomobile.vgmplayer.ui;

import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;

import net.volcanomobile.vgmplayer.R;

/**
 * Created by loki on 2/18/16.
 */
public abstract class SingleFragmentActivity<T extends Fragment> extends ActionBarActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(getLayout());
        initializeToolbar();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    protected void addFragment(T fragment) {
        getSupportFragmentManager().beginTransaction().add(getContainerId(), fragment).commit();
    }

    protected abstract T createFragment();

    /** @return The Fragment added to the stack. */
    protected T getFragment() {
        //noinspection unchecked
        return (T) getSupportFragmentManager().findFragmentById(getContainerId());
    }

    /** @return The resource layout to inflate which must contain a {@link android.view.ViewGroup}
     * whose ID is {@link #getContainerId()}. */
    @LayoutRes
    protected int getLayout() {
        return R.layout.activity_single_fragment;
    }

    /** @return The resource identifier for the Fragment container. */
    @IdRes
    protected int getContainerId() {
        return R.id.container;
    }

    protected boolean isFragmentCreated() {
        return getFragment() != null;
    }
}