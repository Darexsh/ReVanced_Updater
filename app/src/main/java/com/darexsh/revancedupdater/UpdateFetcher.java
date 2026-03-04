package com.darexsh.revancedupdater;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class UpdateFetcher {

    static LatestFetchState fetchLatest(
            Context context,
            String appName,
            String[] preferredAbis,
            boolean preferMicrogHuaweiBuild
    ) {
        String newestVersion = context.getString(R.string.unknown);
        String downloadUrl = null;
        String fetchError = null;
        HttpURLConnection connection = null;

        try {
            connection = getHttpURLConnection(appName);
            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                JSONArray releases = new JSONArray(sb.toString());

                outer:
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.getJSONObject(i);
                    JSONArray assets = release.getJSONArray("assets");

                    if ("Youtube Music ReVanced".equals(appName)) {
                        ApkMatch match = findBestAbiAssetInRelease(
                                assets,
                                "music-revanced",
                                Pattern.compile("music-revanced-v([0-9.]+)-(arm64-v8a|arm-v7a)\\.apk"),
                                preferredAbis
                        );
                        if (match != null) {
                            newestVersion = match.version;
                            downloadUrl = match.downloadUrl;
                            break;
                        }
                        continue;
                    }

                    if ("Youtube Music Morphe".equals(appName)) {
                        ApkMatch match = findBestAbiAssetInRelease(
                                assets,
                                "music-morphe",
                                Pattern.compile("music-morphe-v([0-9.]+)-(arm64-v8a|arm-v7a)\\.apk"),
                                preferredAbis
                        );
                        if (match != null) {
                            newestVersion = match.version;
                            downloadUrl = match.downloadUrl;
                            break;
                        }
                        continue;
                    }

                    if ("Google Photos ReVanced".equals(appName)) {
                        ApkMatch match = findBestAbiAssetInRelease(
                                assets,
                                "googlephotos-revanced",
                                Pattern.compile("googlephotos-revanced-v([0-9.]+)-(arm64-v8a|arm-v7a)\\.apk"),
                                preferredAbis
                        );
                        if (match != null) {
                            newestVersion = match.version;
                            downloadUrl = match.downloadUrl;
                            break;
                        }
                        continue;
                    }

                    if ("microG".equals(appName)) {
                        String microgUrl = findBestMicrogAssetInRelease(assets, preferMicrogHuaweiBuild);
                        if (microgUrl != null) {
                            String tag = release.getString("tag_name");
                            newestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
                            downloadUrl = microgUrl;
                            break;
                        }
                        continue;
                    }

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
                            case "Google Photos ReVanced":
                                break;
                            case "TikTok ReVanced":
                                if (name.startsWith("tiktok-revanced") && name.endsWith("-all.apk"))
                                    pattern = Pattern.compile("tiktok-revanced-v([0-9.]+)-all\\.apk");
                                break;
                            case "Spotify ReVanced":
                                if (name.startsWith("spotify-revanced") && name.endsWith("-all.apk"))
                                    pattern = Pattern.compile("spotify-revanced-v([0-9.]+)-all\\.apk");
                                break;
                            case "YouTube Morphe":
                                if (name.startsWith("youtube-morphe") && name.endsWith("-all.apk"))
                                    pattern = Pattern.compile("youtube-morphe-v([0-9.]+)-all\\.apk");
                                break;
                            case "Youtube Music Morphe":
                                break;
                            case "Reddit Morphe":
                                if (name.startsWith("reddit-morphe") && name.endsWith("-all.apk"))
                                    pattern = Pattern.compile("reddit-morphe-v([0-9.]+)-all\\.apk");
                                break;
                            case "microG":
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

            } else if (responseCode == 403) {
                newestVersion = context.getString(R.string.unknown);
                fetchError = context.getString(R.string.fetch_error_rate_limit);
            } else {
                newestVersion = context.getString(R.string.unknown);
                fetchError = context.getString(R.string.fetch_error_http, responseCode);
            }

        } catch (Exception e) {
            newestVersion = context.getString(R.string.unknown);
            fetchError = context.getString(R.string.fetch_error_network);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return new LatestFetchState(newestVersion, downloadUrl, fetchError);
    }

    private static ApkMatch findBestAbiAssetInRelease(JSONArray assets, String prefix, Pattern pattern, String[] preferredAbis) throws Exception {
        Map<String, ApkMatch> matchesByAbi = new HashMap<>();
        for (int j = 0; j < assets.length(); j++) {
            JSONObject asset = assets.getJSONObject(j);
            String name = asset.getString("name").toLowerCase();
            if (!name.startsWith(prefix) || !name.endsWith(".apk")) {
                continue;
            }
            Matcher matcher = pattern.matcher(name);
            if (!matcher.find()) {
                continue;
            }
            String version = matcher.group(1);
            String abi = matcher.group(2);
            String url = asset.getString("browser_download_url");
            if (!matchesByAbi.containsKey(abi)) {
                matchesByAbi.put(abi, new ApkMatch(version, url));
            }
        }

        for (String abi : preferredAbis) {
            ApkMatch match = matchesByAbi.get(abi);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static String findBestMicrogAssetInRelease(JSONArray assets, boolean preferHuaweiBuild) throws Exception {
        String preferred = null;
        String fallback = null;

        for (int j = 0; j < assets.length(); j++) {
            JSONObject asset = assets.getJSONObject(j);
            String name = asset.getString("name").toLowerCase();
            if (!name.endsWith("-signed.apk")) {
                continue;
            }
            boolean isHuaweiVariant = name.contains("-hw");
            String url = asset.getString("browser_download_url");

            if (preferHuaweiBuild) {
                if (isHuaweiVariant && preferred == null) {
                    preferred = url;
                } else if (!isHuaweiVariant && fallback == null) {
                    fallback = url;
                }
            } else {
                if (!isHuaweiVariant && preferred == null) {
                    preferred = url;
                } else if (isHuaweiVariant && fallback == null) {
                    fallback = url;
                }
            }
        }

        return preferred != null ? preferred : fallback;
    }

    private static HttpURLConnection getHttpURLConnection(String appName) throws Exception {
        String apiUrl = "https://api.github.com/repos/j-hc/revanced-magisk-module/releases";
        if ("microG".equals(appName)) {
            apiUrl = "https://api.github.com/repos/ReVanced/GmsCore/releases";
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "ReVancedUpdater");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        return connection;
    }

    private static class ApkMatch {
        final String version;
        final String downloadUrl;

        ApkMatch(String version, String downloadUrl) {
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }
}
