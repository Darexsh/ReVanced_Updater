package com.darexsh.revancedupdater;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.util.SparseIntArray;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.radiobutton.MaterialRadioButton;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

// Main activity of the ReVanced Updater application
public class MainActivity extends AppCompatActivity {

    private LinearLayout container; // Container for displaying app info boxes
    private TextView tvNoAppsSelected;
    private final Map<String, AppInfoBox> appInfoBoxes = new HashMap<>(); // Map of app names to their corresponding info boxes
    private final Map<String, LatestFetchState> latestFetchStates = new HashMap<>(); // Keeps fetched versions/URLs across UI refreshes
    private String pendingReinstallPackageName;
    private String pendingReinstallDisplayName;
    private DeveloperModeManager developerModeManager;

    private final AppConfig[] appConfigs = {
            new AppConfig("YouTube Morphe", "app.morphe.android.youtube"),
            new AppConfig("YouTube ReVanced", "app.revanced.android.youtube"),
            new AppConfig("Youtube Music Morphe", "app.morphe.android.apps.youtube.music"),
            new AppConfig("Youtube Music ReVanced", "app.revanced.android.apps.youtube.music"),
            new AppConfig("Google Photos ReVanced", "app.revanced.android.photos"),
            new AppConfig("TikTok ReVanced", "com.zhiliaoapp.musically"),
            new AppConfig("Reddit Morphe", "com.reddit.frontpage"),
            new AppConfig("Spotify ReVanced", "com.spotify.music"),
            new AppConfig("microG", "app.revanced.android.gms")
    };

    private boolean[] selectedApps; // Stores which apps are selected for updates
    private static final String PREFS_NAME = "RevancedUpdaterPrefs";
    private static final String KEY_SELECTED_APPS = "selectedApps";
    private static final String KEY_SELECTED_LANGUAGE = "selectedLanguage"; // 0 = German, 1 = English, 2 = Russian, 3 = Spanish, 4 = French, 5 = Italian, 6 = Turkish, 7 = Polish, 8 = Chinese
    private static final String KEY_INITIAL_SELECTION_DONE = "initialSelectionDone";

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
            case 8: newLocale = new Locale("zh"); break;
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
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        container = findViewById(R.id.appContainer);
        tvNoAppsSelected = findViewById(R.id.tvNoAppsSelected);

        // Load selected apps from SharedPreferences
        selectedApps = new boolean[appConfigs.length];
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        developerModeManager = new DeveloperModeManager(this, PREFS_NAME);
        String saved = prefs.getString(KEY_SELECTED_APPS, null);
        if (saved != null) {
            String[] parts = saved.split(",");
            for (int i = 0; i < appConfigs.length; i++) {
                selectedApps[i] = i >= parts.length || Boolean.parseBoolean(parts[i]);
            }
        } else {
            for (int i = 0; i < appConfigs.length; i++) selectedApps[i] = false;
        }

        // Display the initial app list
        refreshAppList();

        if (!prefs.getBoolean(KEY_INITIAL_SELECTION_DONE, false)) {
            showInitialAppSelectionDialog();
        }

        // Setup "Check Updates" button
        Button btnCheckUpdates = findViewById(R.id.btnCheckUpdates);
        btnCheckUpdates.setText(R.string.check_updates);
        btnCheckUpdates.setOnClickListener(v -> checkUpdatesForSelectedApps());

        // Setup "Select Apps" button
        Button btnSelectApps = findViewById(R.id.btnSelectApps);
        btnSelectApps.setText(R.string.select_apps);
        btnSelectApps.setOnClickListener(v -> showSelectAppsDialog());

        // Setup "Change Language" button
        Button btnChangeLanguage = findViewById(R.id.btnChangeLanguage);
        btnChangeLanguage.setOnClickListener(v -> showLanguageDialog());

        TextView settingsInfoButton = findViewById(R.id.settingsInfoButton);
        settingsInfoButton.setOnClickListener(v -> showAppInfoDialog());
        View headerBox = findViewById(R.id.headerBox);
        headerBox.setOnLongClickListener(v -> {
            developerModeManager.showDialog(appConfigs, appInfoBoxes, latestFetchStates, this::checkUpdatesForAllApps);
            return true;
        });

        TextView tvDeviceModel = findViewById(R.id.tvDeviceModel);
        tvDeviceModel.setText(getString(R.string.device_model, getDeviceModel()));

