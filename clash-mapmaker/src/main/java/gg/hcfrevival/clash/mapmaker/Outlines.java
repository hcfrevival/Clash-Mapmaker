package gg.hcfrevival.clash.mapmaker;

import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.Mine;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.ShopNpc;
import gg.hcfrevival.clash.map.MapManifest.Bunkers.SubclaimChest;
import gg.hcfrevival.clash.map.MapManifest.Cuboid;
import gg.hcfrevival.clash.map.MapManifest.Goal;
import gg.hcfrevival.clash.map.MapManifest.Pose;
import gg.hcfrevival.clash.map.MapManifest.Region;
import gg.hcfrevival.clash.map.MapManifest.SpawnGroup;
import gg.hcfrevival.clash.map.MapManifest.Team;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the map around each maker with preview on: regions white, bases in their team's color, goals orange,
 * mines green, subclaim chests lime, spawns as a column in the team's color (spectator pink), shopkeepers aqua,
 * the wand selection yellow. Points sit one block apart on the edges, only near the player, at most
 * {@link #CAP} per pass.
 */
final class Outlines implements Runnable {
    private static final double RANGE = 48;
    private static final int CAP = 2000;
    private final MapmakerPlugin plugin;

    Outlines(MapmakerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Session session : plugin.sessions()) {
            Player player = plugin.getServer().getPlayer(session.player);
            if (player != null && session.preview && player.getWorld().getName().equals(session.world)) {
                draw(player, session);
            }
        }
    }

    private static void draw(Player player, Session session) {
        Draft draft = session.draft;
        Painter paint = new Painter(player);
        Map<String, Color> teamColors = new HashMap<>();
        Map<String, Color> baseColors = new HashMap<>();
        for (Team team : draft.teams) {
            Color color = Msg.rgb(team.color());
            teamColors.put(team.id(), color);
            if (team.base() != null) {
                baseColors.put(team.base(), color);
            }
        }
        for (Region region : draft.regions) {
            if (region.cuboid() != null) {
                paint.box(region.cuboid(), baseColors.getOrDefault(region.id(), Color.WHITE));
            }
        }
        for (Goal goal : draft.goals) {
            draft.goalBox(goal).ifPresent(box -> paint.box(box, Color.ORANGE));
        }
        for (Mine mine : draft.mines) {
            draft.region(mine.region()).map(Region::cuboid).ifPresent(box -> paint.box(box, Color.GREEN));
        }
        for (SubclaimChest chest : draft.chests) {
            paint.box(new Cuboid(chest.pos(), chest.pos()), Color.LIME);
        }
        for (SpawnGroup group : draft.spawns) {
            for (Pose pose : group.points()) {
                paint.column(pose, teamColors.getOrDefault(group.team(), Color.WHITE));
            }
        }
        if (draft.spawn != null) {
            paint.column(draft.spawn, Color.FUCHSIA);
        }
        for (ShopNpc npc : draft.npcs) {
            paint.column(npc.at(), Color.AQUA);
        }
        BlockPos a = session.a == null ? session.b : session.a;
        BlockPos b = session.b == null ? session.a : session.b;
        if (a != null) {
            paint.box(new Cuboid(a, b), Color.YELLOW);
        }
    }

    private static final class Painter {
        private final Player player;
        private final Location eye;
        private int budget = CAP;

        Painter(Player player) {
            this.player = player;
            this.eye = player.getLocation();
        }

        /** The twelve edges of the blocks the cuboid covers. */
        void box(Cuboid cuboid, Color color) {
            BlockPos min = cuboid.min();
            BlockPos max = cuboid.max();
            double[] xs = {min.x(), max.x() + 1};
            double[] ys = {min.y(), max.y() + 1};
            double[] zs = {min.z(), max.z() + 1};
            for (double x = xs[0]; x <= xs[1]; x++) {
                for (double y : ys) {
                    for (double z : zs) {
                        dot(x, y, z, color);
                    }
                }
            }
            for (double y = ys[0]; y <= ys[1]; y++) {
                for (double x : xs) {
                    for (double z : zs) {
                        dot(x, y, z, color);
                    }
                }
            }
            for (double z = zs[0]; z <= zs[1]; z++) {
                for (double x : xs) {
                    for (double y : ys) {
                        dot(x, y, z, color);
                    }
                }
            }
        }

        void column(Pose pose, Color color) {
            for (double dy = 0; dy <= 2; dy += 0.5) {
                dot(pose.x(), pose.y() + dy, pose.z(), color);
            }
        }

        private void dot(double x, double y, double z, Color color) {
            double dx = x - eye.getX();
            double dy = y - eye.getY();
            double dz = z - eye.getZ();
            if (budget <= 0 || dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
                return;
            }
            budget--;
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.0f), true);
        }
    }
}
