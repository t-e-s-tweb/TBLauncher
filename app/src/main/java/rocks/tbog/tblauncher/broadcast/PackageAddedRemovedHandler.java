package rocks.tbog.tblauncher.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import rocks.tbog.tblauncher.DataHandler;
import rocks.tbog.tblauncher.TBApplication;
import rocks.tbog.tblauncher.dataprovider.AppProvider;
import rocks.tbog.tblauncher.utils.UserHandleCompat;

/**
 * This class gets called when an application is created or removed on the
 * system
 * <p/>
 * We then recreate our data set.
 *
 * @author dorvaryn
 */
public class PackageAddedRemovedHandler extends BroadcastReceiver {

    public static void handleEvent(Context ctx, String action, String packageName, UserHandleCompat user, boolean replacing) {
        DataHandler dataHandler = TBApplication.getApplication(ctx).getDataHandler();

        if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("enable-app-history", true)) {
            // Insert into history new packages (not updated ones)
            if ("android.intent.action.PACKAGE_ADDED".equals(action) && !replacing) {
                // Add new package to history
                Intent launchIntent = ctx.getPackageManager().getLaunchIntentForPackage(packageName);
                if (launchIntent == null || launchIntent.getComponent() == null) {
                    //for some plugin app
                    return;
                }

                String className = launchIntent.getComponent().getClassName();
                String pojoID = user.getUserComponentName(packageName, className);
                dataHandler.addToHistory(pojoID);
                // Add shortcut
                dataHandler.addShortcut(packageName);
            }
        }

        if ("android.intent.action.PACKAGE_REMOVED".equals(action) && !replacing) {
            // Remove all installed shortcuts
            dataHandler.removeShortcuts(packageName);
            dataHandler.removeFromExcluded(packageName);
        }

        TBApplication.getApplication(ctx).resetIconsHandler();

        // Reload application list
        final AppProvider provider = dataHandler.getAppProvider();
        if (provider != null) {
            provider.reload();
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {

        String packageName = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;

        if (packageName == null || packageName.equalsIgnoreCase(ctx.getPackageName())) {
            // When running KISS locally, sending a new version of the APK immediately triggers a "package removed" for fr.neamar.kiss,
            // There is no need to handle this event.
            // Discarding it makes startup time much faster locally as apps don't have to be loaded twice.
            return;
        }

        handleEvent(ctx,
                intent.getAction(),
                packageName, new UserHandleCompat(),
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        );

    }

}
