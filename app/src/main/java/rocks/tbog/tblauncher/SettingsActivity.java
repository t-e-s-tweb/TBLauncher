package rocks.tbog.tblauncher;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import rocks.tbog.tblauncher.preference.CustomDialogPreference;
import rocks.tbog.tblauncher.preference.PaletteDialog;
import rocks.tbog.tblauncher.preference.SliderDialog;
import rocks.tbog.tblauncher.utils.SystemUiVisibility;
import rocks.tbog.tblauncher.utils.UIColors;

public class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback/*, PreferenceFragmentCompat.OnPreferenceStartFragmentCallback*/ {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            // Create the fragment only when the activity is created for the first time.
            // ie. not after orientation changes
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(SettingsFragment.FRAGMENT_TAG);
            if (fragment == null) {
                fragment = new SettingsFragment();
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, fragment, SettingsFragment.FRAGMENT_TAG)
                    .commit();
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                setTitle(R.string.activity_settings);
            }
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().popBackStackImmediate()) {
            return true;
        }
        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen preferenceScreen) {
        String key = preferenceScreen.getKey();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, key);
        fragment.setArguments(args);
        ft.replace(R.id.settings_container, fragment, key);
        ft.addToBackStack(key);
        ft.commit();
        return true;
    }

//    @Override
//    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
//        // Instantiate the new Fragment
//        final Bundle args = pref.getExtras();
//        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
//                getClassLoader(),
//                pref.getFragment());
//        fragment.setArguments(args);
//        fragment.setTargetFragment(caller, 0);
//        // Replace the existing Fragment with the new Fragment
//        getSupportFragmentManager().beginTransaction()
//                .replace(R.id.settings_container, fragment)
//                .addToBackStack(null)
//                .commit();
//        return true;
//    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        static final String FRAGMENT_TAG = SettingsFragment.class.getName();
        private static final String DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG";

        public SettingsFragment() {
            super();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                removePreference("black-notification-icons");
            }

        }

        private void removePreference(String key) {
            Preference pref = findPreference(key);
            if (pref != null && pref.getParent() != null)
                pref.getParent().removePreference(pref);
        }

        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);

            int color = UIColors.getColor(sharedPreferences, "notification-bar-color");
            int alpha = UIColors.getAlpha(sharedPreferences, "notification-bar-alpha");
            UIColors.setStatusBarColor((SettingsActivity) getActivity(), UIColors.setAlpha(color, alpha));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View view = getView();
                if (view != null) {
                    if (sharedPreferences.getBoolean("black-notification-icons", false)) {
                        SystemUiVisibility.setLightStatusBar(view);
                    } else {
                        SystemUiVisibility.clearLightStatusBar(view);
                    }
                }
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            // Try if the preference is one of our custom Preferences
            DialogFragment dialogFragment = null;
            if (preference instanceof CustomDialogPreference) {
                // Create a new instance of CustomDialog with the key of the related Preference
                String key = preference.getKey();
                switch (key) {
                    case "notification-bar-color":
                    case "search-bar-color":
                        dialogFragment = PaletteDialog.newInstance(key);
                        break;
                    case "notification-bar-alpha":
                    case "search-bar-alpha":
                        dialogFragment = SliderDialog.newInstance(key);
                        break;
                    default:
                        throw new RuntimeException("CustomDialogPreference \"" + key + "\" has no dialog defined");
                }
            }

            // If it was one of our custom Preferences, show its dialog
            if (dialogFragment != null) {
                dialogFragment.setTargetFragment(this, 0);
                final FragmentManager fm = this.getFragmentManager();
                assert fm != null;
                dialogFragment.show(fm, DIALOG_FRAGMENT_TAG);
            }
            // Could not be handled here. Try with the super method.
            else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case "notification-bar-color":
                case "notification-bar-alpha": {
                    int color = UIColors.getColor(sharedPreferences, "notification-bar-color");
                    int alpha = UIColors.getAlpha(sharedPreferences, "notification-bar-alpha");
                    UIColors.setStatusBarColor((SettingsActivity) getActivity(), UIColors.setAlpha(color, alpha));
                    break;
                }
                case "black-notification-icons":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        View view = getView();
                        if (view == null)
                            break;
                        if (sharedPreferences.getBoolean("black-notification-icons", false)) {
                            SystemUiVisibility.setLightStatusBar(view);
                        } else {
                            SystemUiVisibility.clearLightStatusBar(view);
                        }
                    }
                    break;
            }
        }
    }
}