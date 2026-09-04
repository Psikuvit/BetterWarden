package me.psikuvit.betterWarden.core.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern SEGMENT = Pattern.compile("(\\d+)([smhdw])");

    private DurationParser() {
    }

    /** Parses "7d12h", "2h30m", "perm" (null = permanent). Throws IllegalArgumentException otherwise. */
    public static Duration parse(String input) {
        String trimmed = input.trim().toLowerCase();
        if (trimmed.equals("perm") || trimmed.equals("permanent") || trimmed.equals("-1")) {
            return null;
        }
        Matcher matcher = SEGMENT.matcher(trimmed);
        long totalSeconds = 0;
        int matched = 0;
        while (matcher.find()) {
            matched++;
            long value = Long.parseLong(matcher.group(1));
            totalSeconds += switch (matcher.group(2)) {
                case "s" -> value;
                case "m" -> value * 60;
                case "h" -> value * 3600;
                case "d" -> value * 86400;
                case "w" -> value * 604800;
                default -> 0;
            };
        }
        if (matched == 0) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
        return Duration.ofSeconds(totalSeconds);
    }
}
