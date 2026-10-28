package gg.hcfrevival.clash.map;

import gg.hcfrevival.clash.map.MapManifest.Bunkers;
import gg.hcfrevival.clash.map.MapManifest.ControlPoint;
import gg.hcfrevival.clash.map.MapManifest.ControlPointVictory;
import gg.hcfrevival.clash.map.MapManifest.Cooldowns;
import gg.hcfrevival.clash.map.MapManifest.Core;
import gg.hcfrevival.clash.map.MapManifest.Destroyable;
import gg.hcfrevival.clash.map.MapManifest.Goal;
import gg.hcfrevival.clash.map.MapManifest.GoalsVictory;
import gg.hcfrevival.clash.map.MapManifest.Info;
import gg.hcfrevival.clash.map.MapManifest.Item;
import gg.hcfrevival.clash.map.MapManifest.Kit;
import gg.hcfrevival.clash.map.MapManifest.LastTeamStanding;
import gg.hcfrevival.clash.map.MapManifest.Region;
import gg.hcfrevival.clash.map.MapManifest.Respawn;
import gg.hcfrevival.clash.map.MapManifest.Team;
import gg.hcfrevival.clash.map.MapManifest.TimeLimit;
import gg.hcfrevival.clash.map.MapManifest.TimeLimitVictory;
import gg.hcfrevival.clash.map.MapManifest.VictoryCondition;
import gg.hcfrevival.clash.map.MapManifest.Wool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks that a map makes sense: everything it refers to exists and its gamemode has what
 * it needs.
 *
 * <p>These are the checks that need only map.yml. Checks that need the world itself
 * (is the lava really inside the core, are there chests in the wool room) are done by
 * whatever has the world loaded.
 */