        TextView tvDeviceArch = findViewById(R.id.tvDeviceArch);
        tvDeviceArch.setText(getString(R.string.device_architecture, getDeviceArchitecture()));
    }

    // Show dialog to select which apps to display
    private void showSelectAppsDialog() {
        showAppsSelectionDialog(false);
    }

    private void showInitialAppSelectionDialog() {
        showAppsSelectionDialog(true);
    }

    private void showAppsSelectionDialog(boolean isInitialSetup) {
        boolean[] tempSelection = selectedApps.clone();

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(8), dp(10), dp(4));

        for (int i = 0; i < appConfigs.length; i++) {
            final int index = i;
            MaterialCheckBox checkBox = new MaterialCheckBox(this);
            checkBox.setText(appConfigs[i].displayName);
            checkBox.setChecked(tempSelection[i]);
            checkBox.setTextSize(16);
            checkBox.setPadding(dp(6), dp(8), dp(6), dp(8));
            checkBox.setButtonTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ui_gray)));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> tempSelection[index] = isChecked);
            list.addView(checkBox);
        }
        scrollView.addView(list);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_fancy_selection, null);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        FrameLayout dialogContentContainer = dialogView.findViewById(R.id.dialogContentContainer);
        MaterialButton btnDialogPositive = dialogView.findViewById(R.id.btnDialogPositive);
        MaterialButton btnDialogNegative = dialogView.findViewById(R.id.btnDialogNegative);

        tvDialogTitle.setText(R.string.select_apps);
        dialogContentContainer.addView(scrollView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        dialog.setCancelable(!isInitialSetup);
        dialog.setCanceledOnTouchOutside(!isInitialSetup);

        btnDialogPositive.setOnClickListener(v -> {
            System.arraycopy(tempSelection, 0, selectedApps, 0, selectedApps.length);
            persistSelectedApps();
            if (isInitialSetup) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_INITIAL_SELECTION_DONE, true)
                        .apply();
            }
            refreshAppList();
            dialog.dismiss();
        });

        if (isInitialSetup) {
            btnDialogNegative.setVisibility(View.GONE);
        } else {
            btnDialogNegative.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
        FancyDialogs.styleWindow(dialog, 0.86f);
    }

    private void persistSelectedApps() {
        StringBuilder sb = new StringBuilder();
        for (boolean b : selectedApps) sb.append(b).append(",");
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_APPS, sb.toString())
                .apply();
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

        final int[] selectedLang = {currentLang};

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        group.setPadding(dp(10), dp(8), dp(10), dp(4));

        SparseIntArray idToIndex = new SparseIntArray();
        int checkedId = View.NO_ID;
        for (int i = 0; i < languages.length; i++) {
            MaterialRadioButton radio = new MaterialRadioButton(this);
            int radioId = View.generateViewId();
            radio.setId(radioId);
            idToIndex.put(radioId, i);
            radio.setText(languages[i]);
            radio.setTextSize(16);
            radio.setPadding(dp(6), dp(8), dp(6), dp(8));
            radio.setButtonTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ui_gray)));
            if (i == currentLang) {
                checkedId = radioId;
            }
            group.addView(radio);
        }
        if (checkedId != View.NO_ID) {
            group.check(checkedId);
        }
        group.setOnCheckedChangeListener((g, checkedItemId) -> {
            int idx = idToIndex.get(checkedItemId, currentLang);
            selectedLang[0] = idx;
        });
        scrollView.addView(group);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_fancy_selection, null);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        FrameLayout dialogContentContainer = dialogView.findViewById(R.id.dialogContentContainer);
        MaterialButton btnDialogPositive = dialogView.findViewById(R.id.btnDialogPositive);
        MaterialButton btnDialogNegative = dialogView.findViewById(R.id.btnDialogNegative);

        tvDialogTitle.setText(R.string.language_selection_title);
        dialogContentContainer.addView(scrollView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnDialogPositive.setOnClickListener(v -> {
            if (selectedLang[0] != currentLang) {
                prefs.edit().putInt(KEY_SELECTED_LANGUAGE, selectedLang[0]).apply();
                recreate(); // Refresh UI with new language
            }
            dialog.dismiss();
        });
        btnDialogNegative.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        FancyDialogs.styleWindow(dialog, 0.86f);
    }

    // Check for old APKs on resume and refresh the app list
    @Override
    protected void onResume() {
        super.onResume();

        File apkDir = getExternalFilesDir(null);
        if (apkDir == null) {
            refreshAppList();
            return;
        }
        File[] apkFiles = apkDir.listFiles((dir, name) -> name.endsWith(".apk"));

        // Prompt user to delete old APKs if found
        if (apkFiles != null && apkFiles.length > 0) {
            FancyDialogs.showMessageDialog(MainActivity.this, 
                    getString(R.string.app_name),
                    getString(R.string.old_apks_found),
                    R.string.delete_yes,
                    () -> {
                        for (File file : apkFiles) file.delete();
                        refreshAppList();
                    },
                    R.string.delete_no,
                    null,
                    true
            );
        } else {
            refreshAppList();
        }
    }

    // Refresh the displayed app list
    private void refreshAppList() {
        container.removeAllViews();
        appInfoBoxes.clear();

        PackageManager pm = getPackageManager();
        int shownCount = 0;
        for (int i = 0; i < appConfigs.length; i++) {
            if (selectedApps[i]) {
                addAppBox(appConfigs[i], pm);
                shownCount++;
            }
        }
        tvNoAppsSelected.setVisibility(shownCount == 0 ? TextView.VISIBLE : TextView.GONE);
        handlePendingReinstallIfNeeded();
    }

    // Add UI box for an app
    private void addAppBox(AppConfig appConfig, PackageManager pm) {
        String displayName = appConfig.displayName;
        String installedVersion = getInstalledVersion(pm, appConfig.packageName);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int cardPadding = dp(16);
        box.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
        box.setBackgroundResource(R.drawable.app_box_background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int cardMarginHorizontal = dp(8);
        int cardMarginVertical = dp(8);
        params.setMargins(cardMarginHorizontal, cardMarginVertical, cardMarginHorizontal, cardMarginVertical);
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

        AppInfoBox infoBox = new AppInfoBox(tvNewestVersion, installedVersion, displayName, appConfig.packageName);
        appInfoBoxes.put(displayName, infoBox);
        applyCachedFetchState(infoBox);

        box.setOnClickListener(v -> {
            // Show appropriate dialog based on installation and version status
            if (infoBox.installedVersion == null) {
                if (infoBox.apkUrl == null) {
                    FancyDialogs.showMessageDialog(MainActivity.this, displayName, getString(R.string.no_update_yet));
                } else {
                    FancyDialogs.showMessageDialog(MainActivity.this, 
                            displayName,
                            getString(R.string.not_installed, displayName),
                            R.string.ok,
                            () -> startApkInstall(infoBox),
                            R.string.cancel,
                            null,
                            true
                    );
                }
            } else if ("-".equals(infoBox.newestVersion)) {
                FancyDialogs.showMessageDialog(MainActivity.this, displayName, getString(R.string.no_update_yet));
            } else if (Objects.equals(infoBox.newestVersion, getString(R.string.unknown))) {
                FancyDialogs.showMessageDialog(MainActivity.this, 
                        displayName,
                        infoBox.fetchError != null
                                ? infoBox.fetchError
                                : getString(R.string.no_new_version, displayName)
                );
            } else if (VersionUtils.compareVersions(infoBox.newestVersion, infoBox.installedVersion) > 0) {
                FancyDialogs.showMessageDialog(MainActivity.this, 
                        displayName,
                        getString(R.string.update_available, displayName),
                        R.string.ok,
                        () -> startApkInstall(infoBox),
                        R.string.cancel,
                        null,
                        true
                );
            } else {
                FancyDialogs.showMessageDialog(MainActivity.this, displayName, getString(R.string.already_latest, displayName));
            }
        });

        box.setOnLongClickListener(v -> {
            if (infoBox.packageName == null || infoBox.packageName.trim().isEmpty()) {
                return true;
            }
            boolean isInstalledNow = getInstalledVersion(getPackageManager(), infoBox.packageName) != null;

            View dialogView = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.dialog_app_actions, null);
            TextView tvActionsTitle = dialogView.findViewById(R.id.tvAppActionsTitle);
            TextView tvActionsMessage = dialogView.findViewById(R.id.tvAppActionsMessage);
            MaterialButton btnUninstallReinstall = dialogView.findViewById(R.id.btnUninstallReinstall);
            MaterialButton btnUninstallOnly = dialogView.findViewById(R.id.btnUninstallOnly);
            MaterialButton btnActionCancel = dialogView.findViewById(R.id.btnActionCancel);

            tvActionsTitle.setText(displayName);
            if (isInstalledNow) {
                tvActionsMessage.setText(getString(R.string.app_actions_message, displayName));
            } else {
                tvActionsMessage.setText(getString(R.string.not_installed_actions, displayName));
                btnUninstallReinstall.setEnabled(false);
                btnUninstallOnly.setEnabled(false);
                btnUninstallReinstall.setAlpha(0.45f);
                btnUninstallOnly.setAlpha(0.45f);
            }

            AlertDialog appActionsDialog = new AlertDialog.Builder(MainActivity.this)
                    .setView(dialogView)
                    .create();

            btnUninstallReinstall.setOnClickListener(btn -> {
                if (infoBox.apkUrl == null || infoBox.apkUrl.isEmpty()) {
                    promptFetchBeforeReinstall(infoBox);
                    appActionsDialog.dismiss();
                    return;
                }
                pendingReinstallPackageName = infoBox.packageName;
                pendingReinstallDisplayName = infoBox.displayName;
                launchUninstallIntent(infoBox.packageName);
                appActionsDialog.dismiss();
            });

            btnUninstallOnly.setOnClickListener(btn -> {
                pendingReinstallPackageName = null;
                pendingReinstallDisplayName = null;
                launchUninstallIntent(infoBox.packageName);
                appActionsDialog.dismiss();
            });

            btnActionCancel.setOnClickListener(btn -> appActionsDialog.dismiss());
            appActionsDialog.show();
            FancyDialogs.styleWindow(appActionsDialog, 0.86f);
            return true;
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
            FancyDialogs.showMessageDialog(MainActivity.this, getString(R.string.error_title), getString(R.string.download_error, "No download URL found."));
            return;
        }

        String fileName = apkUrl.substring(apkUrl.lastIndexOf('/') + 1);
        File apkDir = getExternalFilesDir(null);
        if (apkDir == null) {
            FancyDialogs.showMessageDialog(MainActivity.this, getString(R.string.error_title), getString(R.string.download_error, "Storage unavailable."));
            return;
        }
        File apkFile = new File(apkDir, fileName);
        File tempFile = new File(apkDir, fileName + ".part");

        View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_fancy_progress, null);
        TextView tvProgressTitle = progressView.findViewById(R.id.tvProgressTitle);
        TextView tvProgressMessage = progressView.findViewById(R.id.tvProgressMessage);
        ProgressBar progressBar = progressView.findViewById(R.id.progressBarDownload);
        tvProgressTitle.setText(getString(R.string.download_title, infoBox.displayName));
        tvProgressMessage.setText(R.string.download_wait);
        progressBar.setMax(100);
        progressBar.setProgress(0);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(progressView)
                .create();
        dialog.setCancelable(false);
        dialog.show();
        FancyDialogs.styleWindow(dialog, 0.86f);

        // Download APK in a separate thread
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(apkUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "ReVancedUpdater");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.connect();
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + responseCode);
                }
                int fileLength = connection.getContentLength();

                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream output = new FileOutputStream(tempFile)) {
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
                }

                if (apkFile.exists() && !apkFile.delete()) {
                    throw new IOException("Could not replace old APK file.");
                }
                if (!tempFile.renameTo(apkFile)) {
                    throw new IOException("Could not finalize APK download.");
                }

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
                    FancyDialogs.showMessageDialog(MainActivity.this, getString(R.string.error_title), getString(R.string.download_error, e.getMessage()));
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    // Fetch the latest version and download URL from GitHub
    private void fetchNewestVersion(String appName, AppInfoBox infoBox) {
        fetchNewestVersion(appName, infoBox, null);
    }

    private void fetchNewestVersion(String appName, AppInfoBox infoBox, Runnable onComplete) {
        new Thread(() -> {
            String[] preferredAbis = getPreferredApkAbis();
            boolean preferMicrogHuaweiBuild = shouldPreferMicrogHuaweiBuild();
            LatestFetchState state = UpdateFetcher.fetchLatest(
                    MainActivity.this,
                    appName,
                    preferredAbis,
                    preferMicrogHuaweiBuild
            );
            new Handler(Looper.getMainLooper()).post(() -> {
                latestFetchStates.put(infoBox.displayName, state);
                applyFetchStateToInfoBox(infoBox, state.newestVersion, state.downloadUrl, state.fetchError);
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        }).start();
    }

    private String[] getPreferredApkAbis() {
        return developerModeManager.getPreferredApkAbis();
    }

    private boolean shouldPreferMicrogHuaweiBuild() {
        return developerModeManager.shouldPreferMicrogHuaweiBuild();
    }

    private void applyCachedFetchState(AppInfoBox infoBox) {
        LatestFetchState state = latestFetchStates.get(infoBox.displayName);
        if (state == null) {
            return;
        }
        applyFetchStateToInfoBox(infoBox, state.newestVersion, state.downloadUrl, state.fetchError);
    }

    private void applyFetchStateToInfoBox(AppInfoBox infoBox, String newestVersion, String downloadUrl, String fetchError) {
        infoBox.newestVersion = newestVersion;
        infoBox.apkUrl = downloadUrl;
        infoBox.fetchError = fetchError;
        infoBox.tvNewestVersion.setText(getString(R.string.newest_version, newestVersion));

        // Color code: green if up-to-date, red if update available
        if (!Objects.equals(newestVersion, getString(R.string.unknown)) && !"-".equals(newestVersion)) {
            if (VersionUtils.compareVersions(newestVersion, infoBox.installedVersion) == 0)
                infoBox.tvNewestVersion.setTextColor(0xFF4CAF50);
            else if (VersionUtils.compareVersions(newestVersion, infoBox.installedVersion) > 0)
                infoBox.tvNewestVersion.setTextColor(0xFFF44336);
            else
                infoBox.tvNewestVersion.setTextColor(0xFF4CAF50);
        } else {
            infoBox.tvNewestVersion.setTextColor(0xFF444444);
        }
        developerModeManager.onDataChanged(appConfigs, appInfoBoxes, latestFetchStates);
    }


    private static String getInstalledVersion(PackageManager pm, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return null;
        }
        try {
            PackageInfo pkgInfo = pm.getPackageInfo(packageName, 0);
            return pkgInfo.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void launchUninstallIntent(String packageName) {
        try {
            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
            uninstallIntent.setData(Uri.parse("package:" + packageName));
            startActivity(uninstallIntent);
        } catch (Exception e) {
            pendingReinstallPackageName = null;
            pendingReinstallDisplayName = null;
            FancyDialogs.showMessageDialog(MainActivity.this, getString(R.string.error_title), getString(R.string.uninstall_failed, e.getMessage()));
        }
    }

    private void handlePendingReinstallIfNeeded() {
        if (pendingReinstallPackageName == null || pendingReinstallDisplayName == null) {
            return;
        }
        if (getInstalledVersion(getPackageManager(), pendingReinstallPackageName) != null) {
            return;
        }

        AppInfoBox infoBox = appInfoBoxes.get(pendingReinstallDisplayName);
        pendingReinstallPackageName = null;
        pendingReinstallDisplayName = null;

        if (infoBox == null || infoBox.apkUrl == null || infoBox.apkUrl.isEmpty()) {
            FancyDialogs.showMessageDialog(MainActivity.this, getString(R.string.app_name), getString(R.string.reinstall_not_ready));
            return;
        }
        startApkInstall(infoBox);
    }

    private void promptFetchBeforeReinstall(AppInfoBox infoBox) {
        FancyDialogs.showMessageDialog(MainActivity.this, 
                infoBox.displayName,
                getString(R.string.prepare_reinstall_prompt, infoBox.displayName),
                R.string.ok,
                () -> {
                    infoBox.newestVersion = "-";
                    infoBox.apkUrl = null;
                    infoBox.fetchError = null;
                    infoBox.tvNewestVersion.setText(getString(R.string.newest_version, getString(R.string.download_wait)));
                    infoBox.tvNewestVersion.setTextColor(0xFF444444);

                    fetchNewestVersion(infoBox.displayName, infoBox, () -> {
                        if (infoBox.apkUrl == null || infoBox.apkUrl.isEmpty()) {
                            FancyDialogs.showMessageDialog(MainActivity.this, 
                                    getString(R.string.app_name),
                                    infoBox.fetchError != null ? infoBox.fetchError : getString(R.string.reinstall_not_ready)
                            );
                            return;
                        }
                        pendingReinstallPackageName = infoBox.packageName;
                        pendingReinstallDisplayName = infoBox.displayName;
                        launchUninstallIntent(infoBox.packageName);
                    });
                },
                R.string.cancel,
                null,
                true
        );
    }

    private String getDeviceArchitecture() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 && Build.SUPPORTED_ABIS[0] != null) {
            return Build.SUPPORTED_ABIS[0];
        }
        return getString(R.string.unknown);
    }

    private String getDeviceModel() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String brand = Build.BRAND == null ? "" : Build.BRAND.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();

        String prefix = !brand.isEmpty() ? brand : manufacturer;
        if (prefix.isEmpty()) {
            return model.isEmpty() ? getString(R.string.unknown) : capitalizeFirst(model);
        }
        String normalizedPrefix = capitalizeFirst(prefix);
        if (model.isEmpty()) {
            return normalizedPrefix;
        }
        String lowerModel = model.toLowerCase(Locale.ROOT);
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        if (lowerModel.startsWith(lowerPrefix)) {
            return capitalizeFirst(model);
        }
        return normalizedPrefix + " " + model;
    }

    private String capitalizeFirst(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() == 1) {
            return value.toUpperCase(Locale.ROOT);
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private void checkUpdatesForSelectedApps() {
        for (AppInfoBox infoBox : appInfoBoxes.values()) {
            infoBox.newestVersion = "-";
            infoBox.apkUrl = null;
            infoBox.fetchError = null;
            infoBox.tvNewestVersion.setText(getString(R.string.newest_version, getString(R.string.download_wait)));
            infoBox.tvNewestVersion.setTextColor(0xFF444444);
            fetchNewestVersion(infoBox.displayName, infoBox);
        }
        developerModeManager.onDataChanged(appConfigs, appInfoBoxes, latestFetchStates);
    }

    private void checkUpdatesForAllApps() {
        PackageManager pm = getPackageManager();
        for (AppConfig config : appConfigs) {
            AppInfoBox infoBox = appInfoBoxes.get(config.displayName);
            if (infoBox == null) {
                // Temporary holder for hidden apps so fetch state is cached for diagnostics.
                infoBox = new AppInfoBox(new TextView(this), getInstalledVersion(pm, config.packageName), config.displayName, config.packageName);
            } else {
                infoBox.newestVersion = "-";
                infoBox.apkUrl = null;
                infoBox.fetchError = null;
                infoBox.tvNewestVersion.setText(getString(R.string.newest_version, getString(R.string.download_wait)));
                infoBox.tvNewestVersion.setTextColor(0xFF444444);
            }
            fetchNewestVersion(config.displayName, infoBox);
        }
        developerModeManager.onDataChanged(appConfigs, appInfoBoxes, latestFetchStates);
    }

    private void showAppInfoDialog() {
        String versionName = getString(R.string.unknown);
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_app_info, null);
        TextView appName = content.findViewById(R.id.tv_app_name);
        TextView appVersion = content.findViewById(R.id.tv_app_version);
        TextView appDescription = content.findViewById(R.id.tv_app_description);
        TextView appDeveloper = content.findViewById(R.id.tv_app_developer);
        Button openEmail = content.findViewById(R.id.btn_open_email);
        Button openGithub = content.findViewById(R.id.btn_open_github);
        Button openGithubProfile = content.findViewById(R.id.btn_open_github_profile);
        Button openCoffee = content.findViewById(R.id.btn_open_coffee);

        appName.setText(R.string.app_info_name);
        appVersion.setText(getString(R.string.app_info_version, versionName));
        appDescription.setText(R.string.app_info_description);

        String developerLabel = getString(R.string.app_info_developer_label);
        String developerName = getString(R.string.app_info_developer_name);
        SpannableString developerText = new SpannableString(developerLabel + " " + developerName);
        int labelEnd = developerLabel.length();
        developerText.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.ui_gray)),
                0,
                labelEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        developerText.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                labelEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        appDeveloper.setText(developerText);

        openEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:sichler.daniel@gmail.com"));
            startActivity(intent);
        });

        openGithub.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://linktr.ee/darexsh"));
            startActivity(intent);
        });

        openGithubProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Darexsh"));
            startActivity(intent);
        });

        openCoffee.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/darexsh"));
            startActivity(intent);
        });

        FancyDialogs.showContentDialog(MainActivity.this, getString(R.string.app_info_title), content, R.string.dialog_ok);
    }
}

