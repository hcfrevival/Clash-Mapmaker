package gg.hcfrevival.clash.map;

import gg.hcfrevival.clash.map.MapManifest.Author;
import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import gg.hcfrevival.clash.map.MapManifest.Bunkers;
import gg.hcfrevival.clash.map.MapManifest.Classes;
import gg.hcfrevival.clash.map.MapManifest.ControlPoint;
import gg.hcfrevival.clash.map.MapManifest.ControlPointVictory;
import gg.hcfrevival.clash.map.MapManifest.Core;
import gg.hcfrevival.clash.map.MapManifest.Cuboid;
import gg.hcfrevival.clash.map.MapManifest.Destroyable;
import gg.hcfrevival.clash.map.MapManifest.Goal;
import gg.hcfrevival.clash.map.MapManifest.GoalsVictory;
import gg.hcfrevival.clash.map.MapManifest.Info;
import gg.hcfrevival.clash.map.MapManifest.Item;
import gg.hcfrevival.clash.map.MapManifest.Join;
import gg.hcfrevival.clash.map.MapManifest.Kit;
import gg.hcfrevival.clash.map.MapManifest.KitChest;
import gg.hcfrevival.clash.map.MapManifest.LastTeamStanding;
import gg.hcfrevival.clash.map.MapManifest.ModeChange;
import gg.hcfrevival.clash.map.MapManifest.Pose;
import gg.hcfrevival.clash.map.MapManifest.Region;
import gg.hcfrevival.clash.map.MapManifest.Respawn;
import gg.hcfrevival.clash.map.MapManifest.SpawnGroup;
import gg.hcfrevival.clash.map.MapManifest.Team;
import gg.hcfrevival.clash.map.MapManifest.TimeLimit;
import gg.hcfrevival.clash.map.MapManifest.TimeLimitVictory;
import gg.hcfrevival.clash.map.MapManifest.Variant;
import gg.hcfrevival.clash.map.MapManifest.VictoryCondition;
import gg.hcfrevival.clash.map.MapManifest.Wool;
import gg.hcfrevival.clash.map.MapManifest.World;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads map.yml into a {@link MapManifest}.
 *
 * <p>Reading never throws for a bad map. Every mistake becomes a {@link Problem} with
 * the place it was found, and the reader carries on so a map maker sees all of them at
 * once. Reading only checks shape (types, known keys, the proto); whether the map makes
 * sense is {@link MapValidator}'s job.
 *
 * <p>Maps come from strangers, so the YAML is read with SnakeYAML's safe constructor,
 * duplicate keys are refused and aliases and file size are capped.
 */
public final class MapManifestReader {
    /** Keys a variant may replace. */
    public static final Set<String> VARIANT_KEYS = Set.of(
            "objective", "max-players", "join", "world", "teams", "spawns", "kits", "kit-chests",
            "classes", "regions", "goals", "respawn", "bunkers", "time-limit", "victory");

    private static final int MAX_FILE_CHARS = 4 * 1024 * 1024;

    /**
     * What came out of reading a file.
     *
     * @param manifest the map, or {@code null} when the file could not be read at all
     * @param problems everything found while reading, in file order
     */
    public record Result(MapManifest manifest, List<Problem> problems) {
        public Result {
            problems = List.copyOf(problems);
        }

        public boolean hasErrors() {
            return manifest == null || problems.stream().anyMatch(Problem::isError);
        }
    }

