package gg.hcfrevival.clash.map;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Color names map makers can use. */
public final class MapColors {
    /** The 16 Minecraft text colors and their RGB values, as MiniMessage names them. */
    public static final Map<String, Integer> NAMED;

    /** The 16 dye colors, used by wool. */
    public static final List<String> DYES = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    static {
        Map<String, Integer> named = new LinkedHashMap<>();
        named.put("black", 0x000000);
        named.put("dark_blue", 0x0000aa);
        named.put("dark_green", 0x00aa00);
        named.put("dark_aqua", 0x00aaaa);
        named.put("dark_red", 0xaa0000);
        named.put("dark_purple", 0xaa00aa);
        named.put("gold", 0xffaa00);
        named.put("gray", 0xaaaaaa);
        named.put("dark_gray", 0x555555);
        named.put("blue", 0x5555ff);
        named.put("green", 0x55ff55);
        named.put("aqua", 0x55ffff);
        named.put("red", 0xff5555);
        named.put("light_purple", 0xff55ff);
        named.put("yellow", 0xffff55);
        named.put("white", 0xffffff);
        NAMED = java.util.Collections.unmodifiableMap(named);
    }

    private MapColors() {
    }

    /** A named color such as {@code red} or a hex color such as {@code #ff5555}. */
    public static boolean isColor(String value) {
        return value != null && (NAMED.containsKey(normalize(value)) || HEX.matcher(value).matches());
    }

    public static boolean isDye(String value) {
        return value != null && DYES.contains(normalize(value));
    }

    /**
     * The named color closest to {@code value}. Scoreboard teams (nametag colors) only take
     * named colors, so a hex team color is shown with its nearest one there.
     */
    public static String nearestNamed(String value) {
        if (!isColor(value)) {
            throw new IllegalArgumentException("not a color: " + value);
        }
        String name = normalize(value);
        if (NAMED.containsKey(name)) {
            return name;
        }
        int rgb = Integer.parseInt(value.substring(1), 16);
        String best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : NAMED.entrySet()) {
            long distance = distance(rgb, entry.getValue());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static long distance(int a, int b) {
        long dr = ((a >> 16) & 0xff) - ((b >> 16) & 0xff);
        long dg = ((a >> 8) & 0xff) - ((b >> 8) & 0xff);
        long db = (a & 0xff) - (b & 0xff);
        return dr * dr + dg * dg + db * db;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
