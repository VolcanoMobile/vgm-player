package net.volcanomobile.vgmplayer.ui.settings;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.text.TextUtils;

import com.afollestad.materialdialogs.folderselector.FolderChooserDialog;

import net.volcanomobile.vgmplayer.Application;
import net.volcanomobile.vgmplayer.BuildConfig;
import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.theme.Theme;
import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

/**
 * Created by philippesimons on 12/08/16.
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    private void showRootFolderSelector() {
        FolderChooserDialog dialog = new FolderChooserDialog.Builder((SettingsActivity) getActivity())
                .initialPath(Environment.getExternalStorageDirectory().getPath())  // changes initial path, defaults to external storage directory
                .build();
        getFragmentManager().beginTransaction().add(dialog, null).commitAllowingStateLoss();
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.preferences);

        Preference rootFolderPreference = findPreference(getString(R.string.pref_root_folder_key));
        String rootFolder = PreferencesHelper.getInstance(getContext()).getRootFolder();
        rootFolderPreference.setSummary(TextUtils.isEmpty(rootFolder) ? getString(R.string.root_not_set) : rootFolder);
        rootFolderPreference.setOnPreferenceClickListener(preference -> {
            showRootFolderSelector();
            return true;
        });

        Preference themePreference = findPreference("theme_id");
        SettingsActivity.bindPreferenceSummaryToValue(themePreference);
        final Preference.OnPreferenceChangeListener themePreferenceBinding = themePreference.getOnPreferenceChangeListener();
        findPreference("theme_id").setOnPreferenceChangeListener((preference, newValue) -> {
            themePreferenceBinding.onPreferenceChange(preference, newValue);

            String value = (String) newValue;
            Theme newTheme = Theme.ofMarshallingId(Integer.parseInt(value));

            if(newTheme != null) {
                Application.getInstance().setCurrentTheme(newTheme);
                return true;
            }

            return false;
        });

        Preference versionPreference = findPreference(getString(R.string.pref_version_key));
        versionPreference.setSummary(BuildConfig.VERSION_NAME);

        Preference developerPreference = findPreference(getString(R.string.pref_developer_key));
        developerPreference.setSummary("Volcano Mobile");
    }
}
