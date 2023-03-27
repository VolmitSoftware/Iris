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

package com.volmit.iris.engine.object;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.volmit.iris.Iris;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.material.Colorable;

import java.awt.*;
import java.util.Optional;

@Snippet("loot")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a loot entry")
@Data
public class IrisLoot {
    private final transient AtomicCache<CNG> chance = new AtomicCache<>();
    @Desc("The target inventory slot types to fill this loot with")
    private InventorySlotType slotTypes = InventorySlotType.STORAGE;
    @MinNumber(1)
    @Desc("The sub rarity of this loot. Calculated after this loot table has been picked.")
    private int rarity = 1;
    @MinNumber(1)
    @Desc("Minimum amount of this loot")
    private int minAmount = 1;
    @MinNumber(1)
    @Desc("Maximum amount of this loot")
    private int maxAmount = 1;
    @MinNumber(1)
    @Desc("The display name of this item")
    private String displayName = null;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Minimum durability percent")
    private double minDurability = 0;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Maximum durability percent")
    private double maxDurability = 1;
    @Desc("Define a custom model identifier 1.14+ only")
    private Integer customModel = null;
    @Desc("Set this to true to prevent it from being broken")
    private boolean unbreakable = false;
    @ArrayType(min = 1, type = ItemFlag.class)
    @Desc("The item flags to add")
    private KList<ItemFlag> itemFlags = new KList<>();
    @Desc("Apply enchantments to this item")
    @ArrayType(min = 1, type = IrisEnchantment.class)
    private KList<IrisEnchantment> enchantments = new KList<>();
    @Desc("Apply attribute modifiers to this item")
    @ArrayType(min = 1, type = IrisAttributeModifier.class)
    private KList<IrisAttributeModifier> attributes = new KList<>();
    @ArrayType(min = 1, type = String.class)
    @Desc("Add lore to this item")
    private KList<String> lore = new KList<>();
    @RegistryListItemType
    @Required
    @Desc("This is the item or block type. Does not accept minecraft:*, only materials such as DIAMOND_SWORD or DIRT. The exception are modded materials, as they require a namespace.")
    private String type = "";
    @Desc("The dye color")
    private DyeColor dyeColor = null;
    @Desc("The leather armor color")
    private String leatherColor = null;
    @Desc("Defines a custom NBT Tag for the item.")
    private KMap<String, Object> customNbt;

    public Material getType() {
        return B.getMaterial(type);
    }

    public ItemStack get(boolean debug, RNG rng) {
        try {
            ItemStack is = getItemStack(rng);
            if (is == null)
                return new ItemStack(Material.AIR);
            is.setItemMeta(applyProperties(is, rng, debug, null));
            return applyCustomNbt(is);
        } catch (Throwable e) {
            Iris.reportError(e);
            return new ItemStack(Material.AIR);
        }
    }

    public ItemStack get(boolean debug, boolean giveSomething, IrisLootTable table, RNG rng, int x, int y, int z) {
        if (debug) {
            chance.reset();
        }

        if (giveSomething || chance.aquire(() -> NoiseStyle.STATIC.create(rng)).fit(1, rarity * table.getRarity(), x, y, z) == 1) {
            try {
                ItemStack is = getItemStack(rng);
                if (is == null)
                    return null;
                is.setItemMeta(applyProperties(is, rng, debug, table));
                return applyCustomNbt(is);
            } catch (Throwable e) {
                //Iris.reportError(e);
                e.printStackTrace();
            }
        }

        return null;
    }

    // TODO Better Third Party Item Acquisition
    private ItemStack getItemStack(RNG rng) {
        if (!type.startsWith("minecraft:") && type.contains(":")) {
            Optional<ItemStack> opt = Iris.service(ExternalDataSVC.class).getItemStack(Identifier.fromString(type));
            if (opt.isEmpty()) {
                Iris.warn("Unknown Material: " + type);
                return new ItemStack(Material.AIR);
            }
            ItemStack is = opt.get();
            is.setAmount(Math.max(1, rng.i(getMinAmount(), getMaxAmount())));
            return is;
        }
        return new ItemStack(getType(), Math.max(1, rng.i(getMinAmount(), getMaxAmount())));
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
        for (ItemFlag i : getItemFlags()) {
            m.addItemFlags(i);
        }

        if (getCustomModel() != null) {
            m.setCustomModelData(getCustomModel());
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

        if(displayName != null) {
            m.setLocalizedName(C.translateAlternateColorCodes('&', displayName));
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


    private ItemStack applyCustomNbt(ItemStack stack) throws CommandSyntaxException {
        if (customNbt == null || customNbt.isEmpty())
            return stack;
        net.minecraft.world.item.ItemStack s = CraftItemStack.asNMSCopy(stack);
        CompoundTag tag = TagParser.parseTag(new JSONObject(customNbt).toString());
        tag.merge(s.getOrCreateTag());
        s.setTag(tag);
        return CraftItemStack.asBukkitCopy(s);
    }
}
