package net.volcanomobile.vgmplayer.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.widget.Toast;

import com.afollestad.materialdialogs.folderselector.FolderChooserDialog;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.service.MusicService;
import net.volcanomobile.vgmplayer.ui.SingleFragmentActivity;
import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

import java.io.File;

/**
 * Created by philippesimons on 12/08/16.
 */
public class SettingsActivity extends SingleFragmentActivity<SettingsFragment>
        implements FolderChooserDialog.FolderCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isFragmentCreated()) {
            addFragment(createFragment());
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected SettingsFragment createFragment() {
        return SettingsFragment.newInstance();
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    public static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        preference.callChangeListener(PreferenceManager
                .getDefaultSharedPreferences(preference.getContext())
                .getString(preference.getKey(), ""));
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    public void onFolderSelection(@NonNull FolderChooserDialog dialog, @NonNull File folder) {
        String oldRoot = PreferencesHelper.getInstance(this).getRootFolder();
        String newRoot = folder.getAbsolutePath();


        if(!newRoot.equals(oldRoot)) {
            PreferencesHelper.getInstance(this).setRootFolder(newRoot);
            MusicService.rootChanged(SettingsActivity.this);
        }
    }

    @Override
    public void onFolderChooserDismissed(@NonNull FolderChooserDialog dialog) {

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(getString(R.string.pref_root_folder_key))) {
            String folder = PreferencesHelper.getInstance(this).getRootFolder();
            SettingsFragment settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentById(R.id.container);
            if(settingsFragment != null) {
                Preference preference = settingsFragment.findPreference(getString(R.string.pref_root_folder_key));
                preference.setSummary(folder);
            }
        }
    }
}
