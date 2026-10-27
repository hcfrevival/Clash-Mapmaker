package gg.hcfrevival.clash.map;

import gg.hcfrevival.clash.map.MapManifest.Author;
import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import gg.hcfrevival.clash.map.MapManifest.Bunkers;
import gg.hcfrevival.clash.map.MapManifest.ControlPoint;
import gg.hcfrevival.clash.map.MapManifest.ControlPointVictory;
import gg.hcfrevival.clash.map.MapManifest.Core;
import gg.hcfrevival.clash.map.MapManifest.Cuboid;
import gg.hcfrevival.clash.map.MapManifest.Destroyable;
import gg.hcfrevival.clash.map.MapManifest.Goal;
import gg.hcfrevival.clash.map.MapManifest.GoalsVictory;
import gg.hcfrevival.clash.map.MapManifest.Info;
import gg.hcfrevival.clash.map.MapManifest.Item;
import gg.hcfrevival.clash.map.MapManifest.Kit;
import gg.hcfrevival.clash.map.MapManifest.LastTeamStanding;
import gg.hcfrevival.clash.map.MapManifest.Pose;
import gg.hcfrevival.clash.map.MapManifest.Region;
import gg.hcfrevival.clash.map.MapManifest.Respawn;
import gg.hcfrevival.clash.map.MapManifest.Team;
import gg.hcfrevival.clash.map.MapManifest.TimeLimitVictory;
import gg.hcfrevival.clash.map.MapManifest.VictoryCondition;
import gg.hcfrevival.clash.map.MapManifest.Wool;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a {@link MapManifest} back to map.yml.
 *
 * <p>Keys always come out in the same order and small things (positions, teams, regions,
 * items) stay on one line, so a saved map is easy to read and its diffs stay small.
 * Values the map left out are not written.
 */
public final class MapManifestWriter {
    private static final String HEADER = """
            # A Clash map. Format reference:
            # https://github.com/hcfrevival/Clash-Mapmaker/blob/main/docs/MAP_FORMAT.md
            """;

    /** Top-level sections that get a blank line above them. */
    private static final java.util.Set<String> SPACED = java.util.Set.of(
            "world", "teams", "spawns", "kits", "kit-chests", "classes", "regions", "goals",
            "respawn", "bunkers", "time-limit", "victory", "variants");