    public Result read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException e) {
            return new Result(null, List.of(Problem.error("", "could not read " + file.getFileName() + ": " + e.getMessage())));
        }
    }

    public Result read(String yaml) {
        return read(new StringReader(yaml));
    }

    public Result read(Reader reader) {
        Object root;
        try {
            root = yaml().load(reader);
        } catch (YAMLException e) {
            return new Result(null, List.of(Problem.error("", "this is not valid YAML: " + firstLine(e.getMessage()))));
        }
        if (!(root instanceof Map<?, ?> map)) {
            return new Result(null, List.of(Problem.error("", "map.yml should start with key: value pairs such as `proto: 1`.")));
        }
        return read(map);
    }

    /** Reads an already parsed YAML document. */
    public Result read(Map<?, ?> document) {
        List<Problem> problems = new ArrayList<>();
        Node root = new Node(document, "", problems);
        Integer proto = root.integer("proto");
        if (proto == null) {
            if (!root.has("proto") || root.raw("proto") == null) {
                problems.add(Problem.error("proto", "missing. Add `proto: " + Proto.CURRENT + "` at the top of the file."));
            }
            return new Result(null, problems);
        }
        if (proto > Proto.CURRENT) {
            problems.add(Problem.error("proto", "this map uses format " + proto
                    + " but this version only reads up to " + Proto.CURRENT + ". Update Clash Mapmaker."));
            return new Result(null, problems);
        }
        if (!Proto.isSupported(proto)) {
            problems.add(Problem.error("proto", "should be " + Proto.CURRENT + "."));
            return new Result(null, problems);
        }
        MapManifest manifest = new Parse(problems).manifest(root, proto);
        root.finish();
        return new Result(manifest, problems);
    }

    /**
     * The map as one of its variants plays: the variant's sections replace the base map's.
     * Problems are reported with paths relative to the merged map.
     */
    public Result readVariant(MapManifest base, String variantId) {
        Variant variant = base.variants().get(variantId);
        if (variant == null) {
            return new Result(null, List.of(Problem.error("variants", "there is no variant called " + variantId + ".")));
        }
        Map<String, Object> merged = new LinkedHashMap<>(new MapManifestWriter().toMap(base));
        merged.remove("variants");
        merged.putAll(variant.overrides());
        if (variant.name() != null) {
            merged.put("name", variant.name());
        }
        return read(merged);
    }

    /** One read in progress; collects its problems. */
    private static final class Parse {
        private final List<Problem> problems;

        Parse(List<Problem> problems) {
            this.problems = problems;
        }

        MapManifest manifest(Node root, int proto) {
            return new MapManifest(
                    info(root, proto),
                    root.section("world").map(this::world).orElse(null),
                    root.sections("teams", this::team),
                    root.sections("spawns", this::spawnGroup),
                    kits(root),
                    root.sections("kit-chests", node -> new KitChest(node.string("region"), items(node, "items"))),
                    root.section("classes").map(this::classes).orElse(null),
                    root.sections("regions", this::region),
                    root.sections("goals", this::goal),
                    root.section("respawn").map(this::respawn).orElse(null),
                    root.section("bunkers").map(this::bunkers).orElse(null),
                    root.section("time-limit").map(this::timeLimit).orElse(null),
                    root.has("victory") ? victory(root) : null,
                    variants(root)
            );
        }

        // ---- sections ---------------------------------------------------------

        private Info info(Node root, int proto) {
            String gamemodeId = root.string("gamemode");
            Gamemode gamemode = null;
            if (gamemodeId != null) {
                gamemode = Gamemode.byId(gamemodeId).orElse(null);
                if (gamemode == null) {
                    root.error("gamemode", "should be one of " + String.join(", ", Gamemode.ids()) + ".");
                }
            }
            Join join = root.section("join").map(node -> {
                Join parsed = new Join(node.bool("late"));
                node.finish();
                return parsed;
            }).orElse(null);
            return new Info(
                    proto,
                    root.string("name"),
                    root.string("slug"),
                    root.string("version"),
                    root.string("objective"),
                    root.sections("authors", node -> new Author(node.string("uuid"), node.string("name"), node.string("contribution"))),
                    gamemode,
                    root.integer("max-players"),
                    join
            );
        }

        private World world(Node node) {
            Map<String, Object> gamerules = new LinkedHashMap<>();
            node.section("gamerules").ifPresent(rules -> {
                rules.values().forEach((key, value) -> {
                    rules.raw(key);
                    gamerules.put(key, value);
                });
            });
            World world = new World(
                    pose(node, "spawn"),
                    node.string("bounds"),
                    node.longValue("time"),
                    node.string("weather"),
                    gamerules
            );
            node.finish();
            return world;
        }

        private Team team(Node node) {
            return new Team(
                    node.string("id"),
                    node.string("name"),
                    node.string("display-name"),
                    node.string("color"),
                    node.integer("min"),
                    node.integer("max"),
                    node.integer("overfill"),
                    node.string("role"),
                    node.string("base")
            );
        }

        private SpawnGroup spawnGroup(Node node) {
            return new SpawnGroup(node.string("team"), node.list("points", this::pose));
        }

        private Map<String, Kit> kits(Node root) {
            Map<String, Kit> kits = new LinkedHashMap<>();
            root.section("kits").ifPresent(section -> {
                for (String name : section.values().keySet()) {
                    section.section(name).ifPresent(node -> {
                        Map<String, Item> armor = new LinkedHashMap<>();
                        node.section("armor").ifPresent(armorNode -> {
                            for (String slot : Kit.ARMOR_SLOTS) {
                                Item item = item(armorNode.raw(slot), armorNode.path(slot));
                                if (item != null) {
                                    armor.put(slot, item);
                                }
                            }
                            armorNode.finish();
                        });
                        Item offhand = item(node.raw("offhand"), node.path("offhand"));
                        kits.put(name, new Kit(armor, items(node, "items"), offhand));
                        node.finish();
                    });
                }
            });
            return kits;
        }

        private List<Item> items(Node node, String key) {
            return node.list(key, this::item);
        }

        /** An item is a section, or just a material id such as {@code cobweb}. */
        private Item item(Object value, String path) {
            if (value == null) {
                return null;
            }
            if (value instanceof String material) {
                return new Item(null, material, null, null, List.of(), Map.of(), null, null, null, null);
            }
            if (!(value instanceof Map<?, ?> map)) {
                return problem(path, "should be an item: a material id or a section with `material:`.");
            }
            Node node = new Node(map, path, problems);
            Map<String, Integer> enchantments = new LinkedHashMap<>();
            node.section("enchantments").ifPresent(section -> {
                for (String id : section.values().keySet()) {
                    Integer level = section.integer(id);
                    if (level != null) {
                        enchantments.put(id, level);
                    }
                }
            });
            Item item = new Item(
                    node.integer("slot"),
                    node.string("material"),
                    node.integer("amount"),
                    node.string("name"),
                    node.strings("lore"),
                    enchantments,
                    node.bool("unbreakable"),
                    node.string("color"),
                    node.string("potion"),
                    node.string("data")
            );
            node.finish();
            return item;
        }

        private Classes classes(Node node) {
            Map<String, Integer> caps = new LinkedHashMap<>();
            node.section("caps").ifPresent(section -> {
                for (String id : section.values().keySet()) {
                    Integer cap = section.integer(id);
                    if (cap != null) {
                        caps.put(id, cap);
                    }
                }
            });
            Classes classes = new Classes(node.strings("allowed"), caps);
            node.finish();
            return classes;
        }

        private Region region(Node node) {
            Cuboid cuboid = node.section("cuboid").map(section -> {
                BlockPos a = blockPos(section, "a");
                BlockPos b = blockPos(section, "b");
                section.finish();
                return a == null || b == null ? null : new Cuboid(a, b);
            }).orElse(null);
            return new Region(node.string("id"), cuboid, new LinkedHashSet<>(node.strings("flags")));
        }

        private Goal goal(Node node) {
            String type = node.string("type");
            if (type == null) {
                node.error("type", "missing. Use control-point, core, wool or destroyable.");
                markAllUsed(node);
                return null;
            }
            return switch (type) {
                case ControlPoint.TYPE -> new ControlPoint(
                        node.string("id"), node.string("name"), node.string("region"), node.duration("capture-time"));
                case Core.TYPE -> new Core(
                        node.string("id"), node.string("name"), node.string("owner"), node.string("material"),
                        node.string("region"), node.integer("leak"),
                        node.sections("mode-changes", change -> new ModeChange(change.duration("after"), change.string("material"))));
                case Wool.TYPE -> new Wool(
                        node.string("id"), node.string("name"), node.string("owner"), node.string("color"),
                        blockPos(node, "monument"), node.string("room"), node.bool("craftable"));
                case Destroyable.TYPE -> new Destroyable(
                        node.string("id"), node.string("name"), node.string("owner"), node.string("region"),
                        node.strings("materials"), node.percent("completion"), node.bool("repairable"),
                        node.bool("show-progress"));
                default -> {
                    node.error("type", "unknown goal type " + type + ". Use control-point, core, wool or destroyable.");
                    markAllUsed(node);
                    yield null;
                }
            };
        }

        private Respawn respawn(Node node) {
            Map<String, Respawn.Rule> teams = new LinkedHashMap<>();
            node.section("teams").ifPresent(section -> {
                for (String id : section.values().keySet()) {
                    section.section(id).ifPresent(team -> {
                        teams.put(id, rule(team));
                        team.finish();
                    });
                }
            });
            Respawn respawn = new Respawn(rule(node), teams);
            node.finish();
            return respawn;
        }

        private Respawn.Rule rule(Node node) {
            return new Respawn.Rule(node.bool("enabled"), node.duration("delay"), node.string("kit"), node.integer("lives"));
        }

        private Bunkers bunkers(Node node) {
            Bunkers.Build build = node.section("build").map(section -> {
                Bunkers.Build parsed = new Bunkers.Build(section.has("outside-bases") ? section.strings("outside-bases") : null);
                section.finish();
                return parsed;
            }).orElse(null);
            Bunkers.Shops shops = node.section("shops").map(section -> {
                Bunkers.Shops parsed = new Bunkers.Shops(section.string("catalog"), section.sections("npcs",
                        npc -> new Bunkers.ShopNpc(npc.string("team"), npc.string("shop"), pose(npc, "at"),
                                npc.string("profession"), npc.string("biome"))));
                section.finish();
                return parsed;
            }).orElse(null);
            Bunkers.Subclaims subclaims = node.section("subclaims").map(section -> {
                Bunkers.Subclaims parsed = new Bunkers.Subclaims(section.integer("price"), section.sections("chests", chest -> {
                    Integer x = required(chest, "x");
                    Integer y = required(chest, "y");
                    Integer z = required(chest, "z");
                    Integer price = chest.integer("price");
                    return x == null || y == null || z == null ? null : new Bunkers.SubclaimChest(x, y, z, price);
                }));
                section.finish();
                return parsed;
            }).orElse(null);
            Bunkers.Economy economy = node.section("economy").map(section -> {
                Bunkers.Economy parsed = new Bunkers.Economy(section.integer("starting-balance"), section.integer("kill-reward"));
                section.finish();
                return parsed;
            }).orElse(null);
            Bunkers bunkers = new Bunkers(
                    node.duration("grace"),
                    node.decimal("dtr"),
                    build,
                    shops,
                    node.sections("mines", mine -> new Bunkers.Mine(mine.string("region"), mine.duration("respawn"))),
                    subclaims,
                    economy
            );
            node.finish();
            return bunkers;
        }

        private TimeLimit timeLimit(Node node) {
            TimeLimit limit = new TimeLimit(node.duration("duration"), node.string("result"), node.duration("overtime"));
            node.finish();
            return limit;
        }

        private List<VictoryCondition> victory(Node root) {
            return root.list("victory", (value, path) -> {
                if ("last-team-standing".equals(value)) {
                    return new LastTeamStanding();
                }
                if ("time-limit".equals(value)) {
                    return new TimeLimitVictory();
                }
                if ("control-point".equals(value)) {
                    return new ControlPointVictory(null);
                }
                if (value instanceof Map<?, ?> map && map.size() == 1) {
                    Map.Entry<?, ?> entry = map.entrySet().iterator().next();
                    String key = String.valueOf(entry.getKey());
                    String target = entry.getValue() == null ? null : String.valueOf(entry.getValue());
                    if ("goals".equals(key) && target != null) {
                        return new GoalsVictory(target);
                    }
                    if ("control-point".equals(key)) {
                        return new ControlPointVictory(target);
                    }
                }
                return problem(path, "should be last-team-standing, time-limit, control-point, { control-point: <goal id> } or { goals: all-own | all-enemy | <goal id> }.");
            });
        }

        private Map<String, Variant> variants(Node root) {
            Map<String, Variant> variants = new LinkedHashMap<>();
            root.section("variants").ifPresent(section -> {
                for (String id : section.values().keySet()) {
                    section.section(id).ifPresent(node -> {
                        String name = node.string("name");
                        Map<String, Object> overrides = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> entry : node.values().entrySet()) {
                            if (entry.getKey().equals("name")) {
                                continue;
                            }
                            if (VARIANT_KEYS.contains(entry.getKey())) {
                                node.raw(entry.getKey());
                                overrides.put(entry.getKey(), entry.getValue());
                            }
                        }
                        node.finish();
                        variants.put(id, new Variant(name, overrides));
                    });
                }
            });
            return variants;
        }

        // ---- positions --------------------------------------------------------

        private Pose pose(Node node, String key) {
            Object value = node.raw(key);
            return value == null ? null : pose(value, node.path(key));
        }

        private Pose pose(Object value, String path) {
            if (!(value instanceof Map<?, ?> map)) {
                return problem(path, "should be a position such as { x: 0.5, y: 64, z: 0.5, yaw: 90 }.");
            }
            Node node = new Node(map, path, problems);
            Double x = requiredDecimal(node, "x");
            Double y = requiredDecimal(node, "y");
            Double z = requiredDecimal(node, "z");
            Double yaw = node.decimal("yaw");
            Double pitch = node.decimal("pitch");
            node.finish();
            if (x == null || y == null || z == null) {
                return null;
            }
            return new Pose(x, y, z, yaw == null ? 0f : yaw.floatValue(), pitch == null ? 0f : pitch.floatValue());
        }

        private BlockPos blockPos(Node node, String key) {
            Object value = node.raw(key);
            if (value == null) {
                return null;
            }
            if (!(value instanceof Map<?, ?> map)) {
                return problem(node.path(key), "should be a block position such as { x: 10, y: 64, z: -3 }.");
            }
            Node pos = new Node(map, node.path(key), node.problems());
            Integer x = required(pos, "x");
            Integer y = required(pos, "y");
            Integer z = required(pos, "z");
            pos.finish();
            return x == null || y == null || z == null ? null : new BlockPos(x, y, z);
        }

        private static Integer required(Node node, String key) {
            if (!node.has(key)) {
                node.error(key, "missing.");
                return null;
            }
            return node.integer(key);
        }

        private static Double requiredDecimal(Node node, String key) {
            if (!node.has(key)) {
                node.error(key, "missing.");
                return null;
            }
            return node.decimal(key);
        }

        private static void markAllUsed(Node node) {
            node.values().keySet().forEach(node::raw);
        }

        private <T> T problem(String path, String message) {
            problems.add(Problem.error(path, message));
            return null;
        }
    }

    // ---- plumbing ---------------------------------------------------------

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(40);
        options.setCodePointLimit(MAX_FILE_CHARS);
        return new Yaml(new SafeConstructor(options));
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
