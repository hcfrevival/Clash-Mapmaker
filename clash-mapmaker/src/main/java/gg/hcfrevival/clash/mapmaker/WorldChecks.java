package gg.hcfrevival.clash.mapmaker;

import gg.hcfrevival.clash.map.MapManifest;
import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import gg.hcfrevival.clash.map.MapManifest.Region;
import gg.hcfrevival.clash.map.Problem;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The checks the {@code clash-map} library cannot run because they need the world: regions within the world's
 * height, spawns inside the bounds region, lava inside every core casing, a container in every wool room, a listed
 * block in every destroyable region, a container at every subclaim chest.
 *
 * <p>The same checks Clash runs when it loads a map; {@code /cmap validate} and {@code /cmap export} run them here.
 */
final class WorldChecks {
    /** Bigger cuboids are not scanned block by block; the map maker gets a warning instead. */
    public static final long MAX_SCAN_BLOCKS = 1_000_000L;

    private WorldChecks() {
    }

    public static List<Problem> check(MapManifest manifest, World world) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(world, "world");
        List<Problem> problems = new ArrayList<>(geometry(manifest, world.getMinHeight(), world.getMaxHeight()));
        List<MapManifest.Goal> goals = manifest.goals();
        for (int i = 0; i < goals.size(); i++) {
            String path = "goals[" + i + "]";
            if (goals.get(i) instanceof MapManifest.Core core && core.region() != null) {
                scan(manifest, world, core.region(), block -> block.getType() == Material.LAVA, path + ".region",
                        "the core casing " + core.region() + " contains no lava.", problems);
            } else if (goals.get(i) instanceof MapManifest.Destroyable destroyable && destroyable.region() != null) {
                Set<Material> materials = new HashSet<>();
                destroyable.materials().forEach(name -> Optional.ofNullable(Material.matchMaterial(name)).ifPresent(materials::add));
                scan(manifest, world, destroyable.region(), block -> materials.contains(block.getType()), path + ".region",
                        "the destroyable region " + destroyable.region() + " contains none of " + destroyable.materials() + ".", problems);
            } else if (goals.get(i) instanceof MapManifest.Wool wool) {
                if (wool.room() != null) {
                    scan(manifest, world, wool.room(), block -> block.getState() instanceof Container, path + ".room",
                            "the wool room " + wool.room() + " contains no chest or other container.", problems);
                }
                BlockPos monument = wool.monument();
                if (monument != null && !world.getBlockAt(monument.x(), monument.y(), monument.z()).getType().isAir()) {
                    problems.add(Problem.warning(path + ".monument", "the block at " + monument.x() + ", " + monument.y() + ", " + monument.z()
                            + " is not air, so the wool cannot be placed there."));
                }
            }
        }
        MapManifest.Bunkers.Subclaims subclaims = manifest.bunkers() == null ? null : manifest.bunkers().subclaims();
        if (subclaims != null) {
            for (int i = 0; i < subclaims.chests().size(); i++) {
                BlockPos pos = subclaims.chests().get(i).pos();
                if (!(world.getBlockAt(pos.x(), pos.y(), pos.z()).getState(false) instanceof Container)) {
                    problems.add(Problem.error("bunkers.subclaims.chests[" + i + "]",
                            "has no chest or other container at " + pos.x() + ", " + pos.y() + ", " + pos.z() + "."));
                }
            }
        }
        return problems;
    }

    /**
     * The checks that need only the world's height: every region within it, every spawn inside the bounds region.
     *
     * @param minY the lowest block the world has
     * @param maxY one above the highest block the world has
     */
    public static List<Problem> geometry(MapManifest manifest, int minY, int maxY) {
        Objects.requireNonNull(manifest, "manifest");
        List<Problem> problems = new ArrayList<>();
        // shortcut: "inside the world" checks height only, add the world border once a map sets one
        List<Region> regions = manifest.regions();
        for (int i = 0; i < regions.size(); i++) {
            MapManifest.Cuboid cuboid = regions.get(i).cuboid();
            if (cuboid == null) {
                continue;
            }
            BlockPos min = cuboid.min();
            BlockPos max = cuboid.max();
            if (min.y() < minY || max.y() >= maxY) {
                problems.add(Problem.error("regions[" + i + "].cuboid", "spans y=" + min.y() + " to " + max.y()
                        + " but the world only has y=" + minY + " to " + (maxY - 1) + "."));
            }
        }
        String boundsId = manifest.world().bounds();
        MapManifest.Cuboid bounds = boundsId == null
                ? null
                : manifest.region(boundsId).map(Region::cuboid).orElse(null);
        if (bounds == null) {
            // The library already reports a missing or unknown bounds region.
            return problems;
        }
        MapManifest.Pose spawn = manifest.world().spawn();
        if (spawn != null && !bounds.contains(spawn.x(), spawn.y(), spawn.z())) {
            problems.add(Problem.error("world.spawn", "is outside the bounds region " + boundsId + "."));
        }
        List<MapManifest.SpawnGroup> groups = manifest.spawns();
        for (int g = 0; g < groups.size(); g++) {
            List<MapManifest.Pose> points = groups.get(g).points();
            for (int p = 0; p < points.size(); p++) {
                MapManifest.Pose point = points.get(p);
                if (!bounds.contains(point.x(), point.y(), point.z())) {
                    problems.add(Problem.error("spawns[" + g + "].points[" + p + "]",
                            "is outside the bounds region " + boundsId + "."));
                }
            }
        }
        return problems;
    }

    private static void scan(
            MapManifest manifest,
            World world,
            String regionId,
            Predicate<Block> wanted,
            String path,
            String missing,
            List<Problem> problems
    ) {
        MapManifest.Cuboid cuboid = manifest.region(regionId).map(Region::cuboid).orElse(null);
        if (cuboid == null) {
            return;
        }
        Optional<Boolean> found = anyBlock(world, cuboid, wanted);
        if (found.isEmpty()) {
            problems.add(Problem.warning(path, regionId + " is too large to scan block by block (over "
                    + MAX_SCAN_BLOCKS + " blocks); not checked."));
        } else if (!found.get()) {
            problems.add(Problem.error(path, missing));
        }
    }

    /** Empty when the cuboid is too large to scan. */
    private static Optional<Boolean> anyBlock(World world, MapManifest.Cuboid cuboid, Predicate<Block> wanted) {
        BlockPos min = cuboid.min();
        BlockPos max = cuboid.max();
        long volume = (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
        if (volume > MAX_SCAN_BLOCKS) {
            return Optional.empty();
        }
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    if (wanted.test(world.getBlockAt(x, y, z))) {
                        return Optional.of(true);
                    }
                }
            }
        }
        return Optional.of(false);
    }
}
