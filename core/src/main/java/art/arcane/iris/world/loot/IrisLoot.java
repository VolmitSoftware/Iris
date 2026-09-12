/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.world.loot;

import art.arcane.iris.world.entity.IrisAttributeModifier;
import art.arcane.iris.world.entity.IrisEnchantment;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListItemType;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.material.Colorable;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

@Snippet("loot")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents a loot entry")
@Data
@EqualsAndHashCode(doNotUseGetters = true)
@ToString(doNotUseGetters = true)
public class IrisLoot {
    public static final int MAX_AMOUNT = 64;

    private final transient AtomicCache<DyeColor> dyeColorResolved = new AtomicCache<>();
    @Description("The target inventory slot types to fill this loot with. World generation only ever fills STORAGE; the other values are inert during worldgen.")
    private InventorySlotType slotTypes = InventorySlotType.STORAGE;
    @MinNumber(1)
    @Description("The sub rarity of this loot. Calculated after this loot table has been picked.")
    private int rarity = 1;
    @MinNumber(1)
    @MaxNumber(MAX_AMOUNT)
    @Description("Minimum amount of this loot, from 1 to 64")
    private int minAmount = 1;
    @MinNumber(1)
    @MaxNumber(MAX_AMOUNT)
    @Description("Maximum amount of this loot, from 1 to 64")
    private int maxAmount = 1;
    @MinNumber(1)
    @Description("The display name of this item")
    private String displayName = null;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("Minimum durability percent")
    private double minDurability = 0;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("Maximum durability percent")
    private double maxDurability = 1;
    @Description("Define a custom model identifier 1.14+ only")
    private Integer customModel = null;
    @Description("Set this to true to prevent it from being broken")
    private boolean unbreakable = false;
    @ArrayType(min = 1, type = String.class)
    @Description("The item flags to add")
    private KList<String> itemFlags = new KList<>();
    @Description("Apply enchantments to this item")
    @ArrayType(min = 1, type = IrisEnchantment.class)
    private KList<IrisEnchantment> enchantments = new KList<>();
    @Description("Apply attribute modifiers to this item")
    @ArrayType(min = 1, type = IrisAttributeModifier.class)
    private KList<IrisAttributeModifier> attributes = new KList<>();
    @ArrayType(min = 1, type = String.class)
    @Description("Add lore to this item")
    private KList<String> lore = new KList<>();
    @RegistryListItemType
    @Required
    @Description("This is the item or block type. Does not accept minecraft:*, only materials such as DIAMOND_SWORD or DIRT. The exception are modded materials, as they require a namespace.")
    private String type = "";
    @Description("The dye color")
    private String dyeColor = null;
    @Description("The leather armor color")
    private String leatherColor = null;
    @Description("Defines a custom NBT Tag for the item.")
    private KMap<String, Object> customNbt;

    public Material getType() {
        return BukkitBlockResolution.getMaterial(type);
    }

    /**
     * The authored item key, exactly as written in the pack. Platform-neutral: {@link #getType()} resolves it against
     * Bukkit, mod loaders resolve it against their own item registry.
     */
    public String getTypeKey() {
        return type;
    }

    /**
     * The authored dye colour name, or null when unset. Platform-neutral counterpart to {@link #getDyeColor()}.
     */
    public String getDyeColorKey() {
        return dyeColor;
    }

    public DyeColor getDyeColor() {
        if (dyeColor == null) {
            return null;
        }
        return dyeColorResolved.aquire(() -> {
            try {
                return DyeColor.valueOf(dyeColor);
            } catch (IllegalArgumentException e) {
                return null;
            }
        });
    }

