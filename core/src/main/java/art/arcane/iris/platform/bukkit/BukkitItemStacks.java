/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.core.modules.IrisItemRegistry;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;
import com.volmit.item.ItemEnchantment;
import com.volmit.resource.ResourceId;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.material.Colorable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Creates the Bukkit item stacks that module {@link ItemDrop}s describe. */
public final class BukkitItemStacks {
    private static final int LORE_WRAP_WIDTH = 24;
    private static final String LORE_RULE = C.GRAY + "--------------------";
    private static final int CHANCE_DIGITS = 5;

    private BukkitItemStacks() {
    }

    /** The stack {@code drop} describes; an air stack when the item cannot be made. */
    public static ItemStack create(ItemDrop drop) {
        try {
            ItemStack stack = baseStack(drop);
            if (stack == null || stack.getType() == Material.AIR) {
                return new ItemStack(Material.AIR);
            }
            stack.setItemMeta(applyProperties(stack, drop));
            return BukkitPlatform.applyCustomNbt(stack, customNbtOf(drop));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return new ItemStack(Material.AIR);
        }
    }

    /** The non-air stacks of {@code drops}, in order. */
    public static KList<ItemStack> createAll(List<ItemDrop> drops) {
        KList<ItemStack> stacks = new KList<>();
        for (ItemDrop drop : drops) {
            ItemStack stack = create(drop);
            if (stack.getType() != Material.AIR) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static ItemStack baseStack(ItemDrop drop) {
        if (IrisItemRegistry.isCustomItem(drop.item())) {
            Optional<ItemStack> custom = IrisServices.get(ExternalDataSVC.class)
                    .getItemStack(IrisItemRegistry.identifierOf(drop.item()), customNbtOf(drop));
            if (custom.isEmpty()) {
                IrisLogging.warn("Unknown Material: " + drop.item());
                return null;
            }
            ItemStack stack = custom.get();
            stack.setAmount(drop.amount());
            return stack;
        }
        Material material = Material.matchMaterial(drop.item().toString());
        if (material == null) {
            IrisLogging.warnOnce("loot:material:" + drop.item(), "Unknown Material: %s", drop.item());
            return null;
        }
        return new ItemStack(material, drop.amount());
    }

    private static KMap<String, Object> customNbtOf(ItemDrop drop) {
        Map<String, Object> customNbt = drop.extras().customNbt();
        return customNbt.isEmpty() ? null : new KMap<>(customNbt);
    }

    private static ItemMeta applyProperties(ItemStack stack, ItemDrop drop) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }

        drop.enchantments().forEach(enchantment -> enchant(meta, enchantment));
        for (ItemAttribute attribute : drop.attributes()) {
            BukkitAttributes.apply(attribute, meta);
        }

        ItemDrop.Look look = drop.look();
        ItemDrop.Wear wear = drop.wear();
        meta.setUnbreakable(wear.unbreakable());
        flags(drop).forEach(meta::addItemFlags);

        if (look.customModel().isPresent()) {
            CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
            customModelData.setFloats(List.of((float) look.customModel().getAsInt()));
            meta.setCustomModelDataComponent(customModelData);
        }

        int maxDurability = stack.getType().getMaxDurability();
        if (wear.damage().isPresent() && maxDurability > 0 && meta instanceof Damageable damageable) {
            damageable.setDamage((int) Math.round(Math.max(0, Math.min(maxDurability, wear.damage().getAsDouble() * maxDurability))));
        }

        if (look.leatherColor().isPresent() && meta instanceof LeatherArmorMeta leather) {
            leather.setColor(org.bukkit.Color.fromRGB(look.leatherColor().getAsInt()));
        }

        if (look.dyeColor().isPresent() && meta instanceof Colorable colorable) {
            colorable.setColor(DyeColor.valueOf(look.dyeColor().get().name()));
        }

        look.displayName().ifPresent(name -> meta.setDisplayName(C.translateAlternateColorCodes('&', name)));

        meta.setLore(lore(drop));
        return meta;
    }

    private static void enchant(ItemMeta meta, ItemEnchantment enchantment) {
        try {
            Enchantment resolved = resolveEnchantment(enchantment.key());
            if (resolved == null) {
                IrisLogging.warn("Unknown Enchantment: " + enchantment.key());
                return;
            }
            if (meta instanceof EnchantmentStorageMeta storage) {
                storage.addStoredEnchant(resolved, enchantment.level(), true);
                return;
            }
            meta.addEnchant(resolved, enchantment.level(), true);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    private static Enchantment resolveEnchantment(ResourceId key) {
        return Registry.ENCHANTMENT.get(new NamespacedKey(key.namespace(), key.path()));
    }

    private static List<ItemFlag> flags(ItemDrop drop) {
        KList<ItemFlag> resolved = new KList<>();
        for (String flag : drop.extras().flags()) {
            if (flag == null) {
                continue;
            }
            try {
                resolved.add(ItemFlag.valueOf(flag));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return resolved;
    }

    private static KList<String> lore(ItemDrop drop) {
        KList<String> lore = new KList<>();
        for (String line : drop.look().lore()) {
            String colored = C.translateAlternateColorCodes('&', line);
            if (colored.length() > LORE_WRAP_WIDTH) {
                for (String wrapped : Form.wrapWords(colored, LORE_WRAP_WIDTH).split("\\Q\n\\E")) {
                    lore.add(wrapped.trim());
                }
            } else {
                lore.add(colored);
            }
        }
        drop.source().ifPresent(source -> addSourceLore(lore, source));
        return lore;
    }

    private static void addSourceLore(KList<String> lore, ItemDrop.Source source) {
        if (lore.isNotEmpty()) {
            lore.add(LORE_RULE);
        }
        if (source.table().isEmpty()) {
            lore.add(C.GRAY + "1 in " + source.entryRarity() + " Chance (" + Form.pc(1D / source.entryRarity(), CHANCE_DIGITS) + ")");
            return;
        }
        long combined = (long) source.tableRarity() * source.entryRarity();
        lore.add(C.GRAY + "From: " + source.table().get().path() + " (" + Form.pc(1D / source.tableRarity(), CHANCE_DIGITS) + ")");
        lore.add(C.GRAY + "1 in " + combined + " Chance (" + Form.pc(1D / combined, CHANCE_DIGITS) + ")");
    }
}
