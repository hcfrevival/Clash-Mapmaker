package gg.hcfrevival.clash.map;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * One YAML mapping being read. Remembers which keys were used so the rest can be
 * reported as unknown, and turns type mistakes into {@link Problem}s instead of exceptions.
 */
final class Node {
    private final Map<String, Object> values;
    private final String path;
    private final List<Problem> problems;
    private final Set<String> used = new HashSet<>();

    Node(Map<?, ?> values, String path, List<Problem> problems) {
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(String.valueOf(key), value));
        this.values = copy;
        this.path = path;
        this.problems = problems;
    }

    String path() {
        return path;
    }

    String path(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    List<Problem> problems() {
        return problems;
    }

    boolean has(String key) {
        return values.containsKey(key);
    }

    Object raw(String key) {
        used.add(key);
        return values.get(key);
    }

    Map<String, Object> values() {
        return values;
    }

    void error(String key, String message) {
        problems.add(Problem.error(key == null ? path : path(key), message));
    }

    String string(String key) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            error(key, "should be a single value, not a list or section.");
            return null;
        }
        return String.valueOf(value);
    }

    Boolean bool(String key) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        error(key, "should be true or false.");
        return null;
    }

    Integer integer(String key) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer || value instanceof Long) {
            long number = ((Number) value).longValue();
            if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                return (int) number;
            }
        }
        error(key, "should be a whole number.");
        return null;
    }

    Long longValue(String key) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        error(key, "should be a whole number.");
        return null;
    }

    Double decimal(String key) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number && !(value instanceof java.math.BigInteger)) {
            return number.doubleValue();
        }
        error(key, "should be a number.");
        return null;
    }

    /** {@code 60%} or {@code 60} as a whole percent. */
    Integer percent(String key) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.endsWith("%")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            error(key, "should be a percent such as 60%.");
            return null;
        }
    }

    Duration duration(String key) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        Optional<Duration> parsed = value instanceof Map<?, ?> || value instanceof List<?>
                ? Optional.empty()
                : Durations.parse(String.valueOf(value));
        if (parsed.isEmpty()) {
            error(key, "should be a duration such as 90s, 5m or 1h30m.");
            return null;
        }
        return parsed.get();
    }

    Optional<Node> section(String key) {
        Object value = raw(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Map<?, ?> map) {
            return Optional.of(new Node(map, path(key), problems));
        }
        error(key, "should be a section (key: value pairs).");
        return Optional.empty();
    }

    /** Reads a list; each element gets its own path such as {@code teams[2]}. */
    <T> List<T> list(String key, BiFunction<Object, String, T> element) {
        Object value = raw(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            error(key, "should be a list.");
            return List.of();
        }
        List<T> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            T parsed = element.apply(list.get(i), path(key) + "[" + i + "]");
            if (parsed != null) {
                out.add(parsed);
            }
        }
        return out;
    }

    /** Reads a list of mapping elements. */
    <T> List<T> sections(String key, java.util.function.Function<Node, T> element) {
        return list(key, (value, elementPath) -> {
            if (value instanceof Map<?, ?> map) {
                Node node = new Node(map, elementPath, problems);
                T parsed = element.apply(node);
                node.finish();
                return parsed;
            }
            problems.add(Problem.error(elementPath, "should be a section (key: value pairs)."));
            return null;
        });
    }

    List<String> strings(String key) {
        return list(key, (value, elementPath) -> {
            if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
                problems.add(Problem.error(elementPath, "should be a single value."));
                return null;
            }
            return String.valueOf(value);
        });
    }

    /** Warns about every key nobody read, which is almost always a typo. */
    void finish() {
        for (String key : values.keySet()) {
            if (!used.contains(key)) {
                problems.add(Problem.warning(path(key), "unknown key, it will be ignored. Check the spelling."));
            }
        }
    }
}