    private KList<ItemFlag> getResolvedItemFlags() {
        KList<ItemFlag> resolved = new KList<>();
        for (String flag : itemFlags) {
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

    public ItemStack get(boolean debug, RNG rng) {
        try {
            ItemStack is = getItemStack(rng);
            if (is == null)
                return new ItemStack(Material.AIR);
            is.setItemMeta(applyProperties(is, rng, debug, null));
            return BukkitPlatform.applyCustomNbt(is, customNbt);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return new ItemStack(Material.AIR);
        }
    }

    public ItemStack get(boolean debug, IrisLootTable table, RNG rng, long lootSeed, int entryIndex, int x, int y, int z) {
        long combinedRarity = LootResolver.combinedRarity(rarity, table.getRarity());
        if (!LootResolver.spatialOneIn(lootSeed, table, entryIndex, x, y, z, combinedRarity)) {
            return null;
        }

        try {
            ItemStack is = getItemStack(rng);
            if (is == null)
                return null;
            is.setItemMeta(applyProperties(is, rng, debug, table));
            return BukkitPlatform.applyCustomNbt(is, customNbt);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        return null;
    }

    // TODO Better Third Party Item Acquisition
    private ItemStack getItemStack(RNG rng) {
        if (!type.startsWith("minecraft:") && type.contains(":")) {
            Optional<ItemStack> opt = IrisServices.get(ExternalDataSVC.class).getItemStack(Identifier.fromString(type), customNbt);
            if (opt.isEmpty()) {
                IrisLogging.warn("Unknown Material: " + type);
                return new ItemStack(Material.AIR);
            }
            ItemStack is = opt.get();
            is.setAmount(Math.max(1, LootResolver.inclusive(rng, getMinAmount(), getMaxAmount())));
            return is;
        }
        return new ItemStack(getType(), Math.max(1, LootResolver.inclusive(rng, getMinAmount(), getMaxAmount())));
    }

    private ItemMeta applyProperties(ItemStack is, RNG rng, boolean debug, IrisLootTable table) {
        ItemMeta m = is.getItemMeta();
        if (m == null) {
            return null;
        }

        for (IrisEnchantment i : getEnchantments()) {
            i.apply(rng, m);
        }

        for (IrisAttributeModifier i : getAttributes()) {
            i.apply(rng, m);
        }

        m.setUnbreakable(isUnbreakable());
        for (ItemFlag i : getResolvedItemFlags()) {
            m.addItemFlags(i);
        }

        if (getCustomModel() != null) {
            CustomModelDataComponent customModelData = m.getCustomModelDataComponent();
            customModelData.setFloats(List.of(getCustomModel().floatValue()));
            m.setCustomModelDataComponent(customModelData);
        }

        if (is.getType().getMaxDurability() > 0 && m instanceof Damageable d) {
            int max = is.getType().getMaxDurability();
            d.setDamage((int) Math.round(Math.max(0, Math.min(max, (1D - rng.d(getMinDurability(), getMaxDurability())) * max))));
        }

        if (getLeatherColor() != null && m instanceof LeatherArmorMeta leather) {
            Color c = Color.decode(getLeatherColor());
            leather.setColor(org.bukkit.Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue()));
        }

        if (getDyeColor() != null && m instanceof Colorable colorable) {
            colorable.setColor(getDyeColor());
        }

        if (displayName != null) {
            m.setDisplayName(C.translateAlternateColorCodes('&', displayName));
        }

        KList<String> lore = new KList<>();

        getLore().forEach((i) ->
        {
            String mf = C.translateAlternateColorCodes('&', i);

            if (mf.length() > 24) {
                for (String g : Form.wrapWords(mf, 24).split("\\Q\n\\E")) {
                    lore.add(g.trim());
                }
            } else {
                lore.add(mf);
            }
        });

        if (debug) {
            if (table == null) {
                if (lore.isNotEmpty()) {
                    lore.add(C.GRAY + "--------------------");
                }
                lore.add(C.GRAY + "1 in " + (getRarity()) + " Chance (" + Form.pc(1D / (getRarity()), 5) + ")");
            } else {
                if (lore.isNotEmpty()) {
                    lore.add(C.GRAY + "--------------------");
                }

                lore.add(C.GRAY + "From: " + table.getName() + " (" + Form.pc(1D / table.getRarity(), 5) + ")");
                lore.add(C.GRAY + "1 in " + (table.getRarity() * getRarity()) + " Chance (" + Form.pc(1D / (table.getRarity() * getRarity()), 5) + ")");
            }
        }

        m.setLore(lore);

        return m;
    }
}
