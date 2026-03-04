package com.darexsh.revancedupdater;

class LatestFetchState {
    final String newestVersion;
    final String downloadUrl;
    final String fetchError;

    LatestFetchState(String newestVersion, String downloadUrl, String fetchError) {
        this.newestVersion = newestVersion;
        this.downloadUrl = downloadUrl;
        this.fetchError = fetchError;
    }
}