public final class MapValidator {
    /** Armor classes a map can allow. Others are reported but not refused. */
    public static final Set<String> KNOWN_CLASSES = Set.of("archer", "bard", "rogue", "guardian", "miner", "diver");

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern RESOURCE = Pattern.compile("(minecraft:)?[a-z0-9_./-]+");
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}");
    private static final Set<String> WEATHER = Set.of("clear", "rain", "thunder");
    private static final Set<String> ROLES = Set.of("attacker", "defender");

    /** Checks a map and each of its variants. */
    public List<Problem> validate(MapManifest manifest) {
        List<Problem> problems = new ArrayList<>(new Check(manifest, "", true).run());
        MapManifestReader reader = new MapManifestReader();
        manifest.variants().forEach((id, variant) -> {
            String prefix = "variants." + id;
            MapManifestReader.Result result = reader.readVariant(manifest, id);
            List<Problem> found = result.manifest() == null
                    ? result.problems()
                    : concat(result.problems(), new Check(result.manifest(), prefix, false).run());
            found.forEach(problem -> problems.add(new Problem(problem.severity(),
                    problem.path().startsWith(prefix) ? problem.path() : prefix + "." + problem.path(),
                    problem.message())));
        });
        return List.copyOf(problems);
    }

    public static boolean hasErrors(List<Problem> problems) {
        return problems.stream().anyMatch(Problem::isError);
    }

    private static List<Problem> concat(List<Problem> a, List<Problem> b) {
        List<Problem> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** One run over one manifest. */
    private static final class Check {
        private final MapManifest map;
        private final String variant;
        private final boolean base;
        private final List<Problem> problems = new ArrayList<>();
        private final Set<String> teamIds = new HashSet<>();
        private final Set<String> regionIds = new HashSet<>();

        Check(MapManifest map, String variant, boolean base) {
            this.map = map;
            this.variant = variant;
            this.base = base;
        }

        List<Problem> run() {
            map.teams().forEach(team -> {
                if (team.id() != null) {
                    teamIds.add(team.id());
                }
            });
            map.regions().forEach(region -> {
                if (region.id() != null) {
                    regionIds.add(region.id());
                }
            });
            if (base) {
                info();
            }
            world();
            teams();
            spawns();
            regions();
            kits();
            classes();
            goals();
            respawn();
            gamemode();
            bunkers();
            timeLimit();
            cooldowns();
            victory();
            return problems;
        }

        // ---- sections -----------------------------------------------------

        private void info() {
            Info info = map.info();
            require("name", info.name());
            if (require("slug", info.slug()) && !SLUG.matcher(info.slug()).matches()) {
                error("slug", "use lowercase letters, numbers and dashes only, for example bunkers-ravine.");
            }
            require("version", info.version());
            if (info.gamemode() == null) {
                error("gamemode", "missing or unknown. Use one of " + String.join(", ", Gamemode.ids()) + ".");
            }
            if (info.objective() == null) {
                warning("objective", "missing. Players see this line to learn how to win.");
            }
            if (info.authors().isEmpty()) {
                warning("authors", "nobody is credited for this map.");
            }
            for (int i = 0; i < info.authors().size(); i++) {
                MapManifest.Author author = info.authors().get(i);
                require("authors[" + i + "].name", author.name());
                if (author.uuid() != null && !UUID.matcher(author.uuid()).matches()) {
                    error("authors[" + i + "].uuid", "is not a Minecraft account id.");
                }
            }
            if (info.maxPlayers() != null && info.maxPlayers() < 2) {
                error("max-players", "should be at least 2.");
            }
            map.variants().forEach((id, variant) -> {
                if (!ID.matcher(id).matches()) {
                    error("variants." + id, "use lowercase letters, numbers, dashes and underscores for variant ids.");
                }
            });
        }

        private void world() {
            if (map.world().spawn() == null) {
                error("world.spawn", "missing. This is where observers appear.");
            }
            if (map.world().bounds() != null) {
                region("world.bounds", map.world().bounds());
            }
            if (map.world().weather() != null && !WEATHER.contains(map.world().weather())) {
                error("world.weather", "should be clear, rain or thunder.");
            }
            if (map.world().time() != null && map.world().time() < 0) {
                error("world.time", "should be 0 or more.");
            }
        }

        private void teams() {
            if (map.teams().size() < 2) {
                error("teams", "a map needs at least two teams.");
            }
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < map.teams().size(); i++) {
                Team team = map.teams().get(i);
                String path = "teams[" + i + "]";
                if (require(path + ".id", team.id())) {
                    if (!ID.matcher(team.id()).matches()) {
                        error(path + ".id", "use lowercase letters, numbers, dashes and underscores.");
                    }
                    if (!seen.add(team.id())) {
                        error(path + ".id", "another team already uses the id " + team.id() + ".");
                    }
                }
                require(path + ".name", team.name());
                if (require(path + ".color", team.color()) && !MapColors.isColor(team.color())) {
                    error(path + ".color", "should be a color name such as red or a hex color such as #ff5555.");
                }
                if (team.max() == null) {
                    error(path + ".max", "missing. How many players can be on this team?");
                } else if (team.max() < 1) {
                    error(path + ".max", "should be at least 1.");
                }
                if (team.min() != null && team.min() < 0) {
                    error(path + ".min", "should be 0 or more.");
                }
                if (team.min() != null && team.max() != null && team.min() > team.max()) {
                    error(path + ".min", "is more than max.");
                }
                if (team.overfill() != null && team.overfill() < 0) {
                    error(path + ".overfill", "should be 0 or more.");
                }
                if (team.role() != null && !ROLES.contains(team.role())) {
                    error(path + ".role", "should be attacker or defender.");
                }
                if (team.base() != null) {
                    region(path + ".base", team.base());
                }
            }
        }

        private void spawns() {
            for (int i = 0; i < map.spawns().size(); i++) {
                MapManifest.SpawnGroup group = map.spawns().get(i);
                String path = "spawns[" + i + "]";
                if (require(path + ".team", group.team())) {
                    team(path + ".team", group.team());
                }
                if (group.points().isEmpty()) {
                    error(path + ".points", "add at least one spawn point.");
                }
            }
            for (Team team : map.teams()) {
                if (team.id() != null && map.spawnsFor(team.id()).isEmpty()) {
                    error("spawns", "team " + team.id() + " has nowhere to spawn.");
                }
            }
        }

        private void regions() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < map.regions().size(); i++) {
                Region region = map.regions().get(i);
                String path = "regions[" + i + "]";
                if (require(path + ".id", region.id())) {
                    if (!ID.matcher(region.id()).matches()) {
                        error(path + ".id", "use lowercase letters, numbers, dashes and underscores.");
                    }
                    if (!seen.add(region.id())) {
                        error(path + ".id", "another region already uses the id " + region.id() + ".");
                    }
                }
                if (region.cuboid() == null) {
                    error(path + ".cuboid", "missing. Give the two corners a and b.");
                }
                for (String flag : region.flags()) {
                    if (!Region.KNOWN_FLAGS.contains(flag)) {
                        warning(path + ".flags", "unknown flag " + flag + ", it does nothing. Known flags: "
                                + String.join(", ", Region.KNOWN_FLAGS.stream().sorted().toList()) + ".");
                    }
                }
            }
        }

        private void kits() {
            map.kits().forEach((name, kit) -> {
                String path = "kits." + name;
                if (!ID.matcher(name).matches()) {
                    error(path, "use lowercase letters, numbers, dashes and underscores for kit names.");
                }
                kit.armor().forEach((slot, item) -> item(path + ".armor." + slot, item));
                for (int i = 0; i < kit.items().size(); i++) {
                    item(path + ".items[" + i + "]", kit.items().get(i));
                }
                if (kit.offhand() != null) {
                    item(path + ".offhand", kit.offhand());
                }
                Set<Integer> slots = new HashSet<>();
                for (Item item : kit.items()) {
                    if (item.slot() != null && !slots.add(item.slot())) {
                        error(path + ".items", "two items use slot " + item.slot() + ".");
                    }
                }
            });
            for (int i = 0; i < map.kitChests().size(); i++) {
                MapManifest.KitChest chest = map.kitChests().get(i);
                String path = "kit-chests[" + i + "]";
                if (require(path + ".region", chest.region())) {
                    region(path + ".region", chest.region());
                }
                if (chest.items().isEmpty()) {
                    warning(path + ".items", "this chest gets nothing.");
                }
                for (int j = 0; j < chest.items().size(); j++) {
                    item(path + ".items[" + j + "]", chest.items().get(j));
                }
            }
        }

        private void item(String path, Item item) {
            if (item.data() != null) {
                return;
            }
            if (item.material() == null) {
                error(path + ".material", "missing.");
            } else if (!RESOURCE.matcher(item.material()).matches()) {
                error(path + ".material", item.material() + " is not a Minecraft item id such as diamond_sword.");
            }
            if (item.amount() != null && (item.amount() < 1 || item.amount() > 99)) {
                error(path + ".amount", "should be between 1 and 99.");
            }
            if (item.slot() != null && (item.slot() < 0 || item.slot() > 35)) {
                error(path + ".slot", "should be between 0 and 35 (0 to 8 is the hotbar).");
            }
            item.enchantments().forEach((id, level) -> {
                if (!RESOURCE.matcher(id).matches()) {
                    error(path + ".enchantments", id + " is not an enchantment id such as sharpness.");
                }
                if (level < 1 || level > 255) {
                    error(path + ".enchantments." + id, "level should be between 1 and 255.");
                }
            });
            if (item.color() != null && !MapColors.isColor(item.color())) {
                error(path + ".color", "should be a color name or a hex color such as #ff5555.");
            }
            if (item.potion() != null && !RESOURCE.matcher(item.potion()).matches()) {
                error(path + ".potion", item.potion() + " is not a potion id such as strong_healing.");
            }
        }

        private void classes() {
            for (String id : map.classes().allowed()) {
                if (!KNOWN_CLASSES.contains(id)) {
                    warning("classes.allowed", "unknown class " + id + ".");
                }
            }
            map.classes().caps().forEach((id, cap) -> {
                if (!map.classes().allowed().contains(id)) {
                    warning("classes.caps." + id, "this class is not in the allowed list, so the cap does nothing.");
                }
                if (cap < 0) {
                    error("classes.caps." + id, "should be 0 or more.");
                }
            });
        }

        private void goals() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < map.goals().size(); i++) {
                Goal goal = map.goals().get(i);
                String path = "goals[" + i + "]";
                if (require(path + ".id", goal.id())) {
                    if (!ID.matcher(goal.id()).matches()) {
                        error(path + ".id", "use lowercase letters, numbers, dashes and underscores.");
                    }
                    if (!seen.add(goal.id())) {
                        error(path + ".id", "another goal already uses the id " + goal.id() + ".");
                    }
                }
                switch (goal) {
                    case ControlPoint point -> {
                        if (require(path + ".region", point.region())) {
                            region(path + ".region", point.region());
                        }
                        positive(path + ".capture-time", point.captureTime());
                    }
                    case Core core -> {
                        owner(path, core.owner());
                        if (require(path + ".region", core.region())) {
                            region(path + ".region", core.region());
                        }
                        material(path + ".material", core.material());
                        if (core.leak() != null && core.leak() < 1) {
                            error(path + ".leak", "should be at least 1.");
                        }
                        for (int j = 0; j < core.modeChanges().size(); j++) {
                            MapManifest.ModeChange change = core.modeChanges().get(j);
                            String changePath = path + ".mode-changes[" + j + "]";
                            if (change.after() == null) {
                                error(changePath + ".after", "missing.");
                            }
                            if (require(changePath + ".material", change.material())) {
                                material(changePath + ".material", change.material());
                            }
                        }
                    }
                    case Wool wool -> {
                        owner(path, wool.owner());
                        if (require(path + ".color", wool.color()) && !MapColors.isDye(wool.color())) {
                            error(path + ".color", "should be a dye color such as cyan or light_blue.");
                        }
                        if (wool.monument() == null) {
                            error(path + ".monument", "missing. Where is this wool placed?");
                        }
                        if (wool.room() != null) {
                            region(path + ".room", wool.room());
                        }
                    }
                    case Destroyable destroyable -> {
                        owner(path, destroyable.owner());
                        if (require(path + ".region", destroyable.region())) {
                            region(path + ".region", destroyable.region());
                        }
                        if (destroyable.materials().isEmpty()) {
                            error(path + ".materials", "list the blocks that count, for example [obsidian].");
                        }
                        destroyable.materials().forEach(material -> material(path + ".materials", material));
                        Integer completion = destroyable.completion();
                        if (completion != null && (completion < 1 || completion > 100)) {
                            error(path + ".completion", "should be between 1% and 100%.");
                        }
                    }
                }
            }
        }

        private void respawn() {
            Respawn respawn = map.respawn();
            rule("respawn", respawn.defaults());
            respawn.teams().forEach((id, rule) -> {
                team("respawn.teams." + id, id);
                rule("respawn.teams." + id, rule);
            });
        }

        private void rule(String path, Respawn.Rule rule) {
            if (rule.kit() != null && !map.kits().containsKey(rule.kit())) {
                error(path + ".kit", "there is no kit called " + rule.kit() + ".");
            }
            if (rule.lives() != null && rule.lives() < 1) {
                error(path + ".lives", "should be at least 1. Leave it out for unlimited lives.");
            }
            if (rule.delay() != null && rule.delay().isNegative()) {
                error(path + ".delay", "cannot be negative.");
            }
        }

        private void gamemode() {
            Gamemode gamemode = map.info().gamemode();
            if (gamemode == null) {
                return;
            }
            switch (gamemode) {
                case BUNKERS -> {
                    long points = count(ControlPoint.class);
                    if (points != 1) {
                        error("goals", "Bunkers needs exactly one control-point goal (the KOTH), this map has " + points + ".");
                    }
                    for (int i = 0; i < map.teams().size(); i++) {
                        if (map.teams().get(i).base() == null) {
                            error("teams[" + i + "].base", "missing. Every Bunkers team needs a base region.");
                        }
                    }
                    others(ControlPoint.class);
                }
                case DESTROY_THE_CORE -> {
                    if (count(Core.class) == 0) {
                        error("goals", "Destroy the Core needs at least one core goal.");
                    }
                    everyTeamOwns(Core.class, "core");
                    others(Core.class);
                }
                case CAPTURE_THE_WOOL -> {
                    if (count(Wool.class) == 0) {
                        error("goals", "Capture the Wool needs at least one wool goal.");
                    }
                    everyTeamOwns(Wool.class, "wool");
                    others(Wool.class);
                }
                case BASE_DEFENSE -> {
                    List<Team> attackers = map.teams().stream().filter(team -> "attacker".equals(team.role())).toList();
                    List<Team> defenders = map.teams().stream().filter(team -> "defender".equals(team.role())).toList();
                    if (attackers.isEmpty() || defenders.isEmpty()) {
                        error("teams", "Base Defense needs a team with role: attacker and a team with role: defender.");
                    }
                    if (count(Destroyable.class) == 0) {
                        error("goals", "Base Defense needs at least one destroyable goal.");
                    }
                    for (int i = 0; i < map.goals().size(); i++) {
                        if (map.goals().get(i) instanceof Destroyable destroyable && destroyable.owner() != null
                                && defenders.stream().noneMatch(team -> destroyable.owner().equals(team.id()))) {
                            error("goals[" + i + "].owner", "in Base Defense the defending team owns the destroyables.");
                        }
                    }
                    others(Destroyable.class);
                }
            }
            if (gamemode != Gamemode.BUNKERS && map.bunkers() != null) {
                warning("bunkers", "only Bunkers maps use this section, it will be ignored.");
            }
        }

        private void bunkers() {
            Bunkers bunkers = map.bunkers();
            if (bunkers == null) {
                return;
            }
            if (bunkers.grace() != null && bunkers.grace().isNegative()) {
                error("bunkers.grace", "cannot be negative.");
            }
            if (bunkers.dtr() != null && bunkers.dtr() <= 0) {
                error("bunkers.dtr", "should be more than 0.");
            }
            if (bunkers.build() != null && bunkers.build().outsideBases() != null) {
                bunkers.build().outsideBases().forEach(material -> material("bunkers.build.outside-bases", material));
            }
            if (bunkers.shops() != null) {
                for (int i = 0; i < bunkers.shops().npcs().size(); i++) {
                    Bunkers.ShopNpc npc = bunkers.shops().npcs().get(i);
                    String path = "bunkers.shops.npcs[" + i + "]";
                    if (npc.team() != null) {
                        team(path + ".team", npc.team());
                    }
                    require(path + ".shop", npc.shop());
                    if (npc.profession() != null && !Bunkers.ShopNpc.PROFESSIONS.contains(npc.profession())) {
                        error(path + ".profession", npc.profession() + " is not a villager profession such as armorer.");
                    }
                    if (npc.biome() != null && !Bunkers.ShopNpc.BIOMES.contains(npc.biome())) {
                        error(path + ".biome", npc.biome() + " is not a villager biome such as plains.");
                    }
                    if (npc.at() == null) {
                        error(path + ".at", "missing. Where does the shopkeeper stand?");
                    }
                }
            }
            for (int i = 0; i < bunkers.mines().size(); i++) {
                Bunkers.Mine mine = bunkers.mines().get(i);
                String path = "bunkers.mines[" + i + "]";
                if (require(path + ".region", mine.region())) {
                    region(path + ".region", mine.region());
                }
                positive(path + ".respawn", mine.respawn());
            }
            if (bunkers.subclaims() != null) {
                Bunkers.Subclaims subclaims = bunkers.subclaims();
                if (subclaims.price() != null && subclaims.price() < 0) {
                    error("bunkers.subclaims.price", "cannot be negative.");
                }
                Set<MapManifest.BlockPos> seen = new HashSet<>();
                for (int i = 0; i < subclaims.chests().size(); i++) {
                    Bunkers.SubclaimChest chest = subclaims.chests().get(i);
                    String path = "bunkers.subclaims.chests[" + i + "]";
                    if (!seen.add(chest.pos())) {
                        error(path, "this chest is listed twice.");
                    }
                    if (chest.price() != null && chest.price() < 0) {
                        error(path + ".price", "cannot be negative.");
                    }
                    boolean inBase = map.teams().stream()
                            .map(Team::base)
                            .filter(java.util.Objects::nonNull)
                            .flatMap(base -> map.region(base).stream())
                            .anyMatch(region -> region.cuboid() != null && region.cuboid().contains(chest.pos()));
                    if (!inBase) {
                        warning(path, "this chest is not inside any team's base.");
                    }
                }
            }
            if (bunkers.economy() != null) {
                if (bunkers.economy().startingBalance() != null && bunkers.economy().startingBalance() < 0) {
                    error("bunkers.economy.starting-balance", "cannot be negative.");
                }
                if (bunkers.economy().killReward() != null && bunkers.economy().killReward() < 0) {
                    error("bunkers.economy.kill-reward", "cannot be negative.");
                }
            }
        }

        private void timeLimit() {
            TimeLimit limit = map.timeLimit();
            if (limit == null) {
                return;
            }
            if (limit.duration() == null) {
                error("time-limit.duration", "missing.");
            } else {
                positive("time-limit.duration", limit.duration());
            }
            if (limit.result() != null && !TimeLimit.RESULTS.contains(limit.result()) && !teamIds.contains(limit.result())) {
                error("time-limit.result", "should be " + String.join(", ", TimeLimit.RESULTS) + " or a team id.");
            }
            if (limit.overtime() != null) {
                positive("time-limit.overtime", limit.overtime());
            }
        }

        private void cooldowns() {
            Cooldowns cooldowns = map.cooldowns();
            if (cooldowns == null) {
                return;
            }
            nonNegative("cooldowns.enderpearl", cooldowns.enderpearl());
            nonNegative("cooldowns.crapple", cooldowns.crapple());
            nonNegative("cooldowns.gapple", cooldowns.gapple());
            nonNegative("cooldowns.totem", cooldowns.totem());
            nonNegative("cooldowns.wind-charge", cooldowns.windCharge());
        }

        private void victory() {
            boolean explicit = map.victory() != null;
            List<VictoryCondition> conditions = map.effectiveVictory();
            boolean resolvable = false;
            for (int i = 0; i < conditions.size(); i++) {
                String path = explicit ? "victory[" + i + "]" : "victory";
                String reason = unresolved(conditions.get(i));
                if (reason == null) {
                    resolvable = true;
                } else if (explicit) {
                    error(path, reason);
                }
            }
            if (!resolvable && map.info().gamemode() != null) {
                error("victory", "nobody can win this map. Add the goals its gamemode needs or a victory list.");
            }
        }

        /** Why a condition can never happen on this map, or {@code null} when it can. */
        private String unresolved(VictoryCondition condition) {
            return switch (condition) {
                case LastTeamStanding ignored -> null;
                case TimeLimitVictory ignored -> map.timeLimit() == null ? "there is no time-limit section." : null;
                case ControlPointVictory point -> {
                    if (point.goal() == null) {
                        yield count(ControlPoint.class) > 0 ? null : "there is no control-point goal.";
                    }
                    yield map.goal(point.goal()).filter(ControlPoint.class::isInstance).isPresent()
                            ? null : "there is no control-point goal called " + point.goal() + ".";
                }
                case GoalsVictory goals -> switch (goals.goals()) {
                    case GoalsVictory.ALL_OWN, GoalsVictory.ALL_ENEMY ->
                            map.goals().stream().anyMatch(goal -> goal.owner() != null) ? null : "there are no team goals.";
                    default -> map.goal(goals.goals()).isPresent() ? null : "there is no goal called " + goals.goals() + ".";
                };
            };
        }

        // ---- helpers ------------------------------------------------------

        private long count(Class<? extends Goal> type) {
            return map.goals().stream().filter(type::isInstance).count();
        }

        private void others(Class<? extends Goal> expected) {
            for (int i = 0; i < map.goals().size(); i++) {
                Goal goal = map.goals().get(i);
                if (!expected.isInstance(goal)) {
                    warning("goals[" + i + "]", map.info().gamemode().displayName() + " does not use "
                            + goal.type() + " goals, it will be ignored.");
                }
            }
        }

        private void everyTeamOwns(Class<? extends Goal> type, String noun) {
            for (Team team : map.teams()) {
                if (team.id() != null && map.goals().stream()
                        .noneMatch(goal -> type.isInstance(goal) && team.id().equals(goal.owner()))) {
                    warning("goals", "team " + team.id() + " has no " + noun + ".");
                }
            }
        }

        private void owner(String path, String owner) {
            if (require(path + ".owner", owner)) {
                team(path + ".owner", owner);
            }
        }

        private void team(String path, String id) {
            if (!teamIds.contains(id)) {
                error(path, "there is no team called " + id + ".");
            }
        }

        private void region(String path, String id) {
            if (!regionIds.contains(id)) {
                error(path, "there is no region called " + id + ".");
            }
        }

        private void material(String path, String material) {
            if (material != null && !RESOURCE.matcher(material).matches()) {
                error(path, material + " is not a Minecraft block id such as obsidian.");
            }
        }

        private void positive(String path, Duration duration) {
            if (duration != null && (duration.isNegative() || duration.isZero())) {
                error(path, "should be longer than 0s.");
            }
        }

        private void nonNegative(String path, Duration duration) {
            if (duration != null && duration.isNegative()) {
                error(path, "cannot be negative.");
            }
        }

        private boolean require(String path, String value) {
            if (value == null || value.isBlank()) {
                error(path, "missing.");
                return false;
            }
            return true;
        }

        private void error(String path, String message) {
            problems.add(Problem.error(path, message));
        }

        private void warning(String path, String message) {
            problems.add(Problem.warning(path, message));
        }
    }
}
