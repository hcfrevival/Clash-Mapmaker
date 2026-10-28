package gg.hcfrevival.clash.mapmaker;

import gg.hcfrevival.clash.map.MapColors;
import gg.hcfrevival.clash.map.MapManifest.Pose;
import gg.hcfrevival.clash.map.Problem;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Color;
import org.bukkit.Location;

import java.util.Locale;

/** Chat output and the small conversions every command needs. */
final class Msg {
    static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String PREFIX = "<gray>[<gold>cmap</gold>]</gray> ";

    private Msg() {
    }

    static void send(Audience to, String mini, TagResolver... resolvers) {
        to.sendMessage(MINI.deserialize(PREFIX + mini, resolvers));
    }

    /** Plain text, shown green; nothing in it is parsed as a tag. */
    static void ok(Audience to, String text) {
        send(to, "<green><text></green>", Placeholder.unparsed("text", text));
    }

    static void fail(Audience to, String text) {
        send(to, "<red><text></red>", Placeholder.unparsed("text", text));
    }

    static void problem(Audience to, Problem problem) {
        send(to, (problem.isError() ? "<red>" : "<yellow>") + problem.severity() + " <white><path></white>: <message>",
                Placeholder.unparsed("path", problem.path()), Placeholder.unparsed("message", problem.message()));
    }

    /** A pose players can read back: block-centred x and z, y to the half block, whole-degree yaw, level pitch. */
    static Pose pose(Location at) {
        float yaw = Math.round(at.getYaw());
        yaw = ((yaw + 180) % 360 + 360) % 360 - 180;
        return new Pose(Math.floor(at.getX()) + 0.5, Math.round(at.getY() * 2) / 2.0, Math.floor(at.getZ()) + 0.5, yaw, 0f);
    }

    static String at(Pose pose) {
        return pose.x() + ", " + pose.y() + ", " + pose.z();
    }

    /** A named text color or {@code #rrggbb} as a Bukkit color; white when unset or unknown. */
    static Color rgb(String color) {
        if (color == null) {
            return Color.WHITE;
        }
        String key = color.trim().toLowerCase(Locale.ROOT);
        Integer named = MapColors.NAMED.get(key);
        if (named != null) {
            return Color.fromRGB(named);
        }
        return key.matches("#[0-9a-f]{6}") ? Color.fromRGB(Integer.parseInt(key.substring(1), 16)) : Color.WHITE;
    }

    /** {@code red-base} to {@code Red Base}. */
    static String capitalise(String id) {
        StringBuilder out = new StringBuilder();
        for (String part : id.split("[-_]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
