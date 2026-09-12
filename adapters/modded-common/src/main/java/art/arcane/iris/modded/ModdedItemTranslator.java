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

package art.arcane.iris.modded;

import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.entity.IrisAttributeModifier;
import art.arcane.iris.world.entity.IrisEnchantment;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedItemTranslator {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private ModdedItemTranslator() {
    }

    public static KList<ItemStack> loot(IrisLootTable table, long lootSeed, InventorySlotType slot, ServerLevel level, int x, int y, int z) {
        KList<ItemStack> out = new KList<>();
        KList<IrisLoot> entries = table.getLoot();
        if (entries.isEmpty() || table.getMaxTries() <= 0) {
            return out;
        }

        RNG rng = LootResolver.tableRng(lootSeed, table, x, y, z);
        int m = 0;
        int c = 0;
        int mx = Math.max(0, LootResolver.inclusive(rng, table.getMinPicked(), table.getMaxPicked()));

        while (m < mx && c++ < table.getMaxTries()) {
            int entryIndex = rng.nextInt(entries.size());
            IrisLoot entry = entries.get(entryIndex);
            if (entry == null || entry.getSlotTypes() != slot) {
                continue;
            }
            ItemStack item = item(entry, table, rng, lootSeed, entryIndex, level, x, y, z);
            if (item != null && !item.isEmpty()) {
                out.add(item);
                m++;
            }
        }

        return out;
    }

    public static ItemStack stack(IrisLoot loot, RNG rng, ServerLevel level) {
        try {
            ItemStack stack = baseStack(loot, rng);
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            applyComponents(loot, stack, rng, level);
            applyCustomNbt(stack, loot.getCustomNbt());
            return stack;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return null;
        }
    }

    public static ItemStack item(IrisLoot loot, IrisLootTable table, RNG rng, long lootSeed, int entryIndex, ServerLevel level, int x, int y, int z) {
        long combinedRarity = LootResolver.combinedRarity(loot.getRarity(), table.getRarity());
        if (!LootResolver.spatialOneIn(lootSeed, table, entryIndex, x, y, z, combinedRarity)) {
            return null;
        }

        try {
            ItemStack stack = baseStack(loot, rng);
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            applyComponents(loot, stack, rng, level);
            applyCustomNbt(stack, loot.getCustomNbt());
            return stack;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return null;
        }
    }

    private static ItemStack baseStack(IrisLoot loot, RNG rng) {
        String raw = loot.getTypeKey();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String key = raw.toLowerCase(Locale.ROOT);
        Identifier id = Identifier.tryParse(key.contains(":") ? key : "minecraft:" + key);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            warnOnce("item:" + key, "Iris loot: unknown item '" + raw + "'");
            return null;
        }

        Item item = BuiltInRegistries.ITEM.getValue(id);
        return new ItemStack(item, Math.max(1, LootResolver.inclusive(rng, loot.getMinAmount(), loot.getMaxAmount())));
    }

    private static void applyComponents(IrisLoot loot, ItemStack stack, RNG rng, ServerLevel level) {
        applyEnchantments(loot, stack, rng, level);
        applyAttributes(loot, stack, rng, level);

        if (loot.isUnbreakable()) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        }

        if (loot.getItemFlags().isNotEmpty()) {
            applyItemFlags(loot, stack);
        }

        if (loot.getCustomModel() != null) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(loot.getCustomModel().floatValue()), List.of(), List.of(), List.of()));
        }

        if (stack.getMaxDamage() > 0) {
            int max = stack.getMaxDamage();
            int damage = (int) Math.round(Math.max(0, Math.min(max, (1D - rng.d(loot.getMinDurability(), loot.getMaxDurability())) * max)));
            stack.set(DataComponents.DAMAGE, damage);
        }

        if (loot.getLeatherColor() != null) {
            try {
                int rgb = Integer.decode(loot.getLeatherColor()) & 0xFFFFFF;
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
            } catch (NumberFormatException e) {
                warnOnce("leatherColor:" + loot.getLeatherColor(), "Iris loot: invalid leatherColor '" + loot.getLeatherColor() + "'");
            }
        }

        String dye = loot.getDyeColorKey();
        if (dye != null) {
            applyDyeColor(stack, dye);
        }

        if (loot.getDisplayName() != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(colorize(loot.getDisplayName())));
        }

        applyLore(loot, stack);
    }

    private static void applyEnchantments(IrisLoot loot, ItemStack stack, RNG rng, ServerLevel level) {
        if (loot.getEnchantments().isEmpty()) {
            return;
        }

        Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        DataComponentType<ItemEnchantments> component = stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(component, ItemEnchantments.EMPTY));
        boolean changed = false;

        for (IrisEnchantment enchantment : loot.getEnchantments()) {
            String name = enchantment.getEnchantment();
            if (name == null || name.isBlank()) {
                continue;
            }
            // Same normalization as IrisEnchantment.resolve() on Bukkit: trim, lowercase, spaces to underscores.
            // Without it 'Fire Aspect' resolves on Bukkit and warns as unknown here, for the same pack.
            String key = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            Identifier id = Identifier.tryParse(key.contains(":") ? key : "minecraft:" + key);
            Optional<Holder.Reference<Enchantment>> holder = id == null ? Optional.empty() : registry.get(id);
            if (holder.isEmpty()) {
                warnOnce("enchantment:" + key, "Iris loot: unknown enchantment '" + name + "'");
                continue;
            }
            if (rng.nextDouble() < enchantment.getChance()) {
                mutable.set(holder.get(), enchantment.getLevel(rng));
                changed = true;
            }
        }

        if (changed) {
            stack.set(component, mutable.toImmutable());
        }
    }

    private static void applyAttributes(IrisLoot loot, ItemStack stack, RNG rng, ServerLevel level) {
        if (loot.getAttributes().isEmpty()) {
            return;
        }

        Registry<Attribute> registry = level.registryAccess().lookupOrThrow(Registries.ATTRIBUTE);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        boolean changed = false;

        for (IrisAttributeModifier attribute : loot.getAttributes()) {
            if (rng.nextDouble() >= attribute.getChance()) {
                continue;
            }
            double amount = attribute.getAmount(rng);
            Holder<Attribute> holder = resolveAttribute(registry, attribute.getAttribute());
            if (holder == null) {
                warnOnce("attribute:" + attribute.getAttribute(), "Iris loot: unknown attribute '" + attribute.getAttribute() + "'");
                continue;
            }
            Identifier modifierId = attributeModifierId(attribute.getName());
            if (modifierId == null) {
                continue;
            }
            AttributeModifier modifier = new AttributeModifier(modifierId, amount, operationFor(attribute.getOperation()));
            builder.add(holder, modifier, EquipmentSlotGroup.ANY);
            changed = true;
        }

        if (changed) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        }
    }

    public static Holder<Attribute> resolveAttribute(Registry<Attribute> registry, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String value = key.trim().toLowerCase(Locale.ROOT);
        Holder<Attribute> direct = lookupAttribute(registry, Identifier.tryParse(value.indexOf(':') >= 0 ? value : "minecraft:" + value));
        if (direct != null) {
            return direct;
        }
        if (value.startsWith("generic_")) {
            return lookupAttribute(registry, Identifier.tryParse("minecraft:" + value.substring("generic_".length())));
        }
        if (value.startsWith("generic.")) {
            return lookupAttribute(registry, Identifier.tryParse("minecraft:" + value.substring("generic.".length())));
        }
        return null;
    }

    private static Holder<Attribute> lookupAttribute(Registry<Attribute> registry, Identifier id) {
        if (id == null) {
            return null;
        }
        Optional<Holder.Reference<Attribute>> holder = registry.get(id);
        return holder.isPresent() ? holder.get() : null;
    }

    public static AttributeModifier.Operation operationFor(String operation) {
        if (operation == null || operation.isBlank()) {
            return AttributeModifier.Operation.ADD_VALUE;
        }
        return switch (operation.trim().toUpperCase(Locale.ROOT)) {
            case "ADD_SCALAR", "ADD_MULTIPLIED_BASE" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "MULTIPLY_SCALAR_1", "ADD_MULTIPLIED_TOTAL" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    private static Identifier attributeModifierId(String name) {
        String source = name == null || name.isBlank() ? "modifier" : name;
        String normalized = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        if (normalized.isBlank()) {
            normalized = "modifier";
        }
        return Identifier.tryParse("iris:" + normalized);
    }

    private static void applyItemFlags(IrisLoot loot, ItemStack stack) {
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        boolean changed = false;

        for (String flag : loot.getItemFlags()) {
            if (flag == null || flag.isBlank()) {
                continue;
            }
            DataComponentType<?> hidden = tooltipComponentFor(flag.trim().toUpperCase(Locale.ROOT));
            if (hidden == null) {
                warnOnce("itemFlag:" + flag, "Iris loot: item flag '" + flag + "' has no modded tooltip equivalent; skipping");
                continue;
            }
            display = display.withHidden(hidden, true);
            changed = true;
        }

        if (changed) {
            stack.set(DataComponents.TOOLTIP_DISPLAY, display);
        }
    }

    private static DataComponentType<?> tooltipComponentFor(String flag) {
        return switch (flag) {
            case "HIDE_ENCHANTS" -> DataComponents.ENCHANTMENTS;
            case "HIDE_STORED_ENCHANTS" -> DataComponents.STORED_ENCHANTMENTS;
            case "HIDE_ATTRIBUTES" -> DataComponents.ATTRIBUTE_MODIFIERS;
            case "HIDE_UNBREAKABLE" -> DataComponents.UNBREAKABLE;
            case "HIDE_DESTROYS" -> DataComponents.CAN_BREAK;
            case "HIDE_PLACED_ON" -> DataComponents.CAN_PLACE_ON;
            case "HIDE_DYE" -> DataComponents.DYED_COLOR;
            case "HIDE_ARMOR_TRIM" -> DataComponents.TRIM;
            default -> null;
        };
    }

    private static void applyDyeColor(ItemStack stack, String dye) {
        DyeColor color = DyeColor.byName(dye.trim().toLowerCase(Locale.ROOT), null);
        if (color == null) {
            warnOnce("dyeColor:" + dye, "Iris loot: unknown dyeColor '" + dye + "'");
            return;
        }
        stack.set(DataComponents.BASE_COLOR, color);
    }

    private static void applyLore(IrisLoot loot, ItemStack stack) {
        if (loot.getLore().isEmpty()) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        for (String line : loot.getLore()) {
            String colored = colorize(line);
            if (colored.length() > 24) {
                for (String wrapped : Form.wrapWords(colored, 24).split("\\Q\n\\E")) {
                    lines.add(Component.literal(wrapped.trim()));
                }
            } else {
                lines.add(Component.literal(colored));
            }
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static void applyCustomNbt(ItemStack stack, KMap<String, Object> customNbt) {
        if (customNbt == null || customNbt.isEmpty()) {
            return;
        }
        try {
            CompoundTag tag = TagParser.parseCompoundFully(new JSONObject(customNbt).toString());
            tag.merge(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        } catch (CommandSyntaxException e) {
            warnOnce("customNbt:" + e.getMessage(), "Iris loot: invalid customNbt: " + e.getMessage());
        }
    }

    private static String colorize(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && COLOR_CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            IrisLogging.warn(message);
        }
    }

}
