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

package art.arcane.iris.world.entity;

import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListEnchantment;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;


@Snippet("enchantment")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents an enchantment & level")
@Data
public class IrisEnchantment {
    @Required
    @RegistryListEnchantment
    @Description("The enchantment")
    private String enchantment;

    @MinNumber(1)
    @Description("Minimum amount of this loot")
    private int minLevel = 1;

    @MinNumber(1)
    @Description("Maximum amount of this loot")
    private int maxLevel = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance that this enchantment is applied (0 to 1)")
    private double chance = 1;

    public void apply(RNG rng, ItemMeta meta) {
        try {
            Enchantment enchant = resolve();
            if (enchant == null) {
                IrisLogging.warn("Unknown Enchantment: " + getEnchantment());
                return;
            }
            if (rng.nextDouble() < chance) {
                if (meta instanceof EnchantmentStorageMeta) {
                    ((EnchantmentStorageMeta) meta).addStoredEnchant(enchant, getLevel(rng), true);
                    return;
                }
                meta.addEnchant(enchant, getLevel(rng), true);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);

        }
    }

    /**
     * Resolves the authored key against the live enchantment registry. Accepts a bare path
     * ({@code sharpness}) or a full namespaced key ({@code mymod:vorpal}) - parity with the modded resolver.
     */
    private Enchantment resolve() {
        String raw = getEnchantment();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        NamespacedKey key = value.indexOf(':') >= 0 ? NamespacedKey.fromString(value) : NamespacedKey.minecraft(value);
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    public int getLevel(RNG rng) {
        return LootResolver.inclusive(rng, getMinLevel(), getMaxLevel());
    }
}
