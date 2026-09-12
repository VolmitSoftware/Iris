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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListEntityType;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.platform.bukkit.registry.RegistryUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

import java.util.Locale;

@Snippet("custom-biome-spawn")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A custom biome spawn")
@Data
public class IrisBiomeCustomSpawn {
    private final transient AtomicCache<EntityType> typeResolved = new AtomicCache<>();
    @Required
    @RegistryListEntityType
    @Description("The biome's entity type")
    private String type = "minecraft:cow";

    public EntityType getType() {
        String typeKey = getTypeKey();
        if (typeKey == null) {
            return null;
        }
        return typeResolved.aquire(() -> {
            NamespacedKey namespacedKey = NamespacedKey.fromString(typeKey);
            return namespacedKey == null ? null : RegistryUtil.lookup(EntityType.class).get(namespacedKey);
        });
    }

    public String getTypeKey() {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    @MinNumber(1)
    @Description("The min to spawn")
    private int minCount = 2;

    @MinNumber(1)
    @Description("The max to spawn")
    private int maxCount = 5;

    @MinNumber(1)
    @MaxNumber(1000)
    @Description("The weight in this group. Higher weight, the more common this type is spawned")
    private int weight = 1;

    @Description("The rarity")
    private IrisBiomeCustomSpawnType group = IrisBiomeCustomSpawnType.MISC;
}
