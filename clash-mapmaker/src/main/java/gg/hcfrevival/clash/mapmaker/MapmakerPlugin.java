package gg.hcfrevival.clash.mapmaker;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Build Clash maps in game. One {@link Session} per player, kept until {@code /cmap close} (a relog keeps it);
 * maps are saved under {@code plugins/ClashMapmaker/maps/<slug>/map.yml} and exported to
 * {@code plugins/ClashMapmaker/exports/}.
 */
public final class MapmakerPlugin extends JavaPlugin {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private Wand wand;

    @Override
    public void onEnable() {
        wand = new Wand(this);
        getServer().getPluginManager().registerEvents(wand, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(new CmapCommand(this).build(), "Build a Clash map"));
        getServer().getScheduler().runTaskTimer(this, new Outlines(this), 20L, 10L);
    }

    Wand wand() {
        return wand;
    }

    Session session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    void open(Session session) {
        sessions.put(session.player, session);
    }

    void close(UUID player) {
        sessions.remove(player);
    }

    Collection<Session> sessions() {
        return sessions.values();
    }

    Path mapFile(String slug) {
        return getDataFolder().toPath().resolve("maps").resolve(slug).resolve("map.yml");
    }

    Path exportsFolder() {
        return getDataFolder().toPath().resolve("exports");
    }

    /** The slugs with a saved map.yml. */
    Collection<String> savedSlugs() {
        Path maps = getDataFolder().toPath().resolve("maps");
        if (!Files.isDirectory(maps)) {
            return List.of();
        }
        try (Stream<Path> folders = Files.list(maps)) {
            return folders.filter(folder -> Files.isRegularFile(folder.resolve("map.yml"))).map(folder -> folder.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
