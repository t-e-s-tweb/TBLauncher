package rocks.tbog.tblauncher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.collection.ArraySet;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rocks.tbog.tblauncher.dataprovider.ShortcutsProvider;
import rocks.tbog.tblauncher.dataprovider.TagsProvider;
import rocks.tbog.tblauncher.db.ExportedData;
import rocks.tbog.tblauncher.db.XmlImport;
import rocks.tbog.tblauncher.drawable.SizeWrappedDrawable;
import rocks.tbog.tblauncher.entry.AppEntry;
import rocks.tbog.tblauncher.entry.EntryItem;
import rocks.tbog.tblauncher.entry.StaticEntry;
import rocks.tbog.tblauncher.entry.TagEntry;
import rocks.tbog.tblauncher.handler.IconsHandler;
import rocks.tbog.tblauncher.preference.BaseListPreferenceDialog;
import rocks.tbog.tblauncher.preference.BaseMultiSelectListPreferenceDialog;
import rocks.tbog.tblauncher.preference.ConfirmDialog;
import rocks.tbog.tblauncher.preference.ContentLoadHelper;
import rocks.tbog.tblauncher.preference.CustomDialogPreference;
import rocks.tbog.tblauncher.preference.EditSearchEnginesPreferenceDialog;
import rocks.tbog.tblauncher.preference.EditSearchHintPreferenceDialog;
import rocks.tbog.tblauncher.preference.IconListPreferenceDialog;
import rocks.tbog.tblauncher.preference.OrderListPreferenceDialog;
import rocks.tbog.tblauncher.preference.PreferenceColorDialog;
import rocks.tbog.tblauncher.preference.QuickListPreferenceDialog;
import rocks.tbog.tblauncher.preference.SliderDialog;
import rocks.tbog.tblauncher.preference.TagOrderListPreferenceDialog;
import rocks.tbog.tblauncher.ui.dialog.PleaseWaitDialog;
import rocks.tbog.tblauncher.utils.FileUtils;
import rocks.tbog.tblauncher.utils.MimeTypeUtils;
import rocks.tbog.tblauncher.utils.PrefCache;
import rocks.tbog.tblauncher.utils.PrefOrderedListHelper;
import rocks.tbog.tblauncher.utils.SystemUiVisibility;
import rocks.tbog.tblauncher.utils.UIColors;
import rocks.tbog.tblauncher.utils.UISizes;
import rocks.tbog.tblauncher.utils.UITheme;
import rocks.tbog.tblauncher.utils.Utilities;

public class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback/*, PreferenceFragmentCompat.OnPreferenceStartFragmentCallback*/ {

    private final static String INTENT_EXTRA_BACK_STACK_TAGS = "backStackTagList";

    private final static ArraySet<String> PREF_THAT_REQUIRE_LAYOUT_UPDATE = new ArraySet<>(Arrays.asList(
        "result-list-argb", "result-ripple-color", "result-list-radius", "result-list-row-height",
        "notification-bar-argb", "notification-bar-gradient", "black-notification-icons",
        "navigation-bar-argb",
        "search-bar-height", "search-bar-text-size", "search-bar-radius", "search-bar-gradient", "search-bar-at-bottom",
        "search-bar-argb", "search-bar-text-color", "search-bar-icon-color",
        "search-bar-ripple-color", "search-bar-cursor-argb", "enable-suggestions-keyboard",
        "search-bar-layout", "quick-list-position"
    ));
    private final static ArraySet<String> PREF_LISTS_WITH_DEPENDENCY = new ArraySet<>(Arrays.asList(
        "gesture-click",
        "gesture-double-click",
        "gesture-fling-down-left",
        "gesture-fling-down-right",
        "gesture-fling-up",
        "gesture-fling-left",
        "gesture-fling-right",
        "button-launcher",
        "button-home",
        "dm-empty-back",
        "dm-search-back",
        "dm-widget-back",
        "dm-search-open-result"
    ));

    private static final int FILE_SELECT_XML_SET = 63;
    private static final int FILE_SELECT_XML_OVERWRITE = 62;
    private static final int FILE_SELECT_XML_APPEND = 61;
    public static final int ENABLE_DEVICE_ADMIN = 60;
    private static final String TAG = "SettAct";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        int theme = UITheme.getSettingsTheme(this);
        if (theme != UITheme.ID_NULL)
            setTheme(theme);
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

            restoreBackStack();
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void restoreBackStack() {
        Intent intent = getIntent();
        if (intent == null)
            return;
        ArrayList<String> backStackEntryList = intent.getStringArrayListExtra(INTENT_EXTRA_BACK_STACK_TAGS);
        if (backStackEntryList != null)
            for (String key : backStackEntryList)
                if (key != null)
                    addToBackStack(key);
    }

