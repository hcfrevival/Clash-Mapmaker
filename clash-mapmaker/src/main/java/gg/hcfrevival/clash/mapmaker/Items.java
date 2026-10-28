package gg.hcfrevival.clash.mapmaker;

import gg.hcfrevival.clash.map.MapManifest.Item;
import gg.hcfrevival.clash.map.MapManifest.Kit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory items as manifest items. Material and amount stay readable; anything with meta (a name, enchantments,
 * damage, a potion) also carries {@code data}, the exact item as base64 of {@link ItemStack#serializeAsBytes()},
 * which Clash prefers when present.
 */
final class Items {
    private Items() {
    }

    static Item of(Integer slot, ItemStack stack) {
        String data = stack.hasItemMeta() ? Base64.getEncoder().encodeToString(stack.serializeAsBytes()) : null;
        Integer amount = stack.getAmount() == 1 ? null : stack.getAmount();
        return new Item(slot, stack.getType().getKey().getKey(), amount, null, null, null, null, null, null, data);
    }

    /** Armor, the 36 storage slots and the off hand; empty slots are left out. */
    static Kit of(PlayerInventory inventory) {
        Map<String, Item> armor = new LinkedHashMap<>();
        put(armor, "helmet", inventory.getHelmet());
        put(armor, "chestplate", inventory.getChestplate());
        put(armor, "leggings", inventory.getLeggings());
        put(armor, "boots", inventory.getBoots());
        List<Item> items = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                items.add(of(slot, stack));
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        return new Kit(armor, items, offhand.isEmpty() ? null : of(null, offhand));
    }

    private static void put(Map<String, Item> armor, String slot, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            armor.put(slot, of(null, stack));
        }
    }
}
