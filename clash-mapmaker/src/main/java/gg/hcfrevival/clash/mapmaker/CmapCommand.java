package gg.hcfrevival.clash.mapmaker;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import gg.hcfrevival.clash.map.Gamemode;
import gg.hcfrevival.clash.map.MapColors;
import gg.hcfrevival.clash.map.MapManifest;
import gg.hcfrevival.clash.map.MapManifest.Author;
import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.ShopNpc;
import gg.hcfrevival.clash.map.MapManifest.ControlPoint;
import gg.hcfrevival.clash.map.MapManifest.Core;
import gg.hcfrevival.clash.map.MapManifest.Cuboid;
import gg.hcfrevival.clash.map.MapManifest.Destroyable;
import gg.hcfrevival.clash.map.MapManifest.Goal;
import gg.hcfrevival.clash.map.MapManifest.Pose;
import gg.hcfrevival.clash.map.MapManifest.Team;
import gg.hcfrevival.clash.map.MapManifest.Wool;
import gg.hcfrevival.clash.map.MapManifestReader;
import gg.hcfrevival.clash.map.MapManifestWriter;
import gg.hcfrevival.clash.map.MapValidator;
import gg.hcfrevival.clash.map.Problem;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.util.TriState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** The {@code /cmap} tree. Each leaf reads its arguments, calls one {@link Draft} or {@link Session} method and reports. */
final class CmapCommand {
    private static final int LOOK_RANGE = 8;
    private final MapmakerPlugin plugin;

    CmapCommand(MapmakerPlugin plugin) {
        this.plugin = plugin;
    }

