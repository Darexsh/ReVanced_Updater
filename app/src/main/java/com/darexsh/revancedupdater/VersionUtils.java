package com.darexsh.revancedupdater;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VersionUtils {

    private VersionUtils() {
    }

    static int compareVersions(String remote, String local) {
        if (remote == null || local == null) {
            return remote == null ? (local == null ? 0 : -1) : 1;
        }

        String[] remoteParts = remote.split("\\.");
        String[] localParts = local.split("\\.");
        int max = Math.max(remoteParts.length, localParts.length);

        for (int i = 0; i < max; i++) {
            int r = i < remoteParts.length ? parseVersionPart(remoteParts[i]) : 0;
            int l = i < localParts.length ? parseVersionPart(localParts[i]) : 0;
            if (r != l) {
                return Integer.compare(r, l);
            }
        }
        return 0;
    }

    private static int parseVersionPart(String input) {
        Matcher matcher = Pattern.compile("(\\d+)").matcher(input);
        if (matcher.find()) {
            try {
                return Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