    public String write(MapManifest manifest) {
        StringBuilder out = new StringBuilder(HEADER);
        for (String line : yaml().dump(toMap(manifest)).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && !line.startsWith(" ") && SPACED.contains(line.substring(0, colon))) {
                out.append('\n');
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    public void write(MapManifest manifest, Path file) throws IOException {
        Files.writeString(file, write(manifest), StandardCharsets.UTF_8);
    }

    /** The manifest as plain maps and lists, in the order map.yml uses. */
    public Map<String, Object> toMap(MapManifest manifest) {
        Map<String, Object> root = new LinkedHashMap<>();
        Info info = manifest.info();
        root.put("proto", info.proto());
        put(root, "name", info.name());
        put(root, "slug", info.slug());
        put(root, "version", info.version());
        put(root, "objective", info.objective());
        if (!info.authors().isEmpty()) {
            root.put("authors", list(info.authors(), this::author));
        }
        put(root, "gamemode", info.gamemode() == null ? null : info.gamemode().id());
        put(root, "max-players", info.maxPlayers());
        if (info.join().late() != null) {
            root.put("join", flow("late", info.join().late()));
        }

        Map<String, Object> world = new LinkedHashMap<>();
        put(world, "spawn", pose(manifest.world().spawn()));
        put(world, "bounds", manifest.world().bounds());
        put(world, "time", manifest.world().time());
        put(world, "weather", manifest.world().weather());
        if (!manifest.world().gamerules().isEmpty()) {
            world.put("gamerules", flow(manifest.world().gamerules()));
        }
        putSection(root, "world", world);

        if (!manifest.teams().isEmpty()) {
            root.put("teams", list(manifest.teams(), this::team));
        }
        if (!manifest.spawns().isEmpty()) {
            root.put("spawns", list(manifest.spawns(), group -> {
                Map<String, Object> out = new LinkedHashMap<>();
                put(out, "team", group.team());
                out.put("points", list(group.points(), this::pose));
                return out;
            }));
        }
        if (!manifest.kits().isEmpty()) {
            Map<String, Object> kits = new LinkedHashMap<>();
            manifest.kits().forEach((name, kit) -> kits.put(name, kit(kit)));
            root.put("kits", kits);
        }
        if (!manifest.kitChests().isEmpty()) {
            root.put("kit-chests", list(manifest.kitChests(), chest -> {
                Map<String, Object> out = new LinkedHashMap<>();
                put(out, "region", chest.region());
                out.put("items", list(chest.items(), this::item));
                return out;
            }));
        }
        if (!manifest.classes().allowed().isEmpty() || !manifest.classes().caps().isEmpty()) {
            Map<String, Object> classes = new LinkedHashMap<>();
            classes.put("allowed", flowList(manifest.classes().allowed()));
            if (!manifest.classes().caps().isEmpty()) {
                classes.put("caps", flow(manifest.classes().caps()));
            }
            root.put("classes", classes);
        }
        if (!manifest.regions().isEmpty()) {
            root.put("regions", list(manifest.regions(), this::region));
        }
        if (!manifest.goals().isEmpty()) {
            root.put("goals", list(manifest.goals(), this::goal));
        }
        putSection(root, "respawn", respawn(manifest.respawn()));
        if (manifest.bunkers() != null) {
            root.put("bunkers", bunkers(manifest.bunkers()));
        }
        if (manifest.timeLimit() != null) {
            Map<String, Object> limit = flow();
            put(limit, "duration", duration(manifest.timeLimit().duration()));
            put(limit, "result", manifest.timeLimit().result());
            put(limit, "overtime", duration(manifest.timeLimit().overtime()));
            root.put("time-limit", limit);
        }
        if (manifest.victory() != null) {
            root.put("victory", flowList(manifest.victory().stream().map(this::victory).toList()));
        }
        if (!manifest.variants().isEmpty()) {
            Map<String, Object> variants = new LinkedHashMap<>();
            manifest.variants().forEach((id, variant) -> {
                Map<String, Object> out = new LinkedHashMap<>();
                put(out, "name", variant.name());
                out.putAll(variant.overrides());
                variants.put(id, out);
            });
            root.put("variants", variants);
        }
        return root;
    }

    // ---- sections ---------------------------------------------------------

    private Object author(Author author) {
        Map<String, Object> out = flow();
        put(out, "uuid", author.uuid());
        put(out, "name", author.name());
        put(out, "contribution", author.contribution());
        return out;
    }

    private Object team(Team team) {
        Map<String, Object> out = flow();
        put(out, "id", team.id());
        put(out, "name", team.name());
        put(out, "display-name", team.displayName());
        put(out, "color", team.color());
        put(out, "min", team.min());
        put(out, "max", team.max());
        put(out, "overfill", team.overfill());
        put(out, "role", team.role());
        put(out, "base", team.base());
        return out;
    }

    private Object kit(Kit kit) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!kit.armor().isEmpty()) {
            Map<String, Object> armor = new LinkedHashMap<>();
            for (String slot : Kit.ARMOR_SLOTS) {
                Item item = kit.armor().get(slot);
                if (item != null) {
                    armor.put(slot, item(item));
                }
            }
            out.put("armor", armor);
        }
        if (!kit.items().isEmpty()) {
            out.put("items", list(kit.items(), this::item));
        }
        if (kit.offhand() != null) {
            out.put("offhand", item(kit.offhand()));
        }
        return out;
    }

    private Object item(Item item) {
        Map<String, Object> out = flow();
        put(out, "slot", item.slot());
        put(out, "material", item.material());
        put(out, "amount", item.amount());
        put(out, "name", item.name());
        if (!item.lore().isEmpty()) {
            out.put("lore", flowList(item.lore()));
        }
        if (!item.enchantments().isEmpty()) {
            out.put("enchantments", flow(item.enchantments()));
        }
        put(out, "unbreakable", item.unbreakable());
        put(out, "color", item.color());
        put(out, "potion", item.potion());
        put(out, "data", item.data());
        if (out.size() == 1 && item.material() != null) {
            return item.material();
        }
        return out;
    }