    LiteralCommandNode<CommandSourceStack> build() {
        SuggestionProvider<CommandSourceStack> teams = fromDraft(Draft::teamIds);
        SuggestionProvider<CommandSourceStack> regions = fromDraft(Draft::regionIds);
        SuggestionProvider<CommandSourceStack> goals = fromDraft(Draft::goalIds);
        SuggestionProvider<CommandSourceStack> colors = fixed(MapColors.NAMED.keySet());

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cmap")
                .requires(source -> source.getSender().hasPermission("clash.mapmaker"))
                .executes(ctx -> help(ctx.getSource().getSender()));
        root.then(Commands.literal("wand").executes(player((ctx, player) -> {
            player.getInventory().addItem(plugin.wand().item());
            Msg.ok(player, "Left-click a block for corner A, right-click for corner B.");
        })));
        root.then(Commands.literal("world").then(word("slug").executes(ctx -> world(ctx, "void"))
                .then(word("type").suggests(fixed(List.of("void", "flat"))).executes(ctx -> world(ctx, str(ctx, "type"))))));
        root.then(Commands.literal("new").then(word("slug").then(word("gamemode").suggests(fixed(Gamemode.ids())).executes(player(this::newMap)))));
        root.then(Commands.literal("open").then(word("slug").suggests(fixed(plugin::savedSlugs)).executes(player(this::open))));
        root.then(Commands.literal("close").executes(session((ctx, player, session) -> close(player, session, false)))
                .then(Commands.literal("confirm").executes(session((ctx, player, session) -> close(player, session, true)))));
        root.then(Commands.literal("info").executes(session((ctx, player, session) -> info(player, session))));
        root.then(Commands.literal("name").then(text("text").executes(session((ctx, player, session) -> {
            session.draft.name = str(ctx, "text");
            Msg.ok(player, "The map is now called " + session.draft.name + ".");
        }))));
        root.then(Commands.literal("version").then(word("version").executes(session((ctx, player, session) -> {
            session.draft.version = str(ctx, "version");
            Msg.ok(player, "Version " + session.draft.version + ".");
        }))));
        root.then(Commands.literal("objective").then(text("text").executes(session((ctx, player, session) -> {
            session.draft.objective = str(ctx, "text");
            Msg.ok(player, "Objective set.");
        }))));

        root.then(Commands.literal("team")
                .then(Commands.literal("add").then(chain(session((ctx, player, session) -> {
                    String id = str(ctx, "id");
                    session.draft.addTeam(id, str(ctx, "color"), IntegerArgumentType.getInteger(ctx, "max"), optional(ctx, "name"));
                    Msg.ok(player, "Added team " + id + ". Now /cmap spawn add " + id + " and /cmap bunkers base " + id + " (Bunkers).");
                }), 3, word("id"), word("color").suggests(colors), integer("max", 1), text("name"))))
                .then(Commands.literal("edit").then(word("id").suggests(teams).then(word("key").suggests(fixed(Draft.TEAM_KEYS))
                        .then(text("value").executes(session((ctx, player, session) -> {
                            session.draft.editTeam(str(ctx, "id"), str(ctx, "key"), str(ctx, "value"));
                            Msg.ok(player, "Team " + str(ctx, "id") + " updated.");
                        }))))))
                .then(Commands.literal("remove").then(word("id").suggests(teams).executes(session((ctx, player, session) -> {
                    session.draft.removeTeam(str(ctx, "id"));
                    Msg.ok(player, "Removed team " + str(ctx, "id") + " and its spawns.");
                })))));

        root.then(Commands.literal("spawn")
                .then(Commands.literal("add").then(word("team").suggests(fromDraft(draft -> with(draft.teamIds(), "spectator")))
                        .executes(session((ctx, player, session) -> {
                            String team = str(ctx, "team");
                            Pose pose = Msg.pose(player.getLocation());
                            if (team.equals("spectator")) {
                                session.draft.spawn = pose;
                            } else {
                                session.draft.addSpawn(team, pose);
                            }
                            Msg.ok(player, "Spawn for " + team + " at " + Msg.at(pose) + " facing " + (int) pose.yaw() + ".");
                        }))))
                .then(Commands.literal("clear").then(word("team").suggests(teams).executes(session((ctx, player, session) -> {
                    session.draft.clearSpawns(str(ctx, "team"));
                    Msg.ok(player, "Cleared the spawns of " + str(ctx, "team") + ".");
                })))));

        root.then(Commands.literal("region")
                .then(Commands.literal("add").then(chain(session((ctx, player, session) -> {
                    Set<String> flags = new LinkedHashSet<>();
                    Set<String> keepOut = new LinkedHashSet<>();
                    for (String token : Draft.list(optional(ctx, "flags"))) {
                        if (token.startsWith("keep-out:")) {
                            keepOut.addAll(Draft.list(token.substring("keep-out:".length()).replace(',', ' ')));
                        } else {
                            flags.add(token);
                        }
                    }
                    Cuboid cuboid = session.selection();
                    session.draft.putRegion(str(ctx, "id"), cuboid, flags, keepOut);
                    Msg.ok(player, "Region " + str(ctx, "id") + ": " + size(cuboid) + ".");
                }), 1, word("id"), text("flags").suggests(fixed(List.of("deny-build", "kill-outside", "keep-out:"))))))
                .then(Commands.literal("remove").then(word("id").suggests(regions).executes(session((ctx, player, session) -> {
                    session.draft.removeRegion(str(ctx, "id"));
                    Msg.ok(player, "Removed region " + str(ctx, "id") + ".");
                })))));

        root.then(Commands.literal("goal")
                .then(Commands.literal("add")
                        .then(Commands.literal("control-point").then(chain(session((ctx, player, session) -> {
                            String id = str(ctx, "id");
                            String time = optional(ctx, "capture-time");
                            putGoal(player, session, new ControlPoint(id, Msg.capitalise(id), str(ctx, "region"),
                                    time == null ? null : Draft.duration(time)));
                        }), 2, word("id"), word("region").suggests(regions), word("capture-time"))))
                        .then(Commands.literal("core").then(chain(session((ctx, player, session) -> {
                            String id = str(ctx, "id");
                            String material = optional(ctx, "material");
                            String leak = optional(ctx, "leak");
                            putGoal(player, session, new Core(id, Msg.capitalise(id), str(ctx, "owner"), material == null ? null : block(material),
                                    str(ctx, "region"), leak == null ? null : Draft.integer(leak), List.of()));
                        }), 3, word("id"), word("owner").suggests(teams), word("region").suggests(regions), word("material"), word("leak"))))
                        .then(Commands.literal("wool").then(chain(session((ctx, player, session) -> {
                            String id = str(ctx, "id");
                            Block monument = lookingAt(player);
                            putGoal(player, session, new Wool(id, Msg.capitalise(id), str(ctx, "owner"), str(ctx, "color"),
                                    new BlockPos(monument.getX(), monument.getY(), monument.getZ()), optional(ctx, "room"), null));
                        }), 3, word("id"), word("owner").suggests(teams), word("color").suggests(fixed(MapColors.DYES)), word("room").suggests(regions))))
                        .then(Commands.literal("destroyable").then(chain(session((ctx, player, session) -> {
                            String id = str(ctx, "id");
                            List<String> materials = Draft.list(str(ctx, "materials").replace(',', ' ')).stream().map(this::block).toList();
                            String completion = optional(ctx, "completion");
                            putGoal(player, session, new Destroyable(id, Msg.capitalise(id), str(ctx, "owner"), str(ctx, "region"), materials,
                                    completion == null ? null : Draft.integer(completion.replace("%", "")), null, null));
                        }), 4, word("id"), word("owner").suggests(teams), word("region").suggests(regions), word("materials"), word("completion")))))
                .then(Commands.literal("remove").then(word("id").suggests(goals).executes(session((ctx, player, session) -> {
                    session.draft.removeGoal(str(ctx, "id"));
                    Msg.ok(player, "Removed goal " + str(ctx, "id") + ".");
                })))));

        root.then(Commands.literal("kit")
                .then(Commands.literal("set").then(word("name").executes(session((ctx, player, session) -> {
                    MapManifest.Kit kit = Items.of(player.getInventory());
                    session.draft.putKit(str(ctx, "name"), kit);
                    Msg.ok(player, "Kit " + str(ctx, "name") + ": " + kit.armor().size() + " armor pieces, " + kit.items().size() + " items"
                            + (kit.offhand() == null ? "." : " and an off-hand item."));
                }))))
                .then(Commands.literal("remove").then(word("name").suggests(fromDraft(draft -> draft.kits.keySet())).executes(session((ctx, player, session) -> {
                    session.draft.removeKit(str(ctx, "name"));
                    Msg.ok(player, "Removed kit " + str(ctx, "name") + ".");
                })))));

        root.then(Commands.literal("classes")
                .then(Commands.literal("allow").then(text("classes").suggests(fixed(MapValidator.KNOWN_CLASSES)).executes(session((ctx, player, session) -> {
                    session.draft.allowClasses(Draft.list(str(ctx, "classes")));
                    Msg.ok(player, "Classes allowed: " + String.join(", ", session.draft.classes.allowed()) + ".");
                }))))
                .then(Commands.literal("cap").then(word("class").suggests(fixed(MapValidator.KNOWN_CLASSES)).then(integer("cap", 0)
                        .executes(session((ctx, player, session) -> {
                            session.draft.capClass(str(ctx, "class"), IntegerArgumentType.getInteger(ctx, "cap"));
                            Msg.ok(player, "At most " + IntegerArgumentType.getInteger(ctx, "cap") + " " + str(ctx, "class") + " per team.");
                        }))))));

        root.then(Commands.literal("bunkers")
                .then(Commands.literal("base").then(word("team").suggests(teams).executes(session((ctx, player, session) -> {
                    Cuboid cuboid = session.selection();
                    session.draft.setBase(str(ctx, "team"), cuboid);
                    Msg.ok(player, "Base of " + str(ctx, "team") + " is region " + str(ctx, "team") + "-base: " + size(cuboid) + ".");
                }))))
                .then(Commands.literal("shop").then(chain(session((ctx, player, session) -> {
                    String team = str(ctx, "team").equals("none") ? null : str(ctx, "team");
                    Pose at = Msg.pose(player.getLocation());
                    session.draft.addShop(team, str(ctx, "shop"), at, optional(ctx, "profession"), optional(ctx, "biome"));
                    Msg.ok(player, (team == null ? "Neutral " : team + " ") + str(ctx, "shop") + " shopkeeper at " + Msg.at(at) + ".");
                }), 2, word("team").suggests(fromDraft(draft -> with(draft.teamIds(), "none"))), word("shop").suggests(fixed(List.of("gear", "sell", "enchanter"))),
                        word("profession").suggests(fixed(ShopNpc.PROFESSIONS)), word("biome").suggests(fixed(ShopNpc.BIOMES)))))
                .then(Commands.literal("mine").then(chain(session((ctx, player, session) -> {
                    String respawn = optional(ctx, "respawn");
                    Cuboid cuboid = session.selection();
                    session.draft.addMine(str(ctx, "id"), cuboid, respawn == null ? null : Draft.duration(respawn));
                    Msg.ok(player, "Mine " + str(ctx, "id") + ": " + size(cuboid) + ".");
                }), 1, word("id"), word("respawn"))))
                .then(Commands.literal("subclaim").then(chain(session((ctx, player, session) -> {
                    Block block = lookingAt(player);
                    if (!(block.getState(false) instanceof Container)) {
                        throw Draft.bad("Look at a chest or another container.");
                    }
                    String price = optional(ctx, "price");
                    session.draft.putSubclaim(new BlockPos(block.getX(), block.getY(), block.getZ()), price == null ? null : Draft.integer(price));
                    Msg.ok(player, "Subclaim chest at " + block.getX() + ", " + block.getY() + ", " + block.getZ()
                            + (price == null ? " at the default price." : " for " + price + "."));
                }), 0, word("price"))))
                .then(Commands.literal("rules").then(word("key").suggests(fixed(Draft.BUNKERS_KEYS)).then(text("value").executes(session((ctx, player, session) -> {
                    String key = str(ctx, "key");
                    String value = str(ctx, "value");
                    if (key.equals("outside-bases")) {
                        value = Draft.list(value.replace(',', ' ')).stream().map(this::block).collect(Collectors.joining(","));
                    }
                    session.draft.bunkersRule(key, value);
                    Msg.ok(player, "Bunkers " + key + " = " + value + ".");
                }))))));

        root.then(Commands.literal("rules").then(Commands.literal("set").then(word("key").suggests(fixed(Draft.RULE_KEYS))
                .then(text("value").executes(session((ctx, player, session) -> {
                    session.draft.rule(str(ctx, "key"), str(ctx, "value"));
                    Msg.ok(player, str(ctx, "key") + " = " + str(ctx, "value") + ".");
                }))))));

        root.then(Commands.literal("preview").executes(session((ctx, player, session) -> {
            session.preview = !session.preview;
            Msg.ok(player, session.preview ? "Outlines on." : "Outlines off.");
        })));
        root.then(Commands.literal("tp").then(word("target").suggests(fromDraft(draft -> {
            List<String> targets = new ArrayList<>(List.of("spectator"));
            targets.addAll(draft.teamIds());
            targets.addAll(draft.goalIds());
            targets.addAll(draft.regionIds());
            return targets;
        })).executes(session(this::teleport))));
        root.then(Commands.literal("validate").executes(session((ctx, player, session) -> {
            List<Problem> problems = validate(session);
            problems.forEach(problem -> Msg.problem(player, problem));
            summary(player, problems);
        })));
        root.then(Commands.literal("save").executes(session((ctx, player, session) -> save(player, session))));
        root.then(Commands.literal("export").executes(session((ctx, player, session) -> export(player, session))));
        return root.build();
    }

