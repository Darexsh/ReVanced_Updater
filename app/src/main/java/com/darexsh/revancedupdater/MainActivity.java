package com.darexsh.revancedupdater;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Main activity of the ReVanced Updater application
public class MainActivity extends AppCompatActivity {

    private LinearLayout container; // Container for displaying app info boxes
    private final Map<String, AppInfoBox> appInfoBoxes = new HashMap<>(); // Map of app names to their corresponding info boxes

    // List of supported apps
    private final String[] allApps = {
            "YouTube ReVanced",
            "Youtube Music ReVanced",
            "Google Photos ReVanced",
            "TikTok ReVanced",
            "Spotify ReVanced",
            "microG"
    };

    // Corresponding package names for supported apps
    private final String[] packageNames = {
            "app.revanced.android.youtube",
            "app.revanced.android.apps.youtube.music",
            "app.revanced.android.photos",
            "com.zhiliaoapp.musically",
            "com.spotify.music",
            "app.revanced.android.gms"
    };

    private boolean[] selectedApps; // Stores which apps are selected for updates
    private static final String PREFS_NAME = "RevancedUpdaterPrefs";
    private static final String KEY_SELECTED_APPS = "selectedApps";
    private static final String KEY_SELECTED_LANGUAGE = "selectedLanguage"; // 0 = German, 1 = English, 2 = Russian, 3 = Spanish, 4 = French, 5 = Italian, 6 = Turkish, 7 = Polish, 8 = Chinese

    // Attach base context to handle language changes
    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int lang = prefs.getInt(KEY_SELECTED_LANGUAGE, 1);

        Locale newLocale;
        switch (lang) {
            case 0: newLocale = new Locale("de"); break;
            case 1: newLocale = new Locale("en"); break;
            case 2: newLocale = new Locale("ru"); break;
            case 3: newLocale = new Locale("es"); break;
            case 4: newLocale = new Locale("fr"); break;
            case 5: newLocale = new Locale("it"); break;
            case 6: newLocale = new Locale("tr"); break;
            case 7: newLocale = new Locale("pl"); break;
            case 8: newLocale = new Locale("cn"); break;
            default: newLocale = new Locale("en"); break;
        }
        Locale.setDefault(newLocale);

        Configuration config = new Configuration();
        config.setLocale(newLocale);

        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    // Initialize the activity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        container = findViewById(R.id.appContainer);

        // Load selected apps from SharedPreferences
        selectedApps = new boolean[allApps.length];
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(KEY_SELECTED_APPS, null);
        if (saved != null) {
            String[] parts = saved.split(",");
            for (int i = 0; i < allApps.length; i++) {
                selectedApps[i] = i >= parts.length || Boolean.parseBoolean(parts[i]);
            }
        } else {
            for (int i = 0; i < allApps.length; i++) selectedApps[i] = true;
        }

        // Display the initial app list
        refreshAppList();

        // Setup "Check Updates" button
        Button btnCheckUpdates = findViewById(R.id.btnCheckUpdates);
        btnCheckUpdates.setText(R.string.check_updates);
        btnCheckUpdates.setOnClickListener(v -> {
            for (Map.Entry<String, AppInfoBox> entry : appInfoBoxes.entrySet()) {
                AppInfoBox infoBox = entry.getValue();
                infoBox.tvNewestVersion.setText(getString(R.string.newest_version, getString(R.string.download_wait)));
                fetchNewestVersion(entry.getKey(), infoBox);
            }
        });

        // Setup "Select Apps" button
        Button btnSelectApps = findViewById(R.id.btnSelectApps);
        btnSelectApps.setText(R.string.select_apps);
        btnSelectApps.setOnClickListener(v -> showSelectAppsDialog());

