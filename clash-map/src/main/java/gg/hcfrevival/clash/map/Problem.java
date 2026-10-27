package gg.hcfrevival.clash.map;

import java.util.Objects;

/**
 * One thing wrong with a map.
 *
 * @param severity {@link Severity#ERROR} stops the map from loading, {@link Severity#WARNING} does not
 * @param path     where in map.yml, for example {@code teams[1].base}; empty for the whole file
 * @param message  a sentence a map maker can act on
 */
public record Problem(Severity severity, String path, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }

    public Problem {
        Objects.requireNonNull(severity, "severity");
        path = path == null ? "" : path;
        Objects.requireNonNull(message, "message");
    }

    public static Problem error(String path, String message) {
        return new Problem(Severity.ERROR, path, message);
    }

    public static Problem warning(String path, String message) {
        return new Problem(Severity.WARNING, path, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        String where = path.isEmpty() ? "" : path + ": ";
        return severity + " " + where + message;
    }
}
