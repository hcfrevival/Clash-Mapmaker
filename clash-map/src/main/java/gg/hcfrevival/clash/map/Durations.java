package gg.hcfrevival.clash.map;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Durations as map makers write them: {@code 90s}, {@code 5m}, {@code 1h30m}.
 * A bare number means seconds.
 */
public final class Durations {
    private static final Pattern FORMAT = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    private Durations() {
    }

    public static Optional<Duration> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String value = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return Optional.of(Duration.ofSeconds(Long.parseLong(value)));
        }
        Matcher matcher = FORMAT.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long hours = group(matcher, 1);
        long minutes = group(matcher, 2);
        long seconds = group(matcher, 3);
        return Optional.of(Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds));
    }

    /** The shortest text that {@link #parse} reads back to the same duration (whole seconds). */
    public static String format(Duration duration) {
        long total = duration.toSeconds();
        if (total == 0) {
            return "0s";
        }
        StringBuilder out = new StringBuilder();
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        if (hours > 0) {
            out.append(hours).append('h');
        }
        if (minutes > 0) {
            out.append(minutes).append('m');
        }
        if (seconds > 0) {
            out.append(seconds).append('s');
        }
        return out.toString();
    }

    private static long group(Matcher matcher, int group) {
        String value = matcher.group(group);
        return value == null ? 0 : Long.parseLong(value);
    }
}