        // Setup "Change Language" button
        Button btnChangeLanguage = findViewById(R.id.btnChangeLanguage);
        btnChangeLanguage.setOnClickListener(v -> showLanguageDialog());
    }

    // Show dialog to select which apps to display
    private void showSelectAppsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_apps)
                .setMultiChoiceItems(allApps, selectedApps, (d, which, isChecked) -> selectedApps[which] = isChecked)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (boolean b : selectedApps) sb.append(b).append(",");
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putString(KEY_SELECTED_APPS, sb.toString())
                            .apply();
                    refreshAppList();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // Show dialog to select language
    private void showLanguageDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int currentLang = prefs.getInt(KEY_SELECTED_LANGUAGE, 1);

        String[] languages = {
                getString(R.string.german),
                getString(R.string.english),
                getString(R.string.russian),
                getString(R.string.spanish),
                getString(R.string.french),
                getString(R.string.italian),
                getString(R.string.turkish),
                getString(R.string.polish),
                getString(R.string.chinese)
        };

        AlertDialog.Builder langDialog = new AlertDialog.Builder(this);
        langDialog.setTitle(R.string.language_selection_title)
                .setSingleChoiceItems(languages, currentLang, (d, which) -> {
                    prefs.edit().putInt(KEY_SELECTED_LANGUAGE, which).apply();
                    recreate(); // Refresh UI with new language
                    d.dismiss();
                })
                .show();
    }

    // Check for old APKs on resume and refresh the app list
    @Override
    protected void onResume() {
        super.onResume();

        File apkDir = getExternalFilesDir(null);
        assert apkDir != null;
        File[] apkFiles = apkDir.listFiles((dir, name) -> name.endsWith(".apk"));

        // Prompt user to delete old APKs if found
        if (apkFiles != null && apkFiles.length > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.app_name)
                    .setMessage(R.string.old_apks_found)
                    .setPositiveButton(R.string.delete_yes, (d, w) -> {
                        for (File file : apkFiles) file.delete();
                        refreshAppList();
                    })
                    .setNegativeButton(R.string.delete_no, null)
                    .show();
        } else {
            refreshAppList();
        }
    }

    // Refresh the displayed app list
    private void refreshAppList() {
        container.removeAllViews();
        appInfoBoxes.clear();

        PackageManager pm = getPackageManager();
        @SuppressLint("QueryPermissionsNeeded") List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (int i = 0; i < allApps.length; i++) {
            if (selectedApps[i]) addAppBox(packageNames[i], allApps[i], pm, apps);
        }
    }

    // Add UI box for an app
    private void addAppBox(String packageName, String displayName, PackageManager pm, List<ApplicationInfo> apps) {
        String installedVersion = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (apps.stream().anyMatch(a -> a.packageName.equals(packageName))) {
                try {
                    PackageInfo pkgInfo = pm.getPackageInfo(packageName, 0);
                    installedVersion = pkgInfo.versionName;
                } catch (Exception ignored) {}
            }
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 32, 32, 32);
        box.setBackgroundResource(R.drawable.app_box_background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(24, 24, 24, 24);
        box.setLayoutParams(params);

        TextView tvAppName = new TextView(this);
        tvAppName.setText(displayName);
        tvAppName.setTextSize(18);
        tvAppName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvInstalledVersion = new TextView(this);
        tvInstalledVersion.setText(getString(R.string.installed_version, installedVersion != null ? installedVersion : "-"));

        TextView tvNewestVersion = new TextView(this);
        tvNewestVersion.setText(getString(R.string.newest_version, "-"));

        box.addView(tvAppName);
        box.addView(tvInstalledVersion);
        box.addView(tvNewestVersion);
        container.addView(box);

        AppInfoBox infoBox = new AppInfoBox(tvNewestVersion, installedVersion, displayName, packageName);
        appInfoBoxes.put(displayName, infoBox);

        String finalInstalledVersion = installedVersion;
        box.setOnClickListener(v -> {
            String newestVersion = infoBox.tvNewestVersion.getText().toString().replace(getString(R.string.newest_version, ""), "");
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

            // Show appropriate dialog based on installation and version status
            if (finalInstalledVersion == null) {
                builder.setTitle(displayName)
                        .setMessage(getString(R.string.not_installed, displayName))
                        .setPositiveButton(R.string.ok, (d, w) -> startApkInstall(infoBox))
                        .setNegativeButton(R.string.cancel, null);
            } else if (newestVersion.equals("-")) {
                builder.setTitle(displayName)
                        .setMessage(R.string.no_update_yet)
                        .setPositiveButton(R.string.ok, null);
            } else if (newestVersion.equals(getString(R.string.unknown))) {
                builder.setTitle(displayName)
                        .setMessage(getString(R.string.no_new_version, displayName))
                        .setPositiveButton(R.string.ok, null);
            } else if (!newestVersion.equals(finalInstalledVersion)) {
                builder.setTitle(displayName)
                        .setMessage(getString(R.string.update_available, displayName))
                        .setPositiveButton(R.string.ok, (d, w) -> startApkInstall(infoBox))
                        .setNegativeButton(R.string.cancel, null);
            } else {
                builder.setTitle(displayName)
                        .setMessage(getString(R.string.already_latest, displayName))
                        .setPositiveButton(R.string.ok, null);
            }

            builder.create().show();
        });
    }

    // Start APK installation process
    private void startApkInstall(AppInfoBox infoBox) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
        }

        String apkUrl = infoBox.apkUrl;
        if (apkUrl == null || apkUrl.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error_title)
                    .setMessage(getString(R.string.download_error, "No download URL found."))
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        String fileName = apkUrl.substring(apkUrl.lastIndexOf('/') + 1);
        File apkFile = new File(getExternalFilesDir(null), fileName);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.download_title, infoBox.displayName));
        builder.setMessage(R.string.download_wait);

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(50, 20, 50, 20);
        layout.setOrientation(LinearLayout.VERTICAL);

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 20, 0, 0);
        progressBar.setLayoutParams(params);
        layout.addView(progressBar);

        builder.setView(layout);
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();

        // Download APK in a separate thread
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(apkUrl).openConnection();
                connection.connect();
                int fileLength = connection.getContentLength();

                InputStream input = new BufferedInputStream(connection.getInputStream());
                FileOutputStream output = new FileOutputStream(apkFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        new Handler(Looper.getMainLooper()).post(() -> progressBar.setProgress(progress));
                    }
                }

                output.flush();
                output.close();
                input.close();
                connection.disconnect();

                // Launch APK installer after download completes
                new Handler(Looper.getMainLooper()).post(() -> {
                    dialog.dismiss();
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(
                            FileProvider.getUriForFile(this,
                                    getPackageName() + ".provider", apkFile),
                            "application/vnd.android.package-archive"
                    );
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                });

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.error_title)
                            .setMessage(getString(R.string.download_error, e.getMessage()))
                            .setPositiveButton(R.string.ok, null)
                            .show();
                });
            }
        }).start();
    }

    // Fetch the latest version and download URL from GitHub
    private void fetchNewestVersion(String appName, AppInfoBox infoBox) {
        new Thread(() -> {
            String newestVersion = getString(R.string.unknown);
            String downloadUrl = null;

            try {
                HttpURLConnection connection = getHttpURLConnection(appName);

                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    connection.disconnect();

                    JSONArray releases = new JSONArray(sb.toString());

                    outer:
                    for (int i = 0; i < releases.length(); i++) {
                        JSONObject release = releases.getJSONObject(i);
                        JSONArray assets = release.getJSONArray("assets");

                        for (int j = 0; j < assets.length(); j++) {
                            JSONObject asset = assets.getJSONObject(j);
                            String name = asset.getString("name").toLowerCase();
                            Pattern pattern = null;

                            switch (appName) {
                                case "YouTube ReVanced":
                                    if (name.startsWith("youtube-revanced") && name.endsWith("-all.apk"))
                                        pattern = Pattern.compile("youtube-revanced-v([0-9.]+)-all\\.apk");
                                    break;
                                case "Youtube Music ReVanced":
                                    if (name.startsWith("music-revanced") && name.endsWith("-arm64-v8a.apk"))
                                        pattern = Pattern.compile("music-revanced-v([0-9.]+)-arm64-v8a\\.apk");
                                    break;
                                case "Google Photos ReVanced":
                                    if (name.startsWith("googlephotos-revanced") && name.endsWith("-arm64-v8a.apk"))
                                        pattern = Pattern.compile("googlephotos-revanced-v([0-9.]+)-arm64-v8a\\.apk");
                                    break;
                                case "TikTok ReVanced":
                                    if (name.startsWith("tiktok-revanced") && name.endsWith("-all.apk"))
                                        pattern = Pattern.compile("tiktok-revanced-v([0-9.]+)-all\\.apk");
                                    break;
                                case "Spotify ReVanced":
                                    if (name.startsWith("spotify-revanced") && name.endsWith("-all.apk"))
                                        pattern = Pattern.compile("spotify-revanced-v([0-9.]+)-all\\.apk");
                                    break;
                                case "microG":
                                    if (name.endsWith("-signed.apk") && !name.contains("-hw")) {
                                        String tag = release.getString("tag_name");
                                        newestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
                                        downloadUrl = asset.getString("browser_download_url");
                                        break outer;
                                    }
                                    break;
                            }

                            if (pattern != null) {
                                Matcher matcher = pattern.matcher(name);
                                if (matcher.find()) {
                                    newestVersion = matcher.group(1);
                                    downloadUrl = asset.getString("browser_download_url");
                                    break outer;
                                }
                            }
                        }
                    }

                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            String finalVersion = newestVersion;
            String finalDownloadUrl = downloadUrl;
            new Handler(Looper.getMainLooper()).post(() -> {
                infoBox.tvNewestVersion.setText(getString(R.string.newest_version, finalVersion));
                infoBox.apkUrl = finalDownloadUrl;

                // Color code: green if up-to-date, red if update available
                assert finalVersion != null;
                if (!finalVersion.equals(getString(R.string.unknown))) {
                    if (finalVersion.equals(infoBox.installedVersion))
                        infoBox.tvNewestVersion.setTextColor(0xFF4CAF50);
                    else
                        infoBox.tvNewestVersion.setTextColor(0xFFF44336);
                }
            });
        }).start();
    }

    @NonNull
    private static HttpURLConnection getHttpURLConnection(String appName) throws IOException {
        String apiUrl = "https://api.github.com/repos/j-hc/revanced-magisk-module/releases";
        if (appName.equals("microG")) {
            apiUrl = "https://api.github.com/repos/ReVanced/GmsCore/releases";
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "ReVancedUpdater");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        return connection;
    }

    // Internal class to hold app information
    private static class AppInfoBox {
        TextView tvNewestVersion;
        String installedVersion;
        String apkUrl;
        String displayName;
        String packageName;

        AppInfoBox(TextView tvNewestVersion, String installedVersion, String displayName, String packageName) {
            this.tvNewestVersion = tvNewestVersion;
            this.installedVersion = installedVersion;
            this.displayName = displayName;
            this.packageName = packageName;
        }
    }
}

