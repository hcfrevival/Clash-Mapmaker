package gg.hcfrevival.clash.mapmaker;

import gg.hcfrevival.clash.map.MapManifest;
import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import gg.hcfrevival.clash.map.MapManifest.Cuboid;

import java.util.UUID;

/** One map maker editing one map in one world. */
final class Session {
    final UUID player;
    final String world;
    final String slug;
    final Draft draft;
    /** What the file holds, to tell unsaved work apart; null until the first save. */
    MapManifest saved;
    BlockPos a;
    BlockPos b;
    boolean preview = true;

    Session(UUID player, String world, String slug, Draft draft, MapManifest saved) {
        this.player = player;
        this.world = world;
        this.slug = slug;
        this.draft = draft;
        this.saved = saved;
    }

    Cuboid selection() {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Select two corners with the wand first (/cmap wand).");
        }
        return new Cuboid(a, b);
    }

    boolean dirty() {
        return saved == null || !saved.equals(draft.build());
    }
}
