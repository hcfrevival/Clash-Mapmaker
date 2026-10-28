package gg.hcfrevival.clash.mapmaker;

import gg.hcfrevival.clash.map.Durations;
import gg.hcfrevival.clash.map.Gamemode;
import gg.hcfrevival.clash.map.MapColors;
import gg.hcfrevival.clash.map.MapManifest;
import gg.hcfrevival.clash.map.MapManifest.Author;
import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import gg.hcfrevival.clash.map.MapManifest.Bunkers;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.Build;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.Economy;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.Mine;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.ShopNpc;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.Shops;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.SubclaimChest;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.Subclaims;
import gg.hcfrevival.clash.map.MapManifest.Classes;
import gg.hcfrevival.clash.map.MapManifest.ControlPoint;
import gg.hcfrevival.clash.map.MapManifest.Cooldowns;
import gg.hcfrevival.clash.map.MapManifest.Core;
import gg.hcfrevival.clash.map.MapManifest.Cuboid;
import gg.hcfrevival.clash.map.MapManifest.Destroyable;
import gg.hcfrevival.clash.map.MapManifest.Goal;
import gg.hcfrevival.clash.map.MapManifest.Info;
import gg.hcfrevival.clash.map.MapManifest.Join;
import gg.hcfrevival.clash.map.MapManifest.Kit;
import gg.hcfrevival.clash.map.MapManifest.KitChest;
import gg.hcfrevival.clash.map.MapManifest.Pose;
import gg.hcfrevival.clash.map.MapManifest.Region;
import gg.hcfrevival.clash.map.MapManifest.Respawn;
import gg.hcfrevival.clash.map.MapManifest.SpawnGroup;
import gg.hcfrevival.clash.map.MapManifest.Team;
import gg.hcfrevival.clash.map.MapManifest.TimeLimit;
import gg.hcfrevival.clash.map.MapManifest.Variant;
import gg.hcfrevival.clash.map.MapManifest.VictoryCondition;
import gg.hcfrevival.clash.map.MapManifest.Wool;
import gg.hcfrevival.clash.map.MapManifest.World;
import gg.hcfrevival.clash.map.Proto;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The map being edited: {@link MapManifest}'s sections as mutable fields, so a command changes one thing and
 * {@link #build()} makes the record again. Every edit throws {@link IllegalArgumentException} with the sentence
 * the map maker reads.
 */
final class Draft {
    static final List<String> TEAM_KEYS = List.of("name", "display-name", "color", "min", "max", "overfill", "role", "base");
    static final List<String> BUNKERS_KEYS = List.of("grace", "dtr", "outside-bases", "catalog", "subclaim-price", "starting-balance", "kill-reward");
    static final List<String> RULE_KEYS = List.of("max-players", "late-join", "time", "weather", "bounds", "time-limit", "time-limit-result",
            "overtime", "respawn-delay", "respawn-kit", "gamerule.<name>", "cooldown.enderpearl", "cooldown.crapple", "cooldown.gapple",
            "cooldown.totem", "cooldown.wind-charge");
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    String name;
    String slug;
    String version;
    String objective;
    final List<Author> authors = new ArrayList<>();
    Gamemode gamemode;
    Integer maxPlayers;
    Join join;

    Pose spawn;
    String bounds;
    Long time;
    String weather;
    final Map<String, Object> gamerules = new LinkedHashMap<>();

    final List<Team> teams = new ArrayList<>();
    final List<SpawnGroup> spawns = new ArrayList<>();
    final Map<String, Kit> kits = new LinkedHashMap<>();
    List<KitChest> kitChests = List.of();
    Classes classes;
    final List<Region> regions = new ArrayList<>();
    final List<Goal> goals = new ArrayList<>();
    Respawn respawn;
    TimeLimit timeLimit;
    Cooldowns cooldowns;
    List<VictoryCondition> victory;
    Map<String, Variant> variants = Map.of();

    Duration grace;
    Double dtr;
    Build build;
    String catalog;
    final List<ShopNpc> npcs = new ArrayList<>();
    final List<Mine> mines = new ArrayList<>();
    Integer subclaimPrice;
    final List<SubclaimChest> chests = new ArrayList<>();
    Economy economy;

    /** A new map of one mode: the slug names it, the maker is its author, spectators spawn where they stand. */
    static Draft fresh(String slug, Gamemode gamemode, Author author, Pose spawn) {
        requireSlug(slug);
        Draft draft = new Draft();
        draft.name = Msg.capitalise(slug);
        draft.slug = slug;
        draft.version = "1.0.0";
        draft.gamemode = gamemode;
        draft.authors.add(author);
        draft.spawn = spawn;
        return draft;
    }

    static Draft of(MapManifest manifest) {
        Draft draft = new Draft();
        Info info = manifest.info();
        draft.name = info.name();
        draft.slug = info.slug();
        draft.version = info.version();
        draft.objective = info.objective();
        draft.authors.addAll(info.authors());
        draft.gamemode = info.gamemode();
        draft.maxPlayers = info.maxPlayers();
        draft.join = info.join();
        World world = manifest.world();
        draft.spawn = world.spawn();
        draft.bounds = world.bounds();
        draft.time = world.time();
        draft.weather = world.weather();
        draft.gamerules.putAll(world.gamerules());
        draft.teams.addAll(manifest.teams());
        draft.spawns.addAll(manifest.spawns());
        draft.kits.putAll(manifest.kits());
        draft.kitChests = manifest.kitChests();
        draft.classes = manifest.classes();
        draft.regions.addAll(manifest.regions());
        draft.goals.addAll(manifest.goals());
        draft.respawn = manifest.respawn();
        draft.timeLimit = manifest.timeLimit();
        draft.cooldowns = manifest.cooldowns();
        draft.victory = manifest.victory();
        draft.variants = manifest.variants();
        Bunkers bunkers = manifest.bunkers();
        if (bunkers != null) {
            draft.grace = bunkers.grace();
            draft.dtr = bunkers.dtr();
            draft.build = bunkers.build();
            draft.economy = bunkers.economy();
            draft.mines.addAll(bunkers.mines());
            if (bunkers.shops() != null) {
                draft.catalog = bunkers.shops().catalog();
                draft.npcs.addAll(bunkers.shops().npcs());
            }
            if (bunkers.subclaims() != null) {
                draft.subclaimPrice = bunkers.subclaims().price();
                draft.chests.addAll(bunkers.subclaims().chests());
            }
        }
        return draft;
    }

    MapManifest build() {
        Info info = new Info(Proto.CURRENT, name, slug, version, objective, authors, gamemode, maxPlayers, join);
        World world = new World(spawn, bounds, time, weather, gamerules);
        return new MapManifest(info, world, teams, spawns, kits, kitChests, classes, regions, goals, respawn, bunkers(),
                timeLimit, cooldowns, victory, variants);
    }

    private Bunkers bunkers() {
        Shops shops = catalog == null && npcs.isEmpty() ? null : new Shops(catalog, npcs);
        Subclaims subclaims = subclaimPrice == null && chests.isEmpty() ? null : new Subclaims(subclaimPrice, chests);
        boolean any = grace != null || dtr != null || build != null || shops != null || !mines.isEmpty() || subclaims != null
                || economy != null;
        return any ? new Bunkers(grace, dtr, build, shops, mines, subclaims, economy) : null;
    }

    // ---- lookups ----------------------------------------------------------

    Optional<Team> team(String id) {
        return teams.stream().filter(team -> team.id().equals(id)).findFirst();
    }

    Optional<Region> region(String id) {
        return regions.stream().filter(region -> region.id().equals(id)).findFirst();
    }

    Optional<Goal> goal(String id) {
        return goals.stream().filter(goal -> goal.id().equals(id)).findFirst();
    }

    List<String> teamIds() {
        return teams.stream().map(Team::id).toList();
    }

    List<String> regionIds() {
        return regions.stream().map(Region::id).toList();
    }

    List<String> goalIds() {
        return goals.stream().map(Goal::id).toList();
    }

    List<Pose> spawnsFor(String team) {
        return spawns.stream().filter(group -> team.equals(group.team())).flatMap(group -> group.points().stream()).toList();
    }

    /** Where a goal is: its region, or the one block of a wool monument. */
    Optional<Cuboid> goalBox(Goal goal) {
        return switch (goal) {
            case ControlPoint point -> region(point.region()).map(Region::cuboid);
            case Core core -> region(core.region()).map(Region::cuboid);
            case Destroyable destroyable -> region(destroyable.region()).map(Region::cuboid);
            case Wool wool -> Optional.ofNullable(wool.monument()).map(pos -> new Cuboid(pos, pos));
        };
    }

    // ---- teams and spawns ---------------------------------------------------

    void addTeam(String id, String color, int max, String name) {
        requireId(id, "team");
        if (team(id).isPresent()) {
            throw bad("There is already a team called " + id + ".");
        }
        teams.add(new Team(id, name == null ? Msg.capitalise(id) : name, null, color(color), null, max, null, null, null));
    }

    void editTeam(String id, String key, String value) {
        Team t = requireTeam(id);
        Team edited = switch (key) {
            case "name" -> new Team(t.id(), value, t.displayName(), t.color(), t.min(), t.max(), t.overfill(), t.role(), t.base());
            case "display-name" -> new Team(t.id(), t.name(), value, t.color(), t.min(), t.max(), t.overfill(), t.role(), t.base());
            case "color" -> new Team(t.id(), t.name(), t.displayName(), color(value), t.min(), t.max(), t.overfill(), t.role(), t.base());
            case "min" -> new Team(t.id(), t.name(), t.displayName(), t.color(), integer(value), t.max(), t.overfill(), t.role(), t.base());
            case "max" -> new Team(t.id(), t.name(), t.displayName(), t.color(), t.min(), integer(value), t.overfill(), t.role(), t.base());
            case "overfill" -> new Team(t.id(), t.name(), t.displayName(), t.color(), t.min(), t.max(), integer(value), t.role(), t.base());
            case "role" -> new Team(t.id(), t.name(), t.displayName(), t.color(), t.min(), t.max(), t.overfill(), role(value), t.base());
            case "base" -> {
                requireRegion(value);
                yield new Team(t.id(), t.name(), t.displayName(), t.color(), t.min(), t.max(), t.overfill(), t.role(), value);
            }
            default -> throw bad("Unknown team setting " + key + ". Use one of " + String.join(", ", TEAM_KEYS) + ".");
        };
        teams.set(teams.indexOf(t), edited);
    }

    void removeTeam(String id) {
        requireTeam(id);
        teams.removeIf(team -> team.id().equals(id));
        spawns.removeIf(group -> id.equals(group.team()));
    }

    void addSpawn(String team, Pose pose) {
        requireTeam(team);
        for (int i = 0; i < spawns.size(); i++) {
            SpawnGroup group = spawns.get(i);
            if (team.equals(group.team())) {
                List<Pose> points = new ArrayList<>(group.points());
                points.add(pose);
                spawns.set(i, new SpawnGroup(team, points));
                return;
            }
        }
        spawns.add(new SpawnGroup(team, List.of(pose)));
    }

    void clearSpawns(String team) {
        if (!spawns.removeIf(group -> team.equals(group.team()))) {
            throw bad(team + " has no spawns.");
        }
    }

    // ---- regions and goals --------------------------------------------------

    /** Adds or replaces a region. */
    void putRegion(String id, Cuboid cuboid, Set<String> flags, Set<String> keepOut) {
        requireId(id, "region");
        for (String flag : flags) {
            if (!Region.KNOWN_FLAGS.contains(flag)) {
                throw bad("Unknown flag " + flag + ". Flags: " + String.join(", ", Region.KNOWN_FLAGS) + ", keep-out:<team,team>.");
            }
        }
        keepOut.forEach(this::requireTeam);
        regions.removeIf(region -> region.id().equals(id));
        regions.add(new Region(id, cuboid, flags, keepOut));
    }

    void removeRegion(String id) {
        if (!regions.removeIf(region -> region.id().equals(id))) {
            throw bad("There is no region called " + id + ".");
        }
    }

    /** Adds or replaces a goal, once everything it points at exists. */
    void putGoal(Goal goal) {
        requireId(goal.id(), "goal");
        if (goal.owner() != null) {
            requireTeam(goal.owner());
        }
        switch (goal) {
            case ControlPoint point -> requireRegion(point.region());
            case Core core -> requireRegion(core.region());
            case Destroyable destroyable -> requireRegion(destroyable.region());
            case Wool wool -> {
                if (wool.room() != null) {
                    requireRegion(wool.room());
                }
                if (!MapColors.isDye(wool.color())) {
                    throw bad(wool.color() + " is not a wool color. Use one of " + String.join(", ", MapColors.DYES) + ".");
                }
            }
        }
        goals.removeIf(other -> other.id().equals(goal.id()));
        goals.add(goal);
    }

    void removeGoal(String id) {
        if (!goals.removeIf(goal -> goal.id().equals(id))) {
            throw bad("There is no goal called " + id + ".");
        }
    }

    // ---- kits and classes ---------------------------------------------------

    void putKit(String name, Kit kit) {
        requireId(name, "kit");
        kits.put(name, kit);
    }

    void removeKit(String name) {
        if (kits.remove(name) == null) {
            throw bad("There is no kit called " + name + ".");
        }
    }

    void allowClasses(List<String> allowed) {
        classes = new Classes(allowed, classes == null ? Map.of() : classes.caps());
    }

    void capClass(String name, int cap) {
        Map<String, Integer> caps = new LinkedHashMap<>(classes == null ? Map.of() : classes.caps());
        caps.put(name, cap);
        classes = new Classes(classes == null ? List.of() : classes.allowed(), caps);
    }

    // ---- bunkers ------------------------------------------------------------

    /** The team's base: region {@code <team>-base} from the selection. */
    void setBase(String team, Cuboid cuboid) {
        requireTeam(team);
        putRegion(team + "-base", cuboid, Set.of(), Set.of());
        editTeam(team, "base", team + "-base");
    }

    void addShop(String team, String shop, Pose at, String profession, String biome) {
        if (team != null) {
            requireTeam(team);
        }
        if (profession != null && !ShopNpc.PROFESSIONS.contains(profession)) {
            throw bad(profession + " is not a villager job. Use one of " + String.join(", ", ShopNpc.PROFESSIONS) + ".");
        }
        if (biome != null && !ShopNpc.BIOMES.contains(biome)) {
            throw bad(biome + " is not a villager look. Use one of " + String.join(", ", ShopNpc.BIOMES) + ".");
        }
        npcs.add(new ShopNpc(team, shop, at, profession, biome));
    }

    void addMine(String id, Cuboid cuboid, Duration respawn) {
        putRegion(id, cuboid, Set.of(), Set.of());
        mines.removeIf(mine -> mine.region().equals(id));
        mines.add(new Mine(id, respawn));
    }

    void putSubclaim(BlockPos pos, Integer price) {
        chests.removeIf(chest -> chest.pos().equals(pos));
        chests.add(new SubclaimChest(pos.x(), pos.y(), pos.z(), price));
    }

    void bunkersRule(String key, String value) {
        switch (key) {
            case "grace" -> grace = duration(value);
            case "dtr" -> dtr = decimal(value);
            case "outside-bases" -> build = new Build(list(value));
            case "catalog" -> catalog = value;
            case "subclaim-price" -> subclaimPrice = integer(value);
            case "starting-balance" -> economy = new Economy(integer(value), economy == null ? null : economy.killReward());
            case "kill-reward" -> economy = new Economy(economy == null ? null : economy.startingBalance(), integer(value));
            default -> throw bad("Unknown Bunkers rule " + key + ". Use one of " + String.join(", ", BUNKERS_KEYS) + ".");
        }
    }

    // ---- everything else ----------------------------------------------------

    void rule(String key, String value) {
        TimeLimit limit = timeLimit == null ? new TimeLimit(null, null, null) : timeLimit;
        Respawn.Rule defaults = respawn == null ? new Respawn.Rule(null, null, null, null) : respawn.defaults();
        Map<String, Respawn.Rule> perTeam = respawn == null ? Map.of() : respawn.teams();
        switch (key) {
            case "max-players" -> maxPlayers = integer(value);
            case "late-join" -> join = new Join(bool(value));
            case "time" -> time = (long) integer(value);
            case "weather" -> weather = value;
            case "bounds" -> {
                requireRegion(value);
                bounds = value;
            }
            case "time-limit" -> timeLimit = new TimeLimit(duration(value), limit.result(), limit.overtime());
            case "time-limit-result" -> timeLimit = new TimeLimit(limit.duration(), value, limit.overtime());
            case "overtime" -> timeLimit = new TimeLimit(limit.duration(), limit.result(), duration(value));
            case "respawn-delay" -> respawn = new Respawn(new Respawn.Rule(defaults.enabled(), duration(value), defaults.kit(), defaults.lives()), perTeam);
            case "respawn-kit" -> {
                if (!kits.containsKey(value)) {
                    throw bad("There is no kit called " + value + ". Save one with /cmap kit set " + value + ".");
                }
                respawn = new Respawn(new Respawn.Rule(defaults.enabled(), defaults.delay(), value, defaults.lives()), perTeam);
            }
            default -> {
                if (key.startsWith("gamerule.")) {
                    gamerules.put(key.substring("gamerule.".length()), typed(value));
                } else if (key.startsWith("cooldown.")) {
                    cooldowns = cooldown(key.substring("cooldown.".length()), duration(value));
                } else {
                    throw bad("Unknown rule " + key + ". Use one of " + String.join(", ", RULE_KEYS) + ".");
                }
            }
        }
    }

    private Cooldowns cooldown(String item, Duration duration) {
        Cooldowns c = cooldowns == null ? new Cooldowns(null, null, null, null, null) : cooldowns;
        return switch (item) {
            case "enderpearl" -> new Cooldowns(duration, c.crapple(), c.gapple(), c.totem(), c.windCharge());
            case "crapple" -> new Cooldowns(c.enderpearl(), duration, c.gapple(), c.totem(), c.windCharge());
            case "gapple" -> new Cooldowns(c.enderpearl(), c.crapple(), duration, c.totem(), c.windCharge());
            case "totem" -> new Cooldowns(c.enderpearl(), c.crapple(), c.gapple(), duration, c.windCharge());
            case "wind-charge" -> new Cooldowns(c.enderpearl(), c.crapple(), c.gapple(), c.totem(), duration);
            default -> throw bad("Unknown cooldown " + item + ". Use enderpearl, crapple, gapple, totem or wind-charge.");
        };
    }

    // ---- checks and parsing -------------------------------------------------

    Team requireTeam(String id) {
        return team(id).orElseThrow(() -> bad("There is no team called " + id + ". Teams: " + String.join(", ", teamIds()) + "."));
    }

    Region requireRegion(String id) {
        return region(id).orElseThrow(() -> bad("There is no region called " + id + ". Add it with /cmap region add " + id + "."));
    }

    static void requireId(String id, String what) {
        if (id == null || !ID.matcher(id).matches()) {
            throw bad("A " + what + " id is lowercase letters, digits, - and _, like red-base.");
        }
    }

    static void requireSlug(String slug) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw bad("A slug is lowercase letters, digits and -, like bunkers-ravine.");
        }
    }

    static String color(String color) {
        if (!MapColors.isColor(color)) {
            throw bad(color + " is not a color. Use a name like red or #ff5555.");
        }
        return color;
    }

    static String role(String role) {
        if (!role.equals("attacker") && !role.equals("defender")) {
            throw bad("A role is attacker or defender.");
        }
        return role;
    }

    static Duration duration(String text) {
        return Durations.parse(text).orElseThrow(() -> bad(text + " is not a time. Write it like 90s, 5m or 1h30m."));
    }

    static int integer(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw bad(text + " is not a whole number.");
        }
    }

    static double decimal(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw bad(text + " is not a number.");
        }
    }

    static boolean bool(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> true;
            case "false", "no", "off" -> false;
            default -> throw bad(text + " is not true or false.");
        };
    }

    /** Words separated by commas or spaces. */
    static List<String> list(String text) {
        return text == null ? List.of() : Arrays.stream(text.trim().split("[,\\s]+")).filter(word -> !word.isEmpty()).toList();
    }

    private static Object typed(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    static IllegalArgumentException bad(String message) {
        return new IllegalArgumentException(message);
    }
}
