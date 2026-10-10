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

package art.arcane.iris.builtin.spawn;

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MinItems;

import java.util.Collections;
import java.util.List;

/**
 * The ambient effects one region or biome declares, written as {@code "effects": [...]}, the list of
 * {@link AmbientEffect}s. They play around every player in the area.
 */
@Desc("Effects are ambient effects such as potion effects, random sounds, or even particles around each player. All of these effects are played via packets so two players won't see/hear each others effects.\nDue to performance reasons, effects will play arround the player even if where the effect was played is no longer in the biome the player is in.")
@Feature(id = "iris:spawn/effects", name = "Effects", key = "effects", root = "effects", targets = {Target.REGION, Target.BIOME})
public final class EffectSettings implements IrisFeature {
    @MinItems(1)
    @Desc("Effects are ambient effects such as potion effects, random sounds, or even particles around each player.")
    private List<AmbientEffect> effects = List.of();

    public EffectSettings() {
    }

    public List<AmbientEffect> effects() {
        return effects == null ? List.of() : Collections.unmodifiableList(effects);
    }
}
