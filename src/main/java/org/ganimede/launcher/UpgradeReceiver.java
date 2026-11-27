package org.ganimede.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** After an upgrade: builds that offered an app icon choice left .IconDefault disabled; the manifest default is the only entry now. */
public class UpgradeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        ComponentName entry = new ComponentName(context, context.getPackageName() + ".IconDefault");
        PackageManager pm = context.getPackageManager();
        if (pm.getComponentEnabledSetting(entry) != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            pm.setComponentEnabledSetting(entry, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
    }
}
