package com.darexsh.revancedupdater;

import android.widget.TextView;

class AppInfoBox {
    final TextView tvNewestVersion;
    final String displayName;
    final String packageName;
    String installedVersion;
    String newestVersion;
    String apkUrl;
    String fetchError;

    AppInfoBox(TextView tvNewestVersion, String installedVersion, String displayName, String packageName) {
        this.tvNewestVersion = tvNewestVersion;
        this.installedVersion = installedVersion;
        this.newestVersion = "-";
        this.fetchError = null;
        this.displayName = displayName;
        this.packageName = packageName;
    }
}