    private void addToBackStack(@NonNull String key) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, key);
        fragment.setArguments(args);
        ft.replace(R.id.settings_container, fragment, key);
        ft.addToBackStack(key);
        ft.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        String[] themeNames = getResources().getStringArray(R.array.settingsThemeEntries);
        for (String name : themeNames)
            menu.add(name);
        return true;
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle() != null) {
            String itemName = item.getTitle().toString();

            String[] themeNames = getResources().getStringArray(R.array.settingsThemeEntries);
            String[] themeValues = getResources().getStringArray(R.array.settingsThemeValues);

            for (int themeIdx = 0; themeIdx < themeNames.length; themeIdx++) {
                String name = themeNames[themeIdx];
                if (itemName.equals(name)) {
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                    sharedPreferences.edit().putString("settings-theme", themeValues[themeIdx]).commit();
                    restart();
                    return true;
                }
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void restart() {
        // save backstack
        FragmentManager fm = getSupportFragmentManager();
        int backStackEntryCount = fm.getBackStackEntryCount();
        ArrayList<String> backStackTags = null;
        if (backStackEntryCount > 0) {
            backStackTags = new ArrayList<>(backStackEntryCount);
            for (int idx = 0; idx < backStackEntryCount; idx += 1) {
                FragmentManager.BackStackEntry entry = fm.getBackStackEntryAt(idx);
                String tag = entry.getName();
                backStackTags.add(tag);
            }
        }

        // close current activity
        finish();

        // start new activity
        Intent activityIntent = new Intent(this, getClass());
        if (backStackTags != null) {
            // remember the back stack pages so we can restore them
            activityIntent.putStringArrayListExtra(INTENT_EXTRA_BACK_STACK_TAGS, backStackTags);
        }
        startActivity(activityIntent);

        // set transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (color != 0 && !(title instanceof Spannable)) {
                SpannableString ss = new SpannableString(title);
                ss.setSpan(new ForegroundColorSpan(color), 0, title.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                actionBar.setTitle(ss);
            } else {
                actionBar.setTitle(title);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().popBackStackImmediate()) {
            final int count = getSupportFragmentManager().getBackStackEntryCount();
            CharSequence title = null;
            if (count > 0) {
                String tag = getSupportFragmentManager().getBackStackEntryAt(count - 1).getName();
                if (tag != null) {
                    Fragment fragment = getSupportFragmentManager().findFragmentByTag(SettingsFragment.FRAGMENT_TAG);
                    if (fragment instanceof SettingsFragment) {
                        Preference preference = ((SettingsFragment) fragment).findPreference(tag);
                        if (preference != null)
                            title = preference.getTitle();
                    }
                }
            }
            if (title != null)
                setTitle(title);
            else
                setTitle(R.string.menu_popup_launcher_settings);
            return true;
        }
        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen preferenceScreen) {
        final String key = preferenceScreen.getKey();
        addToBackStack(key);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult request=" + requestCode + " result=" + resultCode);
        if (requestCode == ENABLE_DEVICE_ADMIN) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Failed!", Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == RESULT_OK) {
            ExportedData.Method method = null;
            switch (requestCode) {
                case FILE_SELECT_XML_APPEND:
                    method = ExportedData.Method.APPEND;
                    break;
                case FILE_SELECT_XML_OVERWRITE:
                    method = ExportedData.Method.OVERWRITE;
                    break;
                case FILE_SELECT_XML_SET:
                    method = ExportedData.Method.SET;
                    break;
            }
            if (method != null) {
                Uri uri = data != null ? data.getData() : null;
                File importedFile = FileUtils.copyFile(this, uri, "imported.xml");
                if (importedFile != null) {
                    PleaseWaitDialog dialog = new PleaseWaitDialog();
                    // set args
                    {
                        Bundle args = new Bundle();
                        //args.putString(PleaseWaitDialog.ARG_TITLE, getString(R.string.import_dialog_title));
                        args.putString(PleaseWaitDialog.ARG_DESCRIPTION, getString(R.string.import_dialog_description));
                        dialog.setArguments(args);
                    }
                    final ExportedData.Method importMethod = method;
                    dialog.setWork(() -> {
                        Activity activity = Utilities.getActivity(dialog.getContext());
                        if (activity != null) {
                            if (!XmlImport.settingsXml(activity, importedFile, importMethod)) {
                                Toast.makeText(activity, R.string.error_fail_import, Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                            }
                        }
                        dialog.onWorkFinished();
                    });
                    dialog.show(getSupportFragmentManager(), "load_imported");
                } else {
                    Toast.makeText(this, R.string.error_fail_import, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String FRAGMENT_TAG = SettingsFragment.class.getName();
        private static final String DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG";
        private static final String TAG = "Settings";

        private static Pair<CharSequence[], CharSequence[]> AppToRunListContent = null;
        private static Pair<CharSequence[], CharSequence[]> ShortcutToRunListContent = null;
        private static Pair<CharSequence[], CharSequence[]> EntryToShowListContent = null;
        private static ContentLoadHelper.OrderedMultiSelectListData TagsMenuContent = null;
        private static ContentLoadHelper.OrderedMultiSelectListData ResultPopupContent = null;
        private static Pair<CharSequence[], CharSequence[]> MimeTypeListContent = null;

        public SettingsFragment() {
            super();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            if (rootKey != null && rootKey.startsWith("feature-"))
                setPreferencesFromResource(R.xml.preference_features, rootKey);
            else
                setPreferencesFromResource(R.xml.preferences, rootKey);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                removePreference("black-notification-icons");
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                removePreference("pin-auto-confirm");
            }

            final Activity activity = requireActivity();

            // set activity title as the preference screen title
            activity.setTitle(getPreferenceScreen().getTitle());

            ActionBar actionBar = ((SettingsActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                // we can change the theme from the options menu
                removePreference("settings-theme");
            }

            setupButtonActions(activity);

            final Context context = requireContext();

            tintPreferenceIcons(getPreferenceScreen(), UIColors.getThemeColor(context, R.attr.colorAccent));

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

            // quick-list
            {
                Preference pref = findPreference("quick-list-enabled");
                // if we don't have the toggle in this screen we need to apply dependency by hand
                if (pref == null) {
                    // only show the category if we use the quick list
                    Preference section = findPreference("quick-list-section");
                    if (section != null)
                        section.setVisible(sharedPreferences.getBoolean("quick-list-enabled", true));
                }
            }

            onCreateAsyncLoad(context, sharedPreferences, savedInstanceState);
        }

        private void setupButtonActions(@NonNull Activity activity) {
            // import settings
            {
                Preference pref = findPreference("import-settings-set");
                if (pref != null)
                    pref.setOnPreferenceClickListener(preference -> {
                        FileUtils.chooseSettingsFile(activity, FILE_SELECT_XML_SET);
                        return true;
                    });
                pref = findPreference("import-settings-overwrite");
                if (pref != null)
                    pref.setOnPreferenceClickListener(preference -> {
                        FileUtils.chooseSettingsFile(activity, FILE_SELECT_XML_OVERWRITE);
                        return true;
                    });
                pref = findPreference("import-settings-append");
                if (pref != null)
                    pref.setOnPreferenceClickListener(preference -> {
                        FileUtils.chooseSettingsFile(activity, FILE_SELECT_XML_APPEND);
                        return true;
                    });
            }
        }

        private void onCreateAsyncLoad(@NonNull Context context, @NonNull SharedPreferences sharedPreferences, @Nullable Bundle savedInstanceState) {
            if (savedInstanceState == null) {
                initAppToRunLists(context, sharedPreferences);
                initShortcutToRunLists(context, sharedPreferences);
                initEntryToShowLists(context, sharedPreferences);
                initTagsMenuList(context, sharedPreferences);
                initResultPopupList(context, sharedPreferences);
                initMimeTypes(context);
            } else {
                synchronized (SettingsFragment.class) {
                    if (AppToRunListContent == null)
                        AppToRunListContent = generateAppToRunListContent(context);
                    if (ShortcutToRunListContent == null)
                        ShortcutToRunListContent = generateShortcutToRunListContent(context);
                    if (EntryToShowListContent == null)
                        EntryToShowListContent = generateEntryToShowListContent(context);
                    if (TagsMenuContent == null)
                        TagsMenuContent = ContentLoadHelper.generateTagsMenuContent(context, sharedPreferences);
                    if (ResultPopupContent == null)
                        ResultPopupContent = ContentLoadHelper.generateResultPopupContent(context, sharedPreferences);
                    if (MimeTypeListContent == null)
                        MimeTypeListContent = generateMimeTypeListContent(context);

                    for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY) {
                        updateAppToRunList(sharedPreferences, gesturePref);
                        updateShortcutToRunList(sharedPreferences, gesturePref);
                        updateEntryToShowList(sharedPreferences, gesturePref);
                    }
                    TagsMenuContent.setMultiListValues(findPreference("tags-menu-list"));
                    TagsMenuContent.setOrderedListValues(findPreference("tags-menu-order"));
                    ResultPopupContent.setOrderedListValues(findPreference("result-popup-order"));
                    ContentLoadHelper.setMultiListValues(findPreference("selected-contact-mime-types"), MimeTypeListContent, null);
                }
            }

            final ListPreference iconsPack = findPreference("icons-pack");
            if (iconsPack != null) {
                iconsPack.setEnabled(false);

                if (savedInstanceState == null) {
                    // Run asynchronously to open settings fast
                    Utilities.runAsync(getLifecycle(),
                        t -> SettingsFragment.this.setListPreferenceIconsPacksData(iconsPack),
                        t -> iconsPack.setEnabled(true));
                } else {
                    // Run synchronously to ensure preferences can be restored from state
                    SettingsFragment.this.setListPreferenceIconsPacksData(iconsPack);
                    iconsPack.setEnabled(true);
                }
            }
        }

        private void initAppToRunLists(@NonNull Context context, @NonNull SharedPreferences sharedPreferences) {
            final Runnable updateLists = () -> {
                for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY)
                    updateAppToRunList(sharedPreferences, gesturePref);
            };
            if (AppToRunListContent == null) {
                Utilities.runAsync(getLifecycle(), t -> {
                    Pair<CharSequence[], CharSequence[]> content = generateAppToRunListContent(context);
                    synchronized (SettingsFragment.class) {
                        if (AppToRunListContent == null)
                            AppToRunListContent = content;
                    }
                }, t -> updateLists.run());
            } else {
                updateLists.run();
            }
        }

        private void initShortcutToRunLists(@NonNull Context context, @NonNull SharedPreferences sharedPreferences) {
            final Runnable updateLists = () -> {
                for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY)
                    updateShortcutToRunList(sharedPreferences, gesturePref);
            };
            if (ShortcutToRunListContent == null) {
                Utilities.runAsync(getLifecycle(), t -> {
                    Pair<CharSequence[], CharSequence[]> content = generateShortcutToRunListContent(context);
                    synchronized (SettingsFragment.this) {
                        if (ShortcutToRunListContent == null)
                            ShortcutToRunListContent = content;
                    }
                }, t -> updateLists.run());
            } else {
                updateLists.run();
            }
        }

        private void initEntryToShowLists(@NonNull Context context, @NonNull SharedPreferences sharedPreferences) {
            final Runnable updateLists = () -> {
                for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY)
                    updateEntryToShowList(sharedPreferences, gesturePref);
            };
            if (EntryToShowListContent == null) {
                Utilities.runAsync(getLifecycle(), t -> {
                    Pair<CharSequence[], CharSequence[]> content = generateEntryToShowListContent(context);
                    synchronized (SettingsFragment.class) {
                        if (EntryToShowListContent == null)
                            EntryToShowListContent = content;
                    }
                }, t -> updateLists.run());
            } else {
                updateLists.run();
            }
        }

        private void initTagsMenuList(@NonNull Context context, @NonNull SharedPreferences sharedPreferences) {
            final Runnable setTagsMenuValues = () -> {
                synchronized (SettingsFragment.class) {
                    if (TagsMenuContent != null) {
                        TagsMenuContent.setMultiListValues(findPreference("tags-menu-list"));
                        TagsMenuContent.setOrderedListValues(findPreference("tags-menu-order"));
                    }
                }
            };

            if (TagsMenuContent == null) {
                Utilities.runAsync(getLifecycle(), t -> {
                    ContentLoadHelper.OrderedMultiSelectListData content = ContentLoadHelper.generateTagsMenuContent(context, sharedPreferences);
                    synchronized (SettingsFragment.class) {
                        if (TagsMenuContent == null) {
                            TagsMenuContent = content;
                        }
                    }
                }, t -> setTagsMenuValues.run());
            } else {
                setTagsMenuValues.run();
            }
        }

        private void initResultPopupList(@NonNull Context context, @NonNull SharedPreferences sharedPreferences) {
            final Runnable setResultPopupValues = () -> {
                synchronized (SettingsFragment.class) {
                    if (ResultPopupContent != null)
                        ResultPopupContent.setOrderedListValues(findPreference("result-popup-order"));
                }
            };

            if (ResultPopupContent == null) {
                Utilities.runAsync(getLifecycle(), t -> {
                    ContentLoadHelper.OrderedMultiSelectListData content = ContentLoadHelper.generateResultPopupContent(context, sharedPreferences);
                    synchronized (SettingsFragment.class) {
                        if (ResultPopupContent == null) {
                            ResultPopupContent = content;
                        }
                    }
                }, t -> setResultPopupValues.run());
            } else {
                setResultPopupValues.run();
            }
        }

        private void initMimeTypes(@NonNull Context context) {
            // get all supported mime types
            final Runnable setMimeTypeValues = () -> {
                synchronized (SettingsFragment.class) {
                    if (MimeTypeListContent != null)
                        ContentLoadHelper.setMultiListValues(findPreference("selected-contact-mime-types"), MimeTypeListContent, null);
                }
            };

            if (MimeTypeListContent == null) {
                Utilities.runAsync(getLifecycle(), t -> {
                    Pair<CharSequence[], CharSequence[]> content = generateMimeTypeListContent(context);
                    synchronized (SettingsFragment.class) {
                        if (MimeTypeListContent == null)
                            MimeTypeListContent = content;
                    }
                }, t -> setMimeTypeValues.run());
            } else {
                setMimeTypeValues.run();
            }
        }

        private void tintPreferenceIcons(Preference preference, int color) {
            Drawable icon = preference.getIcon();
            if (icon != null) {
                // workaround to set drawable size
                {
                    int size = UISizes.getResultIconSize(preference.getContext());
                    icon = new SizeWrappedDrawable(icon, size);
                }
                icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                preference.setIcon(icon);
            }
            if (preference instanceof PreferenceGroup) {
                PreferenceGroup group = ((PreferenceGroup) preference);
                for (int i = 0; i < group.getPreferenceCount(); i++) {
                    tintPreferenceIcons(group.getPreference(i), color);
                }
            }
        }

        private void removePreference(String key) {
            Preference pref = findPreference(key);
            if (pref != null && pref.getParent() != null)
                pref.getParent().removePreference(pref);
        }

        private void setListPreferenceIconsPacksData(ListPreference lp) {
            Context context = getContext();
            if (context == null)
                return;
            IconsHandler iph = TBApplication.getApplication(context).iconsHandler();

            CharSequence[] entries = new CharSequence[iph.getIconPackNames().size() + 1];
            CharSequence[] entryValues = new CharSequence[iph.getIconPackNames().size() + 1];

            int i = 0;
            entries[0] = this.getString(R.string.icons_pack_default_name);
            entryValues[0] = "default";
            for (String packageIconsPack : iph.getIconPackNames().keySet()) {
                entries[++i] = iph.getIconPackNames().get(packageIconsPack);
                entryValues[i] = packageIconsPack;
            }

            lp.setEntries(entries);
            lp.setDefaultValue("default");
            lp.setEntryValues(entryValues);
        }

        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);

            applyNotificationBarColor(sharedPreferences, requireContext());
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            synchronized (SettingsFragment.class) {
                AppToRunListContent = null;
                ShortcutToRunListContent = null;
                EntryToShowListContent = null;
                TagsMenuContent = null;
                ResultPopupContent = null;
            }
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            // Try if the preference is one of our custom Preferences
            DialogFragment dialogFragment = null;
            if (preference instanceof CustomDialogPreference) {
                // Create a new instance of CustomDialog with the key of the related Preference
                String key = preference.getKey();
                Log.d(TAG, "onDisplayPreferenceDialog " + key);
                switch (key) {
                    case "quick-list-content":
                        dialogFragment = QuickListPreferenceDialog.newInstance(key);
                        break;
                    case "reset-search-engines":
                    case "edit-search-engines":
                    case "add-search-engine":
                        dialogFragment = EditSearchEnginesPreferenceDialog.newInstance(key);
                        break;
                    case "reset-search-hint":
                    case "edit-search-hint":
                    case "add-search-hint":
                        dialogFragment = EditSearchHintPreferenceDialog.newInstance(key);
                        break;
                    default:
                        dialogFragment = null;
                }
                if (dialogFragment == null) {
                    @LayoutRes
                    int dialogLayout = ((CustomDialogPreference) preference).getDialogLayoutResource();
                    if (dialogLayout == 0) {
                        if (key.endsWith("-color") || key.endsWith("-argb"))
                            dialogFragment = PreferenceColorDialog.newInstance(key);
                    } else if (R.layout.pref_slider == dialogLayout) {
                        dialogFragment = SliderDialog.newInstance(key);
                    } else if (R.layout.pref_confirm == dialogLayout) {
                        dialogFragment = ConfirmDialog.newInstance(key);
                    }
                }
                if (dialogFragment == null)
                    throw new IllegalArgumentException("CustomDialogPreference \"" + key + "\" has no dialog defined");
            } else if (preference instanceof ListPreference) {
                String key = preference.getKey();
                switch (key) {
                    case "adaptive-shape":
                    case "contacts-shape":
                    case "shortcut-shape":
                    case "icons-pack":
                        dialogFragment = IconListPreferenceDialog.newInstance(key);
                        break;
                    default:
                        dialogFragment = BaseListPreferenceDialog.newInstance(key);
                        break;
                }
            } else if (preference instanceof MultiSelectListPreference) {
                String key = preference.getKey();
                if ("tags-menu-order".equals(key)) {
                    dialogFragment = TagOrderListPreferenceDialog.newInstance(key);
                } else if ("result-popup-order".equals(key)) {
                    dialogFragment = OrderListPreferenceDialog.newInstance(key);
                } else {
                    dialogFragment = BaseMultiSelectListPreferenceDialog.newInstance(key);
                }
            }

            // If it was one of our custom Preferences, show its dialog
            if (dialogFragment != null) {
                final FragmentManager fm = this.getParentFragmentManager();
                // check if dialog is already showing
                if (fm.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
                    return;
                }
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(fm, DIALOG_FRAGMENT_TAG);
            }
            // Could not be handled here. Try with the super method.
            else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        private static Pair<CharSequence[], CharSequence[]> generateAppToRunListContent(@NonNull Context context) {
            List<AppEntry> appEntryList = TBApplication.appsHandler(context).getApplications();
            Collections.sort(appEntryList, AppEntry.NAME_COMPARATOR);
            final int appCount = appEntryList.size();
            CharSequence[] entries = new CharSequence[appCount];
            CharSequence[] entryValues = new CharSequence[appCount];
            for (int idx = 0; idx < appCount; idx++) {
                AppEntry appEntry = appEntryList.get(idx);
                entries[idx] = appEntry.getName();
                entryValues[idx] = appEntry.getUserComponentName();
            }
            return new Pair<>(entries, entryValues);
        }

        private static Pair<CharSequence[], CharSequence[]> generateShortcutToRunListContent(@NonNull Context context) {
            ShortcutsProvider shortcutsProvider = TBApplication.dataHandler(context).getShortcutsProvider();
            List<? extends EntryItem> shortcutList = shortcutsProvider == null ? null : shortcutsProvider.getPojos();
            if (shortcutList == null)
                return new Pair<>(new CharSequence[0], new CharSequence[0]);
            // copy list in order to sort it
            shortcutList = new ArrayList<>(shortcutList);
            Collections.sort(shortcutList, EntryItem.NAME_COMPARATOR);
            final int entryCount = shortcutList.size();
            CharSequence[] entries = new CharSequence[entryCount];
            CharSequence[] entryValues = new CharSequence[entryCount];
            for (int idx = 0; idx < entryCount; idx++) {
                EntryItem shortcutEntry = shortcutList.get(idx);
                entries[idx] = shortcutEntry.getName();
                entryValues[idx] = shortcutEntry.id;
            }
            return new Pair<>(entries, entryValues);
        }

        private static Pair<CharSequence[], CharSequence[]> generateEntryToShowListContent(@NonNull Context context) {
            final List<StaticEntry> tagList;

            final TBApplication app = TBApplication.getApplication(context);
            final TagsProvider tagsProvider = app.getDataHandler().getTagsProvider();
            if (tagsProvider != null) {
                ArrayList<String> tagNames = new ArrayList<>(app.tagsHandler().getValidTags());
                Collections.sort(tagNames);
                tagList = new ArrayList<>(tagNames.size());
                for (String tagName : tagNames) {
                    TagEntry tagEntry = tagsProvider.getTagEntry(tagName);
                    tagList.add(tagEntry);
                }
            } else {
                tagList = Collections.emptyList();
            }

            final CharSequence[] entries;
            final CharSequence[] entryValues;
            if (tagList.isEmpty()) {
                entries = new CharSequence[]{context.getString(R.string.no_tags)};
                entryValues = new CharSequence[]{""};
            } else {
                return ContentLoadHelper.generateStaticEntryList(context, tagList);
            }
            return new Pair<>(entries, entryValues);
        }

        private static Pair<CharSequence[], CharSequence[]> generateMimeTypeListContent(@NonNull Context context) {
            Set<String> supportedMimeTypes = MimeTypeUtils.getSupportedMimeTypes(context);
            Map<String, String> labels = TBApplication.mimeTypeCache(context).getUniqueLabels(context, supportedMimeTypes);

            String[] mimeTypes = labels.keySet().toArray(new String[0]);
            Arrays.sort(mimeTypes);

            CharSequence[] mimeLabels = new CharSequence[mimeTypes.length];
            for (int index = 0; index < mimeTypes.length; index += 1) {
                mimeLabels[index] = labels.get(mimeTypes[index]);
            }
            return new Pair<>(mimeTypes, mimeLabels);
        }

        private void updateListPrefDependency(@NonNull String dependOnKey, @Nullable String dependOnValue, @NonNull String enableValue, @NonNull String listKey, @Nullable Pair<CharSequence[], CharSequence[]> listContent) {
            Preference prefEntryToRun = findPreference(listKey);
            if (prefEntryToRun instanceof ListPreference) {
                synchronized (SettingsFragment.class) {
                    if (listContent != null) {
                        CharSequence[] entries = listContent.first;
                        CharSequence[] entryValues = listContent.second;
                        ((ListPreference) prefEntryToRun).setEntries(entries);
                        ((ListPreference) prefEntryToRun).setEntryValues(entryValues);
                        prefEntryToRun.setVisible(enableValue.equals(dependOnValue));
                        return;
                    }
                }
            }
            if (prefEntryToRun == null) {
                // the ListPreference for selecting an app is missing. Remove the option to run an app.
                Preference pref = findPreference(dependOnKey);
                if (pref instanceof ListPreference) {
                    removeEntryValueFromListPreference(enableValue, (ListPreference) pref);
                }
            } else {
                Log.w(TAG, "ListPreference `" + listKey + "` can't be updated");
                prefEntryToRun.setVisible(false);
            }
        }

        private void updateAppToRunList(@NonNull SharedPreferences sharedPreferences, String key) {
            updateListPrefDependency(key, sharedPreferences.getString(key, null), "runApp", key + "-app-to-run", AppToRunListContent);
        }

        private void updateShortcutToRunList(@NonNull SharedPreferences sharedPreferences, String key) {
            updateListPrefDependency(key, sharedPreferences.getString(key, null), "runShortcut", key + "-shortcut-to-run", ShortcutToRunListContent);
        }

        private void updateEntryToShowList(@NonNull SharedPreferences sharedPreferences, String key) {
            updateListPrefDependency(key, sharedPreferences.getString(key, null), "showEntry", key + "-entry-to-show", EntryToShowListContent);
        }

        private static void removeEntryValueFromListPreference(@NonNull String entryValueToRemove, ListPreference listPref) {
            CharSequence[] entryValues = listPref.getEntryValues();
            int indexToRemove = -1;
            for (int idx = 0, entryValuesLength = entryValues.length; idx < entryValuesLength; idx++) {
                CharSequence entryValue = entryValues[idx];
                if (entryValueToRemove.contentEquals(entryValue)) {
                    indexToRemove = idx;
                    break;
                }
            }
            if (indexToRemove == -1)
                return;
            CharSequence[] entries = listPref.getEntries();
            final int size = entries.length;
            final int newSize = size - 1;
            CharSequence[] newEntries = new CharSequence[newSize];
            CharSequence[] newEntryValues = new CharSequence[newSize];
            if (indexToRemove > 0) {
                System.arraycopy(entries, 0, newEntries, 0, indexToRemove);
                System.arraycopy(entryValues, 0, newEntryValues, 0, indexToRemove);
            }
            if (indexToRemove < newSize) {
                System.arraycopy(entries, indexToRemove + 1, newEntries, indexToRemove, newSize - indexToRemove);
                System.arraycopy(entryValues, indexToRemove + 1, newEntryValues, indexToRemove, newSize - indexToRemove);
            }
            listPref.setEntries(newEntries);
            listPref.setEntryValues(newEntryValues);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            SettingsActivity activity = (SettingsActivity) getActivity();
            if (activity == null || key == null)
                return;

            if (PREF_LISTS_WITH_DEPENDENCY.contains(key)) {
                updateAppToRunList(sharedPreferences, key);
                updateShortcutToRunList(sharedPreferences, key);
                updateEntryToShowList(sharedPreferences, key);
            }

            // rebind and relayout all visible views because I can't find how to rebind only the current view
            getListView().getAdapter().notifyDataSetChanged();

            SettingsActivity.onSharedPreferenceChanged(activity, sharedPreferences, key);

            synchronized (SettingsFragment.class) {
                if (TagsMenuContent != null) {
                    if ("tags-menu-list".equals(key) || "tags-menu-order".equals(key)) {
                        TagsMenuContent.reloadOrderedValues(sharedPreferences, this, "tags-menu-order");
                    } else if ("result-popup-order".equals(key)) {
                        ResultPopupContent.reloadOrderedValues(sharedPreferences, this, "result-popup-order");
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void setActionBarTextColor(Activity activity, int color) {
        ActionBar actionBar = activity instanceof AppCompatActivity
            ? ((AppCompatActivity) activity).getSupportActionBar()
            : null;
        CharSequence title = actionBar != null ? actionBar.getTitle() : null;
        if (title == null)
            return;
        activity.setTitleColor(color);

        Drawable arrow = AppCompatResources.getDrawable(activity, R.drawable.ic_arrow_back);
        if (arrow != null) {
            arrow = DrawableCompat.wrap(arrow);
            DrawableCompat.setTint(arrow, color);
            actionBar.setHomeAsUpIndicator(arrow);
        }

        SpannableString text = new SpannableString(title);
        ForegroundColorSpan[] spansToRemove = text.getSpans(0, text.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spansToRemove) {
            text.removeSpan(span);
        }
        text.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        actionBar.setTitle(text);
    }

    private static void applyNotificationBarColor(@NonNull SharedPreferences sharedPreferences, @Nullable Context context) {
        int color = UIColors.getColor(sharedPreferences, "notification-bar-argb");
        // keep the bars opaque to avoid white text on white background by mistake
        int alpha = 0xFF;//UIColors.getAlpha(sharedPreferences, "notification-bar-alpha");
        Activity activity = Utilities.getActivity(context);
        if (activity instanceof SettingsActivity)
            UIColors.setStatusBarColor((SettingsActivity) activity, UIColors.setAlpha(color, alpha));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View view = activity != null ? activity.findViewById(android.R.id.content) : null;
            if (view == null && activity != null)
                view = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (view != null) {
                if (sharedPreferences.getBoolean("black-notification-icons", false)) {
                    SystemUiVisibility.setLightStatusBar(view);
                } else {
                    SystemUiVisibility.clearLightStatusBar(view);
                }
            }
        }
        setActionBarTextColor(activity, UIColors.getTextContrastColor(color));
    }

    private static void applyNavigationBarColor(@NonNull SharedPreferences sharedPreferences, @Nullable Context context) {
        int color = UIColors.getColor(sharedPreferences, "navigation-bar-argb");
        Activity activity = Utilities.getActivity(context);
        if (activity instanceof SettingsActivity)
            UIColors.setNavigationBarColor((SettingsActivity) activity, color, color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View view = activity != null ? activity.findViewById(android.R.id.content) : null;
            if (view == null && activity != null)
                view = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (view != null) {
                if (UIColors.isColorLight(color)) {
                    SystemUiVisibility.setLightNavigationBar(view);
                } else {
                    SystemUiVisibility.clearLightNavigationBar(view);
                }
            }
        }
    }

    public static void onSharedPreferenceChanged(Context context, SharedPreferences sharedPreferences, String key) {
        TBApplication app = TBApplication.getApplication(context);

        if (PREF_THAT_REQUIRE_LAYOUT_UPDATE.contains(key))
            app.requireLayoutUpdate();

        TBLauncherActivity activity = app.launcherActivity();

        if (activity != null)
            activity.liveWallpaper.onPrefChanged(sharedPreferences, key);

        switch (key) {
            case "notification-bar-argb":
            case "black-notification-icons":
                applyNotificationBarColor(sharedPreferences, context);
                break;
            case "navigation-bar-argb":
                applyNavigationBarColor(sharedPreferences, context);
                break;
            case "icon-scale-red":
            case "icon-scale-green":
            case "icon-scale-blue":
            case "icon-scale-alpha":
            case "icon-hue":
            case "icon-contrast":
            case "icon-brightness":
            case "icon-saturation":
            case "icon-background-argb":
            case "matrix-contacts":
            case "icons-visible":
                TBApplication.drawableCache(context).clearCache();
                if (activity != null)
                    activity.refreshSearchRecords();
                // fallthrough
            case "quick-list-argb":
            case "quick-list-ripple-color":
                // static entities will change color based on luminance
                // fallthrough
            case "quick-list-toggle-color":
                // toggle animation is also caching the color
                if (activity != null)
                    activity.queueDockReload();
                // fallthrough
            case "result-list-argb":
            case "result-ripple-color":
            case "result-highlight-color":
            case "result-text-color":
            case "result-text2-color":
            case "result-shadow-color":
            case "contact-action-color":
                if (activity != null)
                    activity.refreshSearchRecords();
                // fallthrough
            case "search-bar-text-color":
            case "popup-background-argb":
            case "popup-border-argb":
            case "popup-ripple-color":
            case "popup-text-color":
            case "popup-title-color":
                UIColors.resetCache();
                break;
            case "quick-list-icon-size":
                if (activity != null)
                    activity.queueDockReload();
                // fallthrough
            case "result-text-size":
            case "result-text2-size":
            case "result-icon-size":
            case "result-shadow-radius":
            case "result-shadow-dx":
            case "result-shadow-dy":
            case "result-list-row-height":
                if (activity != null)
                    activity.refreshSearchRecords();
                // fallthrough
            case "tags-menu-icon-size":
            case "popup-corner-radius":
                UISizes.resetCache();
                break;
            case "result-history-size":
            case "result-history-adaptive":
            case "fuzzy-search-tags":
            case "result-search-cap":
            case "tags-menu-icons":
            case "loading-icon":
            case "tags-menu-untagged":
            case "tags-menu-untagged-index":
            case "result-popup-order":
                PrefCache.resetCache();
                break;
            case "adaptive-shape":
            case "force-adaptive":
            case "force-shape":
            case "icons-pack":
            case "contact-pack-mask":
            case "contacts-shape":
            case "shortcut-pack-mask":
            case "shortcut-shape":
            case "shortcut-pack-badge-mask":
                TBApplication.iconsHandler(context).onPrefChanged(sharedPreferences);
                TBApplication.drawableCache(context).clearCache();
                if (activity != null)
                    activity.queueDockReload();
                break;
            case "tags-enabled": {
                boolean useTags = sharedPreferences.getBoolean("tags-enabled", true);
                Activity settingsActivity = Utilities.getActivity(context);
                Fragment fragment = null;
                if (settingsActivity instanceof SettingsActivity)
                    fragment = ((SettingsActivity) settingsActivity).getSupportFragmentManager().findFragmentByTag(SettingsFragment.FRAGMENT_TAG);
                SwitchPreference preference = null;
                if (fragment instanceof SettingsFragment)
                    preference = ((SettingsFragment) fragment).findPreference("fuzzy-search-tags");
                if (preference != null)
                    preference.setChecked(useTags);
                else
                    sharedPreferences.edit().putBoolean("fuzzy-search-tags", useTags).apply();
                break;
            }
            case "quick-list-enabled":
            case "quick-list-text-visible":
            case "quick-list-icons-visible":
            case "quick-list-show-badge":
            case "quick-list-columns":
            case "quick-list-rows":
            case "quick-list-rtl":
                if (activity != null)
                    activity.queueDockReload();
                break;
            case "cache-drawable":
            case "cache-half-apps":
                TBApplication.drawableCache(context).onPrefChanged(context, sharedPreferences);
                break;
            case "enable-search":
            case "enable-url":
            case "enable-calculator":
            case "enable-dial":
            case "enable-contacts":
            case "selected-contact-mime-types":
            case "shortcut-dynamic-in-results":
                TBApplication.dataHandler(context).reloadProviders();
                break;
            case "root-mode":
                if (sharedPreferences.getBoolean("root-mode", false) &&
                    !TBApplication.rootHandler(context).isRootAvailable()) {
                    //show error dialog
                    new AlertDialog.Builder(context).setMessage(R.string.root_mode_error)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            sharedPreferences.edit().putBoolean("root-mode", false).apply();
                        }).show();
                }
                TBApplication.rootHandler(context).resetRootHandler(sharedPreferences);
                break;
            case "tags-menu-list":
                PrefOrderedListHelper.syncOrderedList(sharedPreferences, "tags-menu-list", "tags-menu-order");
                break;
        }
    }
}