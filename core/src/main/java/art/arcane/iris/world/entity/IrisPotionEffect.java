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

import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListPotionEffect;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor

@Snippet("potion-effect")
@Description("An iris potion effect")
@Data
public class IrisPotionEffect {
    private final transient AtomicCache<PotionEffectType> pt = new AtomicCache<>();
    @Required
    @RegistryListPotionEffect
    @Description("The potion effect to apply in this area")
    private String potionEffect = "";
    @Required
    @MinNumber(-1)
    @MaxNumber(1024)
    @Description("The Potion Strength or -1 to disable")
    private int strength = -1;
    @Required
    @MinNumber(1)
    @Description("The time the potion will last for")
    private int ticks = 200;
    @Description("Is the effect ambient")
    private boolean ambient = false;
    @Description("Is the effect showing particles")
    private boolean particles = true;

    public PotionEffectType getRealType() {
        return pt.aquire(() ->
        {
            if (getPotionEffect() == null || getPotionEffect().isEmpty()) {
                return PotionEffectType.LUCK;
            }

            try {
                PotionEffectType resolved = PotionEffectTypes.resolve(getPotionEffect());
                if (resolved != null) {
                    return resolved;
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }

            if (PotionEffectTypes.shouldWarn(getPotionEffect())) {
                IrisLogging.warn("Unknown Potion Effect Type: \"" + getPotionEffect() + "\". Valid types: " + PotionEffectTypes.knownTypesList());
            }

            return PotionEffectType.LUCK;
        });
    }

    public void apply(LivingEntity p) {
        if (strength > -1) {
            if (p.hasPotionEffect(getRealType())) {
                PotionEffect e = p.getPotionEffect(getRealType());
                if (e.getAmplifier() > strength) {
                    return;
                }

                p.removePotionEffect(getRealType());
            }

            p.addPotionEffect(new PotionEffect(getRealType(), ticks, strength, ambient, particles, false));
        }
    }
}
