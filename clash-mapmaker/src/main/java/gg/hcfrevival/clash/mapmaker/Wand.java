package gg.hcfrevival.clash.mapmaker;

import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The selection stick: left-click sets corner A, right-click corner B, on the session's world. */
final class Wand implements Listener {
    private final MapmakerPlugin plugin;
    private final NamespacedKey key;

    Wand(MapmakerPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "wand");
    }

    ItemStack item() {
        ItemStack stick = ItemStack.of(Material.STICK);
        stick.editMeta(meta -> {
            meta.displayName(Msg.MINI.deserialize("<!italic><gold>Clash Wand"));
            meta.lore(List.of(Msg.MINI.deserialize("<!italic><gray>Left-click: corner A"), Msg.MINI.deserialize("<!italic><gray>Right-click: corner B")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        return stick;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Block block = event.getClickedBlock();
        if (event.getHand() != EquipmentSlot.HAND || block == null || item == null || !item.hasItemMeta()
                || !item.getItemMeta().getPersistentDataContainer().has(key)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Session session = plugin.session(player);
        if (session == null) {
            Msg.fail(player, "No map is open. /cmap new <slug> <gamemode> or /cmap open <slug> first.");
            return;
        }
        if (!player.getWorld().getName().equals(session.world)) {
            Msg.fail(player, "Your map " + session.slug + " is in world " + session.world + ".");
            return;
        }
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            session.a = pos;
            Msg.ok(player, "Corner A: " + pos.x() + ", " + pos.y() + ", " + pos.z() + ".");
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            session.b = pos;
            Msg.ok(player, "Corner B: " + pos.x() + ", " + pos.y() + ", " + pos.z() + ".");
        }
    }
}
