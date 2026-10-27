package gg.hcfrevival.clash.map;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything one {@code map.yml} says, as plain records.
 *
 * <p>Records are lenient on purpose: a map that is still being built can miss
 * required fields, so nothing here throws on a missing value. {@link MapValidator}
 * decides whether a manifest is good enough to play. Optional values are
 * {@code null} when the map leaves them out, which means "use the default".
 * Lists and maps are never {@code null}, only empty.
 *
 * <p>Fields marked "MiniMessage" in {@code docs/MAP_FORMAT.md} hold raw MiniMessage
 * text; this library never parses or strips it.
 */
public record MapManifest(
        Info info,
        World world,
        List<Team> teams,
        List<SpawnGroup> spawns,
        Map<String, Kit> kits,
        List<KitChest> kitChests,
        Classes classes,
        List<Region> regions,
        List<Goal> goals,
        Respawn respawn,
        Bunkers bunkers,
        TimeLimit timeLimit,
        List<VictoryCondition> victory,
        Map<String, Variant> variants
) {
    public MapManifest {
        info = info == null ? Info.EMPTY : info;
        world = world == null ? World.EMPTY : world;
        teams = copy(teams);
        spawns = copy(spawns);
        kits = copy(kits);
        kitChests = copy(kitChests);
        classes = classes == null ? Classes.EMPTY : classes;
        regions = copy(regions);
        goals = copy(goals);
        respawn = respawn == null ? Respawn.EMPTY : respawn;
        victory = victory == null ? null : List.copyOf(victory);
        variants = copy(variants);
    }

    public Optional<Team> team(String id) {
        return teams.stream().filter(team -> team.id() != null && team.id().equals(id)).findFirst();
    }

    public Optional<Region> region(String id) {
        return regions.stream().filter(region -> region.id() != null && region.id().equals(id)).findFirst();
    }

    public Optional<Goal> goal(String id) {
        return goals.stream().filter(goal -> goal.id() != null && goal.id().equals(id)).findFirst();
    }

    /** Spawn points for a team, empty when the map has none for it. */
    public List<Pose> spawnsFor(String teamId) {
        return spawns.stream()
                .filter(group -> teamId != null && teamId.equals(group.team()))
                .flatMap(group -> group.points().stream())
                .toList();
    }

    /** The victory list this map plays with: its own, or the gamemode's default. */
    public List<VictoryCondition> effectiveVictory() {
        if (victory != null) {
            return victory;
        }
        Gamemode gamemode = info.gamemode();
        return gamemode == null ? List.of() : gamemode.defaultVictory();
    }

    // ---- info -------------------------------------------------------------

    /**
     * The top-level fields of map.yml.
     *
     * @param proto      format version, see {@link Proto}
     * @param name       display name (MiniMessage)
     * @param slug       folder-safe id, for example {@code bunkers-ravine}
     * @param version    map version, for example {@code 1.0.0}
     * @param objective  one line telling players how to win (MiniMessage)
     * @param maxPlayers players across all teams, or {@code null}
     */
    public record Info(
            int proto,
            String name,
            String slug,
            String version,
            String objective,
            List<Author> authors,
            Gamemode gamemode,
            Integer maxPlayers,
            Join join
    ) {
        static final Info EMPTY = new Info(Proto.CURRENT, null, null, null, null, List.of(), null, null, null);

        public Info {
            authors = copy(authors);
            join = join == null ? Join.DEFAULT : join;
        }
    }

    /** @param uuid Minecraft account id, optional */
    public record Author(String uuid, String name, String contribution) {
    }

    /** @param late whether players may join after the match started; {@code null} uses the gamemode's rule */
    public record Join(Boolean late) {
        static final Join DEFAULT = new Join(null);
    }

    /** One alternative layout of the same map; {@code overrides} replace whole sections. */
    public record Variant(String name, Map<String, Object> overrides) {
        public Variant {
            overrides = copy(overrides);
        }
    }

    // ---- world ------------------------------------------------------------

    /**
     * @param spawn     where observers appear
     * @param bounds    id of the region players must stay in, optional
     * @param time      fixed world time in ticks, optional
     * @param weather   {@code clear}, {@code rain} or {@code thunder}, optional
     * @param gamerules Minecraft gamerules by name
     */
    public record World(Pose spawn, String bounds, Long time, String weather, Map<String, Object> gamerules) {
        static final World EMPTY = new World(null, null, null, null, Map.of());

        public World {
            gamerules = copy(gamerules);
        }
    }

    // ---- positions --------------------------------------------------------

    /** A standing position. Yaw and pitch are in degrees. */
    public record Pose(double x, double y, double z, float yaw, float pitch) {
    }

    /** One block. */
    public record BlockPos(int x, int y, int z) {
    }

    /** A box between two corner blocks, both included. Corners can be given in any order. */
    public record Cuboid(BlockPos a, BlockPos b) {
        public BlockPos min() {
            return new BlockPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
        }

        public BlockPos max() {
            return new BlockPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
        }

        public boolean contains(double x, double y, double z) {
            BlockPos min = min();
            BlockPos max = max();
            return x >= min.x() && x < max.x() + 1
                    && y >= min.y() && y < max.y() + 1
                    && z >= min.z() && z < max.z() + 1;
        }

        public boolean contains(BlockPos pos) {
            return contains(pos.x(), pos.y(), pos.z());
        }
    }

    // ---- teams and spawns -------------------------------------------------

    /**
     * @param name        short team name (MiniMessage)
     * @param displayName longer name for titles, optional (MiniMessage)
     * @param color       a named color such as {@code red} or a hex color such as {@code #ff5555}
     * @param overfill    extra seats only premium players and staff can take
     * @param role        {@code attacker} or {@code defender}, used by Base Defense
     * @param base        id of this team's base region, used by Bunkers
     */
    public record Team(
            String id,
            String name,
            String displayName,
            String color,
            Integer min,
            Integer max,
            Integer overfill,
            String role,
            String base
    ) {
    }

    public record SpawnGroup(String team, List<Pose> points) {
        public SpawnGroup {
            points = copy(points);
        }
    }

    // ---- kits -------------------------------------------------------------

    /**
     * An inventory given to players.
     *
     * @param armor   {@code helmet}, {@code chestplate}, {@code leggings} and {@code boots}
     * @param items   the rest of the inventory; items without a slot fill the first free one
     * @param offhand item for the off hand, optional
     */
    public record Kit(Map<String, Item> armor, List<Item> items, Item offhand) {
        public static final List<String> ARMOR_SLOTS = List.of("helmet", "chestplate", "leggings", "boots");

        public Kit {
            armor = copy(armor);
            items = copy(items);
        }
    }

    /**
     * One item stack.
     *
     * @param slot         inventory slot 0-35 (0-8 is the hotbar), optional
     * @param material     Minecraft item id such as {@code diamond_sword}
     * @param name         custom name, optional (MiniMessage)
     * @param lore         lore lines (MiniMessage)
     * @param enchantments enchantment id to level
     * @param color        dye color for leather armor, named or hex, optional
     * @param potion       potion type id for potions and tipped arrows, optional
     * @param data         an exact item saved by the map maker plugin, optional; wins over the other fields
     */
    public record Item(
            Integer slot,
            String material,
            Integer amount,
            String name,
            List<String> lore,
            Map<String, Integer> enchantments,
            Boolean unbreakable,
            String color,
            String potion,
            String data
    ) {
        public Item {
            lore = copy(lore);
            enchantments = copy(enchantments);
        }

        public int amountOrOne() {
            return amount == null ? 1 : amount;
        }
    }

    /** Containers inside {@code region} are filled with {@code items} when the match starts. */
    public record KitChest(String region, List<Item> items) {
        public KitChest {
            items = copy(items);
        }
    }

    // ---- classes ----------------------------------------------------------

    /**
     * @param allowed which armor classes players may use; empty means none
     * @param caps    most players per team that may use a class at once
     */
    public record Classes(List<String> allowed, Map<String, Integer> caps) {
        static final Classes EMPTY = new Classes(List.of(), Map.of());

        public Classes {
            allowed = copy(allowed);
            caps = copy(caps);
        }
    }

    // ---- regions ----------------------------------------------------------

    public record Region(String id, Cuboid cuboid, Set<String> flags) {
        /** Nobody can place or break blocks here. */
        public static final String DENY_BUILD = "deny-build";
        /** Players outside this region die. Usually set on the map bounds. */
        public static final String KILL_OUTSIDE = "kill-outside";
        public static final Set<String> KNOWN_FLAGS = Set.of(DENY_BUILD, KILL_OUTSIDE);

        public Region {
            flags = flags == null ? Set.of() : Set.copyOf(flags);
        }

        public boolean has(String flag) {
            return flags.contains(flag);
        }
    }

    // ---- goals ------------------------------------------------------------

    /** Something a team has to do, or stop the other team doing. */
    public sealed interface Goal permits ControlPoint, Core, Wool, Destroyable {
        String id();

        /** Display name (MiniMessage). */
        String name();

        /** The type as written in map.yml. */
        String type();

        /**
         * The team the goal belongs to, or {@code null} for a neutral goal. For cores and
         * destroyables it is the team defending them; for wool it is the team placing it.
         */
        String owner();
    }

    /** A hill held by one team alone for {@code captureTime}. Neutral. */
    public record ControlPoint(String id, String name, String region, Duration captureTime) implements Goal {
        public static final String TYPE = "control-point";

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String owner() {
            return null;
        }
    }

    /**
     * A block casing with lava inside. Enemies leak it by making lava flow {@code leak} blocks
     * below the core.
     */
    public record Core(
            String id,
            String name,
            String owner,
            String material,
            String region,
            Integer leak,
            List<ModeChange> modeChanges
    ) implements Goal {
        public static final String TYPE = "core";

        public Core {
            modeChanges = copy(modeChanges);
        }

        @Override
        public String type() {
            return TYPE;
        }
    }

    /** After {@code after} has passed, the core's blocks turn into {@code material}. */
    public record ModeChange(Duration after, String material) {
    }

    /**
     * A wool the owner must fetch and place on the monument.
     *
     * @param color one of the 16 dye colors, for example {@code cyan}
     * @param room  region whose containers hold this wool, optional
     */
    public record Wool(
            String id,
            String name,
            String owner,
            String color,
            BlockPos monument,
            String room,
            Boolean craftable
    ) implements Goal {
        public static final String TYPE = "wool";

        @Override
        public String type() {
            return TYPE;
        }
    }

    /**
     * Blocks the owner defends. Enemies win it by breaking {@code completion} percent of them.
     */
    public record Destroyable(
            String id,
            String name,
            String owner,
            String region,
            List<String> materials,
            Integer completion,
            Boolean repairable,
            Boolean showProgress
    ) implements Goal {
        public static final String TYPE = "destroyable";

        public Destroyable {
            materials = copy(materials);
        }

        @Override
        public String type() {
            return TYPE;
        }
    }

    // ---- respawn ----------------------------------------------------------

    /**
     * How players come back after dying. {@code teams} overrides the defaults for single teams.
     */
    public record Respawn(Rule defaults, Map<String, Rule> teams) {
        static final Respawn EMPTY = new Respawn(Rule.EMPTY, Map.of());

        public Respawn {
            defaults = defaults == null ? Rule.EMPTY : defaults;
            teams = copy(teams);
        }

        /** The rule for one team: its own values first, then the map's defaults. */
        public Rule forTeam(String teamId) {
            Rule own = teams.get(teamId);
            return own == null ? defaults : own.orElse(defaults);
        }

        /**
         * @param enabled whether the team respawns at all
         * @param delay   wait before respawning
         * @param kit     kit given on every spawn, by name
         * @param lives   respawns per player, {@code null} for unlimited
         */
        public record Rule(Boolean enabled, Duration delay, String kit, Integer lives) {
            static final Rule EMPTY = new Rule(null, null, null, null);

            Rule orElse(Rule fallback) {
                return new Rule(
                        enabled != null ? enabled : fallback.enabled,
                        delay != null ? delay : fallback.delay,
                        kit != null ? kit : fallback.kit,
                        lives != null ? lives : fallback.lives
                );
            }
        }
    }

    // ---- bunkers ----------------------------------------------------------

    /** Rules only Bunkers uses. Anything left out uses the server's default. */
    public record Bunkers(
            Duration grace,
            Double dtr,
            Build build,
            Shops shops,
            List<Mine> mines,
            Subclaims subclaims,
            Economy economy
    ) {
        public Bunkers {
            mines = copy(mines);
        }

        /** @param outsideBases the only blocks players may place outside their own base */
        public record Build(List<String> outsideBases) {
            public Build {
                outsideBases = outsideBases == null ? null : List.copyOf(outsideBases);
            }
        }

        /** @param catalog which shop catalog the server uses, for example {@code default} */
        public record Shops(String catalog, List<ShopNpc> npcs) {
            public Shops {
                npcs = copy(npcs);
            }
        }

        /**
         * @param team the team the shopkeeper belongs to, or {@code null} for a neutral one
         * @param shop which shop it opens, for example {@code gear} or {@code sell}
         */
        public record ShopNpc(String team, String shop, Pose at) {
        }

        /** Ores broken inside {@code region} grow back after {@code respawn}. */
        public record Mine(String region, Duration respawn) {
        }

        /** @param price default price for every chest that does not set its own */
        public record Subclaims(Integer price, List<SubclaimChest> chests) {
            public Subclaims {
                chests = copy(chests);
            }
        }

        public record SubclaimChest(int x, int y, int z, Integer price) {
            public BlockPos pos() {
                return new BlockPos(x, y, z);
            }
        }

        public record Economy(Integer startingBalance, Integer killReward) {
        }
    }

    // ---- ending -----------------------------------------------------------

    /**
     * @param result   who wins when time runs out: {@code tie}, {@code most-dtr},
     *                 {@code most-kills}, {@code most-goals} or a team id
     * @param overtime extra time when the result would be a tie, optional
     */
    public record TimeLimit(Duration duration, String result, Duration overtime) {
        public static final List<String> RESULTS = List.of("tie", "most-dtr", "most-kills", "most-goals");
    }

    /** One way to win. The first condition a team meets ends the match. */
    public sealed interface VictoryCondition
            permits GoalsVictory, ControlPointVictory, LastTeamStanding, TimeLimitVictory {
    }

    /**
     * Complete goals: {@code all-own} (every goal your team owns, as in Capture the Wool),
     * {@code all-enemy} (every goal the other teams own, as in Destroy the Core) or one goal id.
     */
    public record GoalsVictory(String goals) implements VictoryCondition {
        public static final String ALL_OWN = "all-own";
        public static final String ALL_ENEMY = "all-enemy";
    }

    /** Fully capture a control point. {@code goal} is {@code null} for "the map's control point". */
    public record ControlPointVictory(String goal) implements VictoryCondition {
    }

    /** The last team with players still alive wins. */
    public record LastTeamStanding() implements VictoryCondition {
    }

    /** When the time limit runs out, its {@code result} decides. */
    public record TimeLimitVictory() implements VictoryCondition {
    }

    // ---- helpers ----------------------------------------------------------

    private static <T> List<T> copy(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }

    private static <K, V> Map<K, V> copy(Map<K, V> map) {
        return map == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(map));
    }
}
