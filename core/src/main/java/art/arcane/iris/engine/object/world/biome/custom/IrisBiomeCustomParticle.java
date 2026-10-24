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

package art.arcane.iris.engine.object.world.biome.custom;

import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.iris.util.common.data.registry.RegistryUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;

@Snippet("custom-biome-particle")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A custom biome ambient particle")
@Data
public class IrisBiomeCustomParticle {
    private final transient AtomicCache<Particle> particleResolved = new AtomicCache<>();
    @Required
    @Desc("The biome's particle type")
    private String particle = "minecraft:flash";

    @MinNumber(1)
    @MaxNumber(10000)
    @Desc("The rarity")
    private int rarity = 35;

    /**
     * The authored particle key, normalized to a namespaced key. Platform-neutral: datapack
     * emission must use this and never {@link #getParticle()}, which only resolves on Bukkit.
     */
    public String getParticleKey() {
        if (particle == null || particle.isEmpty()) {
            return null;
        }
        return particle.indexOf(':') >= 0 ? particle : "minecraft:" + particle;
    }

    public Particle getParticle() {
        return particleResolved.aquire(() -> {
            NamespacedKey namespacedKey = NamespacedKey.fromString(particle);
            return namespacedKey == null ? null : RegistryUtil.lookup(Particle.class).get(namespacedKey);
        });
    }
}