    // ---- sessions ------------------------------------------------------------

    private int world(CommandContext<CommandSourceStack> ctx, String type) {
        String slug = str(ctx, "slug");
        CommandSender sender = ctx.getSource().getSender();
        boolean flat = type.equals("flat");
        if (!flat && !type.equals("void")) {
            Msg.fail(sender, "A world is void or flat.");
            return 0;
        }
        try {
            Draft.requireSlug(slug);
        } catch (IllegalArgumentException e) {
            Msg.fail(sender, e.getMessage());
            return 0;
        }
        // createWorld refuses to run while worlds tick, so it goes on the next tick from anywhere.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            World world = plugin.getServer().getWorld(slug);
            if (world == null) {
                WorldCreator creator = new WorldCreator(slug).environment(World.Environment.NORMAL).generateStructures(false)
                        .keepSpawnLoaded(TriState.FALSE);
                world = (flat ? creator.type(WorldType.FLAT) : creator.generator(new VoidChunkGenerator())).createWorld();
                if (world == null) {
                    Msg.fail(sender, "The server could not create " + slug + ".");
                    return;
                }
                if (!flat) {
                    world.getBlockAt(0, 64, 0).setType(Material.STONE);
                }
                world.setSpawnLocation(0, world.getHighestBlockYAt(0, 0) + 1, 0);
                Msg.ok(sender, "Created " + type + " world " + slug + " (" + world.getWorldFolder() + ").");
            }
            if (sender instanceof Player player) {
                player.teleport(world.getSpawnLocation().add(0.5, 0, 0.5));
                Msg.ok(player, "You are in " + slug + ". Build, then /cmap new " + slug + " <gamemode>.");
            }
        });
        return 1;
    }

    private void newMap(CommandContext<CommandSourceStack> ctx, Player player) {
        String slug = str(ctx, "slug");
        Gamemode gamemode = Gamemode.byId(str(ctx, "gamemode"))
                .orElseThrow(() -> Draft.bad("Unknown gamemode. Use one of " + String.join(", ", Gamemode.ids()) + "."));
        if (plugin.session(player) != null) {
            throw Draft.bad("You already have a map open. /cmap save and /cmap close it first.");
        }
        if (Files.exists(plugin.mapFile(slug))) {
            throw Draft.bad(slug + " is already saved. Edit it with /cmap open " + slug + ".");
        }
        Author author = new Author(player.getUniqueId().toString(), player.getName(), "Build");
        Draft draft = Draft.fresh(slug, gamemode, author, Msg.pose(player.getLocation()));
        plugin.open(new Session(player.getUniqueId(), player.getWorld().getName(), slug, draft, null));
        Msg.ok(player, "Started " + gamemode.displayName() + " map " + slug + " in world " + player.getWorld().getName()
                + ". Spectators spawn where you stand. /cmap wand to start selecting, /cmap validate to see what is missing.");
    }

    private void open(CommandContext<CommandSourceStack> ctx, Player player) {
        String slug = str(ctx, "slug");
        if (plugin.session(player) != null) {
            throw Draft.bad("You already have a map open. /cmap save and /cmap close it first.");
        }
        Path file = plugin.mapFile(slug);
        if (!Files.exists(file)) {
            throw Draft.bad("There is no saved map called " + slug + ". Saved maps: " + String.join(", ", plugin.savedSlugs()) + ".");
        }
        MapManifestReader.Result result = new MapManifestReader().read(file);
        result.problems().forEach(problem -> Msg.problem(player, problem));
        if (result.manifest() == null) {
            throw Draft.bad("Could not read " + file + ".");
        }
        plugin.open(new Session(player.getUniqueId(), player.getWorld().getName(), slug, Draft.of(result.manifest()), result.manifest()));
        Msg.ok(player, "Opened " + slug + " in world " + player.getWorld().getName() + ".");
    }

    private void close(Player player, Session session, boolean confirmed) {
        if (session.dirty() && !confirmed) {
            throw Draft.bad("You have unsaved changes. /cmap save first, or /cmap close confirm to drop them.");
        }
        plugin.close(player.getUniqueId());
        Msg.ok(player, "Closed " + session.slug + ".");
    }

    private void info(Player player, Session session) {
        Draft draft = session.draft;
        Msg.send(player, "<gold><name></gold> <gray>(<slug> <version>, " + (draft.gamemode == null ? "no gamemode" : draft.gamemode.displayName())
                + ", world " + session.world + ")</gray>", Placeholder.unparsed("name", String.valueOf(draft.name)),
                Placeholder.unparsed("slug", String.valueOf(draft.slug)), Placeholder.unparsed("version", String.valueOf(draft.version)));
        Msg.ok(player, "Teams: " + (draft.teams.isEmpty() ? "none" : draft.teams.stream()
                .map(team -> team.id() + " (" + draft.spawnsFor(team.id()).size() + " spawns" + (team.base() == null ? "" : ", base " + team.base()) + ")")
                .collect(Collectors.joining(", "))));
        Msg.ok(player, "Spectator spawn: " + (draft.spawn == null ? "none" : Msg.at(draft.spawn)) + ". Regions: "
                + (draft.regions.isEmpty() ? "none" : String.join(", ", draft.regionIds())));
        Msg.ok(player, "Goals: " + (draft.goals.isEmpty() ? "none" : draft.goals.stream().map(goal -> goal.id() + " (" + goal.type() + ")")
                .collect(Collectors.joining(", "))) + ". Kits: " + (draft.kits.isEmpty() ? "none" : String.join(", ", draft.kits.keySet())));
        if (draft.gamemode == Gamemode.BUNKERS) {
            Msg.ok(player, "Bunkers: " + draft.npcs.size() + " shopkeepers, " + draft.mines.size() + " mines, " + draft.chests.size() + " subclaim chests.");
        }
        summary(player, new MapValidator().validate(draft.build()));
    }

    // ---- editing helpers -----------------------------------------------------

    private void putGoal(Player player, Session session, Goal goal) {
        session.draft.putGoal(goal);
        Msg.ok(player, "Goal " + goal.id() + " (" + goal.type() + ") set.");
    }

    private void teleport(CommandContext<CommandSourceStack> ctx, Player player, Session session) {
        String target = str(ctx, "target");
        Draft draft = session.draft;
        World world = player.getWorld();
        Location to;
        if (target.equals("spectator")) {
            if (draft.spawn == null) {
                throw Draft.bad("There is no spectator spawn yet.");
            }
            to = location(world, draft.spawn);
        } else if (draft.team(target).isPresent()) {
            List<Pose> points = draft.spawnsFor(target);
            if (points.isEmpty()) {
                throw Draft.bad(target + " has no spawns yet.");
            }
            to = location(world, points.getFirst());
        } else if (draft.goal(target).isPresent()) {
            to = above(world, draft.goalBox(draft.goal(target).get()).orElseThrow(() -> Draft.bad(target + " has no place yet.")));
        } else if (draft.region(target).isPresent()) {
            to = above(world, draft.region(target).get().cuboid());
        } else {
            throw Draft.bad("Nothing is called " + target + ". Use spectator, a team, a goal or a region.");
        }
        player.teleport(to);
        Msg.ok(player, "Teleported to " + target + ".");
    }

    private static Location location(World world, Pose pose) {
        return new Location(world, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
    }

    private static Location above(World world, Cuboid cuboid) {
        BlockPos min = cuboid.min();
        BlockPos max = cuboid.max();
        return new Location(world, (min.x() + max.x() + 1) / 2.0, max.y() + 1, (min.z() + max.z() + 1) / 2.0);
    }

    private static Block lookingAt(Player player) {
        Block block = player.getTargetBlockExact(LOOK_RANGE);
        if (block == null || block.getType().isAir()) {
            throw Draft.bad("Look at a block within " + LOOK_RANGE + " blocks.");
        }
        return block;
    }

    /** A block's id, checked against this server so a typo fails here and not on Clash. */
    private String block(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock()) {
            throw Draft.bad(name + " is not a block.");
        }
        return material.getKey().getKey();
    }

    private static String size(Cuboid cuboid) {
        BlockPos min = cuboid.min();
        BlockPos max = cuboid.max();
        return (max.x() - min.x() + 1) + "x" + (max.y() - min.y() + 1) + "x" + (max.z() - min.z() + 1) + " blocks from "
                + min.x() + ", " + min.y() + ", " + min.z() + " to " + max.x() + ", " + max.y() + ", " + max.z();
    }

    // ---- validate, save, export ----------------------------------------------

    private List<Problem> validate(Session session) {
        MapManifest manifest = session.draft.build();
        List<Problem> problems = new ArrayList<>(new MapValidator().validate(manifest));
        World world = plugin.getServer().getWorld(session.world);
        if (world == null) {
            problems.add(Problem.error("world", "the world " + session.world + " is not loaded."));
        } else {
            problems.addAll(WorldChecks.check(manifest, world));
        }
        return problems;
    }

    private static void summary(Player player, List<Problem> problems) {
        long errors = problems.stream().filter(Problem::isError).count();
        if (problems.isEmpty()) {
            Msg.ok(player, "No problems.");
        } else {
            Msg.send(player, (errors > 0 ? "<red>" : "<yellow>") + errors + " errors, " + (problems.size() - errors) + " warnings.");
        }
    }

    private MapManifest save(Player player, Session session) throws IOException {
        MapManifest manifest = session.draft.build();
        Path file = plugin.mapFile(session.slug);
        Files.createDirectories(file.getParent());
        new MapManifestWriter().write(manifest, file);
        session.saved = manifest;
        Msg.ok(player, "Saved " + file + ".");
        return manifest;
    }

    private void export(Player player, Session session) throws IOException {
        List<Problem> problems = validate(session);
        if (problems.stream().anyMatch(Problem::isError)) {
            problems.forEach(problem -> Msg.problem(player, problem));
            throw Draft.bad("Fix the errors above, then export again.");
        }
        MapManifest manifest = save(player, session);
        World world = plugin.getServer().getWorld(session.world);
        String yaml = new MapManifestWriter().write(manifest);
        Path zip = plugin.exportsFolder().resolve(session.slug + "-" + session.draft.version + ".zip");
        Path folder = world.getWorldFolder().toPath();
        Msg.ok(player, "Saving the world and packing " + zip.getFileName() + "...");
        world.save(true);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String failure = null;
            try {
                Export.zip(session.slug, yaml, folder, zip);
            } catch (IOException e) {
                failure = e.getMessage();
            }
            String outcome = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (outcome == null) {
                    Msg.ok(player, "Exported " + zip + ". Send it to the Clash staff.");
                } else {
                    Msg.fail(player, "Export failed: " + outcome);
                }
            });
        });
    }

    private int help(CommandSender sender) {
        Msg.send(sender, "<gold>/cmap</gold> <gray>world, new, open, close, info, name, version, objective, team, spawn, region, goal, kit, "
                + "classes, bunkers, rules, preview, tp, validate, save, export. Tab completes every step; "
                + "<white>docs/MAPMAKER.md</white> walks through a map.</gray>");
        return 1;
    }

    // ---- brigadier plumbing --------------------------------------------------

    @FunctionalInterface
    private interface PlayerOp {
        void run(CommandContext<CommandSourceStack> ctx, Player player) throws IOException;
    }

    @FunctionalInterface
    private interface SessionOp {
        void run(CommandContext<CommandSourceStack> ctx, Player player, Session session) throws IOException;
    }

    private Command<CommandSourceStack> player(PlayerOp op) {
        return ctx -> {
            if (!(ctx.getSource().getSender() instanceof Player player)) {
                Msg.fail(ctx.getSource().getSender(), "Only a player can do that.");
                return 0;
            }
            try {
                op.run(ctx, player);
                return 1;
            } catch (IllegalArgumentException | IOException e) {
                Msg.fail(player, e.getMessage());
                return 0;
            }
        };
    }

    private Command<CommandSourceStack> session(SessionOp op) {
        return player((ctx, player) -> {
            Session session = plugin.session(player);
            if (session == null) {
                throw Draft.bad("No map is open. Start one with /cmap new <slug> <gamemode> or /cmap open <slug>.");
            }
            op.run(ctx, player, session);
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> word(String name) {
        return Commands.argument(name, StringArgumentType.word());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> text(String name) {
        return Commands.argument(name, StringArgumentType.greedyString());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> integer(String name, int min) {
        return Commands.argument(name, IntegerArgumentType.integer(min));
    }

    private static String str(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    /** An argument the player may have left out. */
    private static String optional(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Nests the arguments one inside the next and runs {@code op} from the {@code required}th on, so the
     * rest are optional.
     */
    @SafeVarargs
    private static ArgumentBuilder<CommandSourceStack, ?> chain(Command<CommandSourceStack> op, int required,
                                                               RequiredArgumentBuilder<CommandSourceStack, ?>... args) {
        ArgumentBuilder<CommandSourceStack, ?> tail = null;
        for (int i = args.length - 1; i >= 0; i--) {
            RequiredArgumentBuilder<CommandSourceStack, ?> arg = args[i];
            if (i >= required - 1) {
                arg.executes(op);
            }
            if (tail != null) {
                arg.then(tail);
            }
            tail = arg;
        }
        return tail;
    }

    private static SuggestionProvider<CommandSourceStack> fixed(Collection<String> values) {
        return fixed(() -> values);
    }

    private static SuggestionProvider<CommandSourceStack> fixed(Supplier<Collection<String>> values) {
        return (ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (String value : values.get()) {
                if (value.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(value);
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> fromDraft(Function<Draft, Collection<String>> values) {
        return (ctx, builder) -> {
            Session session = ctx.getSource().getSender() instanceof Player player ? plugin.session(player) : null;
            if (session != null) {
                String typed = builder.getRemainingLowerCase();
                for (String value : values.apply(session.draft)) {
                    if (value.startsWith(typed)) {
                        builder.suggest(value);
                    }
                }
            }
            return builder.buildFuture();
        };
    }

    private static List<String> with(List<String> values, String extra) {
        List<String> all = new ArrayList<>(values);
        all.add(extra);
        return all;
    }
}