    private Object region(Region region) {
        Map<String, Object> out = flow();
        put(out, "id", region.id());
        put(out, "cuboid", cuboid(region.cuboid()));
        if (!region.flags().isEmpty()) {
            out.put("flags", flowList(region.flags().stream().sorted().toList()));
        }
        return out;
    }

    private Object goal(Goal goal) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", goal.type());
        put(out, "id", goal.id());
        put(out, "name", goal.name());
        put(out, "owner", goal.owner());
        switch (goal) {
            case ControlPoint point -> {
                put(out, "region", point.region());
                put(out, "capture-time", duration(point.captureTime()));
            }
            case Core core -> {
                put(out, "material", core.material());
                put(out, "region", core.region());
                put(out, "leak", core.leak());
                if (!core.modeChanges().isEmpty()) {
                    out.put("mode-changes", list(core.modeChanges(), change -> {
                        Map<String, Object> entry = flow();
                        put(entry, "after", duration(change.after()));
                        put(entry, "material", change.material());
                        return entry;
                    }));
                }
            }
            case Wool wool -> {
                put(out, "color", wool.color());
                put(out, "monument", blockPos(wool.monument()));
                put(out, "room", wool.room());
                put(out, "craftable", wool.craftable());
            }
            case Destroyable destroyable -> {
                put(out, "region", destroyable.region());
                if (!destroyable.materials().isEmpty()) {
                    out.put("materials", flowList(destroyable.materials()));
                }
                put(out, "completion", destroyable.completion() == null ? null : destroyable.completion() + "%");
                put(out, "repairable", destroyable.repairable());
                put(out, "show-progress", destroyable.showProgress());
            }
        }
        return out;
    }

    private Map<String, Object> respawn(Respawn respawn) {
        Map<String, Object> out = rule(respawn.defaults(), new LinkedHashMap<>());
        if (!respawn.teams().isEmpty()) {
            Map<String, Object> teams = new LinkedHashMap<>();
            respawn.teams().forEach((id, rule) -> teams.put(id, rule(rule, flow())));
            out.put("teams", teams);
        }
        return out;
    }

    private Map<String, Object> rule(Respawn.Rule rule, Map<String, Object> out) {
        put(out, "enabled", rule.enabled());
        put(out, "delay", duration(rule.delay()));
        put(out, "kit", rule.kit());
        put(out, "lives", rule.lives());
        return out;
    }

    private Object bunkers(Bunkers bunkers) {
        Map<String, Object> out = new LinkedHashMap<>();
        put(out, "grace", duration(bunkers.grace()));
        put(out, "dtr", bunkers.dtr());
        if (bunkers.build() != null && bunkers.build().outsideBases() != null) {
            Map<String, Object> build = new LinkedHashMap<>();
            build.put("outside-bases", flowList(bunkers.build().outsideBases()));
            out.put("build", build);
        }
        if (bunkers.shops() != null) {
            Map<String, Object> shops = new LinkedHashMap<>();
            put(shops, "catalog", bunkers.shops().catalog());
            if (!bunkers.shops().npcs().isEmpty()) {
                shops.put("npcs", list(bunkers.shops().npcs(), npc -> {
                    Map<String, Object> entry = flow();
                    put(entry, "team", npc.team());
                    put(entry, "shop", npc.shop());
                    put(entry, "profession", npc.profession());
                    put(entry, "biome", npc.biome());
                    put(entry, "at", pose(npc.at()));
                    return entry;
                }));
            }
            out.put("shops", shops);
        }
        if (!bunkers.mines().isEmpty()) {
            out.put("mines", list(bunkers.mines(), mine -> {
                Map<String, Object> entry = flow();
                put(entry, "region", mine.region());
                put(entry, "respawn", duration(mine.respawn()));
                return entry;
            }));
        }
        if (bunkers.subclaims() != null) {
            Map<String, Object> subclaims = new LinkedHashMap<>();
            put(subclaims, "price", bunkers.subclaims().price());
            if (!bunkers.subclaims().chests().isEmpty()) {
                subclaims.put("chests", list(bunkers.subclaims().chests(), chest -> {
                    Map<String, Object> entry = flow();
                    entry.put("x", chest.x());
                    entry.put("y", chest.y());
                    entry.put("z", chest.z());
                    put(entry, "price", chest.price());
                    return entry;
                }));
            }
            out.put("subclaims", subclaims);
        }
        if (bunkers.economy() != null) {
            Map<String, Object> economy = flow();
            put(economy, "starting-balance", bunkers.economy().startingBalance());
            put(economy, "kill-reward", bunkers.economy().killReward());
            out.put("economy", economy);
        }
        return out;
    }

    private Object victory(VictoryCondition condition) {
        return switch (condition) {
            case LastTeamStanding ignored -> "last-team-standing";
            case TimeLimitVictory ignored -> "time-limit";
            case ControlPointVictory point -> point.goal() == null ? "control-point" : flow("control-point", point.goal());
            case GoalsVictory goals -> flow("goals", goals.goals());
        };
    }

    // ---- positions --------------------------------------------------------

    private Object pose(Pose pose) {
        if (pose == null) {
            return null;
        }
        Map<String, Object> out = flow();
        out.put("x", number(pose.x()));
        out.put("y", number(pose.y()));
        out.put("z", number(pose.z()));
        if (pose.yaw() != 0f) {
            out.put("yaw", number(pose.yaw()));
        }
        if (pose.pitch() != 0f) {
            out.put("pitch", number(pose.pitch()));
        }
        return out;
    }

    private Object blockPos(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        Map<String, Object> out = flow();
        out.put("x", pos.x());
        out.put("y", pos.y());
        out.put("z", pos.z());
        return out;
    }

    private Object cuboid(Cuboid cuboid) {
        if (cuboid == null) {
            return null;
        }
        Map<String, Object> out = flow();
        out.put("a", blockPos(cuboid.a()));
        out.put("b", blockPos(cuboid.b()));
        return out;
    }

    // ---- helpers ----------------------------------------------------------

    private static Object number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? (Object) (long) value : (Object) value;
    }

    private static String duration(Duration duration) {
        return duration == null ? null : Durations.format(duration);
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static void putSection(Map<String, Object> map, String key, Map<String, Object> section) {
        if (!section.isEmpty()) {
            map.put(key, section);
        }
    }

    private static <T> List<Object> list(Collection<T> values, java.util.function.Function<T, Object> mapper) {
        List<Object> out = new ArrayList<>();
        values.forEach(value -> out.add(mapper.apply(value)));
        return out;
    }

    private static FlowMap flow() {
        return new FlowMap();
    }

    private static FlowMap flow(String key, Object value) {
        FlowMap map = new FlowMap();
        map.put(key, value);
        return map;
    }

    private static FlowMap flow(Map<String, ?> values) {
        FlowMap map = new FlowMap();
        map.putAll(values);
        return map;
    }

    private static FlowList flowList(Collection<?> values) {
        FlowList list = new FlowList();
        list.addAll(values);
        return list;
    }

    /** A mapping written on one line: { x: 1, y: 2 }. */
    private static final class FlowMap extends LinkedHashMap<String, Object> {
    }

    /** A list written on one line: [a, b]. */
    private static final class FlowList extends ArrayList<Object> {
    }

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(2);
        options.setIndentWithIndicator(true);
        options.setWidth(1000);
        options.setSplitLines(false);
        Representer representer = new Representer(options) {
            {
                this.representers.put(FlowMap.class,
                        data -> representMapping(Tag.MAP, (Map<?, ?>) data, DumperOptions.FlowStyle.FLOW));
                this.representers.put(FlowList.class,
                        data -> representSequence(Tag.SEQ, (List<?>) data, DumperOptions.FlowStyle.FLOW));
            }
        };
        return new Yaml(representer, options);
    }
}
