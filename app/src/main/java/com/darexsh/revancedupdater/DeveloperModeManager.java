package com.darexsh.revancedupdater;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class DeveloperModeManager {

    private static final String KEY_DEV_EMULATED_BRAND = "devEmulatedBrand";
    private static final String KEY_DEV_EMULATED_ARCH = "devEmulatedArch";

    private final AppCompatActivity activity;
    private final SharedPreferences prefs;

    private String emulatedBrand;
    private String emulatedArch;
    private TextView activeDiagnosticsView;

    DeveloperModeManager(AppCompatActivity activity, String prefsName) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(prefsName, AppCompatActivity.MODE_PRIVATE);
        this.emulatedBrand = prefs.getString(KEY_DEV_EMULATED_BRAND, "auto");
        this.emulatedArch = prefs.getString(KEY_DEV_EMULATED_ARCH, "auto");
    }

    void showDialog(
            AppConfig[] appConfigs,
            Map<String, AppInfoBox> appInfoBoxes,
            Map<String, LatestFetchState> latestFetchStates,
            Runnable onCheckUpdatesAll
    ) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_developer_mode, null);
        TextView tvDevDiag = dialogView.findViewById(R.id.tvDeveloperDiagnosticsContent);
        Spinner spinnerBrand = dialogView.findViewById(R.id.spinnerDeveloperBrand);
        Spinner spinnerArch = dialogView.findViewById(R.id.spinnerDeveloperArch);
        MaterialButton btnDevCheckUpdates = dialogView.findViewById(R.id.btnDeveloperCheckUpdates);
        MaterialButton btnDevClose = dialogView.findViewById(R.id.btnDeveloperClose);

        final String[] brandValues = {"auto", "samsung", "honor", "huawei", "google"};
        final String[] brandLabels = {
                activity.getString(R.string.developer_emulation_auto),
                activity.getString(R.string.developer_brand_samsung),
                activity.getString(R.string.developer_brand_honor),
                activity.getString(R.string.developer_brand_huawei),
                activity.getString(R.string.developer_brand_google)
        };
        ArrayAdapter<String> brandAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, brandLabels);
        brandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBrand.setAdapter(brandAdapter);
        spinnerBrand.setSelection(indexOfValue(brandValues, emulatedBrand));

        final String[] archValues = {"auto", "arm64-v8a", "arm-v7a"};
        final String[] archLabels = {
                activity.getString(R.string.developer_emulation_auto),
                activity.getString(R.string.developer_arch_arm64),
                activity.getString(R.string.developer_arch_armv7)
        };
        ArrayAdapter<String> archAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, archLabels);
        archAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerArch.setAdapter(archAdapter);
        spinnerArch.setSelection(indexOfValue(archValues, emulatedArch));

        spinnerBrand.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                emulatedBrand = brandValues[position];
                persist();
                tvDevDiag.setText(buildDiagnosticsText(appConfigs, appInfoBoxes, latestFetchStates));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinnerArch.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                emulatedArch = archValues[position];
                persist();
                tvDevDiag.setText(buildDiagnosticsText(appConfigs, appInfoBoxes, latestFetchStates));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        activeDiagnosticsView = tvDevDiag;
        tvDevDiag.setText(buildDiagnosticsText(appConfigs, appInfoBoxes, latestFetchStates));

        AlertDialog developerDialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();
        developerDialog.setOnDismissListener(d -> activeDiagnosticsView = null);

        btnDevCheckUpdates.setOnClickListener(v -> {
            onCheckUpdatesAll.run();
            tvDevDiag.setText(buildDiagnosticsText(appConfigs, appInfoBoxes, latestFetchStates));
        });
        btnDevClose.setOnClickListener(v -> developerDialog.dismiss());

        developerDialog.show();
        FancyDialogs.styleWindow(developerDialog, 0.94f);
    }

    void onDataChanged(AppConfig[] appConfigs, Map<String, AppInfoBox> appInfoBoxes, Map<String, LatestFetchState> latestFetchStates) {
        if (activeDiagnosticsView != null) {
            activeDiagnosticsView.setText(buildDiagnosticsText(appConfigs, appInfoBoxes, latestFetchStates));
        }
    }

    String[] getPreferredApkAbis() {
        if (emulatedArch != null && !"auto".equalsIgnoreCase(emulatedArch)) {
            return new String[]{emulatedArch};
        }
        Set<String> preferred = new LinkedHashSet<>();
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        if (supportedAbis != null) {
            for (String abi : supportedAbis) {
                if (abi == null) continue;
                String lower = abi.toLowerCase(Locale.ROOT);
                if (lower.contains("arm64")) {
                    preferred.add("arm64-v8a");
                } else if (lower.contains("armeabi") || lower.contains("armv7") || lower.contains("arm-v7a")) {
                    preferred.add("arm-v7a");
                }
            }
        }
        if (preferred.isEmpty()) {
            preferred.add("arm64-v8a");
            preferred.add("arm-v7a");
        }
        return preferred.toArray(new String[0]);
    }

    boolean shouldPreferMicrogHuaweiBuild() {
        String effectiveBrand = getEffectiveBrand().toLowerCase(Locale.ROOT);
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        return effectiveBrand.contains("huawei")
                || manufacturer.contains("huawei")
                || effectiveBrand.contains("honor")
                || manufacturer.contains("honor");
    }

    private String buildDiagnosticsText(
            AppConfig[] appConfigs,
            Map<String, AppInfoBox> appInfoBoxes,
            Map<String, LatestFetchState> latestFetchStates
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("[DEVICE]").append('\n');
        sb.append(activity.getString(R.string.developer_diag_emulated_brand, getEmulatedBrandLabel())).append('\n');
        sb.append(activity.getString(R.string.developer_diag_emulated_arch, getEmulatedArchLabel())).append('\n');
        sb.append(activity.getString(R.string.developer_diag_abis, formatPreferredAbis())).append('\n');
        sb.append(activity.getString(
                R.string.developer_diag_microg,
                activity.getString(shouldPreferMicrogHuaweiBuild()
                        ? R.string.developer_diag_microg_huawei
                        : R.string.developer_diag_microg_standard)
        ));
        sb.append('\n').append('\n');
        sb.append("[APPS]").append('\n');

        for (AppConfig config : appConfigs) {
            AppInfoBox info = appInfoBoxes.get(config.displayName);
            LatestFetchState state = latestFetchStates.get(config.displayName);
            String version = "-";
            String urlValue = activity.getString(R.string.developer_diag_not_checked);
            if (info != null) {
                version = info.newestVersion != null ? info.newestVersion : "-";
                if (info.apkUrl != null && !info.apkUrl.isEmpty()) {
                    urlValue = info.apkUrl;
                } else if (info.newestVersion != null && !"-".equals(info.newestVersion)) {
                    urlValue = "-";
                }
            } else if (state != null) {
                version = state.newestVersion != null ? state.newestVersion : "-";
                if (state.downloadUrl != null && !state.downloadUrl.isEmpty()) {
                    urlValue = state.downloadUrl;
                } else if (state.newestVersion != null && !"-".equals(state.newestVersion)) {
                    urlValue = "-";
                }
            }
            sb.append("- ")
                    .append(config.displayName)
                    .append('\n')
                    .append("    v=")
                    .append(version)
                    .append('\n')
                    .append("    url=")
                    .append(urlValue)
                    .append('\n');
        }
        return sb.toString();
    }

    private void persist() {
        prefs.edit()
                .putString(KEY_DEV_EMULATED_BRAND, emulatedBrand)
                .putString(KEY_DEV_EMULATED_ARCH, emulatedArch)
                .apply();
    }

    private int indexOfValue(String[] values, String selected) {
        if (selected == null) return 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(selected)) return i;
        }
        return 0;
    }

    private String getEffectiveBrand() {
        if (emulatedBrand != null && !"auto".equalsIgnoreCase(emulatedBrand)) {
            return emulatedBrand;
        }
        return Build.BRAND == null ? "" : Build.BRAND;
    }

    private String formatPreferredAbis() {
        String[] abis = getPreferredApkAbis();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < abis.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(abis[i]);
        }
        return sb.toString();
    }

    private String getEmulatedBrandLabel() {
        if ("samsung".equalsIgnoreCase(emulatedBrand)) return activity.getString(R.string.developer_brand_samsung);
        if ("honor".equalsIgnoreCase(emulatedBrand)) return activity.getString(R.string.developer_brand_honor);
        if ("huawei".equalsIgnoreCase(emulatedBrand)) return activity.getString(R.string.developer_brand_huawei);
        if ("google".equalsIgnoreCase(emulatedBrand)) return activity.getString(R.string.developer_brand_google);
        String realBrand = getEffectiveBrand();
        if (realBrand == null || realBrand.trim().isEmpty()) {
            realBrand = activity.getString(R.string.unknown);
        } else {
            realBrand = capitalizeFirst(realBrand.trim());
        }
        return realBrand;
    }

    private String getEmulatedArchLabel() {
        if ("arm64-v8a".equalsIgnoreCase(emulatedArch)) return activity.getString(R.string.developer_arch_arm64);
        if ("arm-v7a".equalsIgnoreCase(emulatedArch)) return activity.getString(R.string.developer_arch_armv7);
        return getDeviceArchitecture();
    }

    private String getDeviceArchitecture() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 && Build.SUPPORTED_ABIS[0] != null) {
            return Build.SUPPORTED_ABIS[0];
        }
        return activity.getString(R.string.unknown);
    }

    private static String capitalizeFirst(String value) {
        if (value == null || value.isEmpty()) return value;
        if (value.length() == 1) return value.toUpperCase(Locale.ROOT);
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
