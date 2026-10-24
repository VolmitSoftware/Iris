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

package art.arcane.iris.modules.vanillafeatures;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MinItems;
import com.volmit.vanilla.DecorationStep;

import java.util.Collections;
import java.util.List;

/**
 * Whether a dimension runs the platform's own placed features over its terrain, written as
 * {@code "importedFeatures": {"enabled": true, "disabledSteps": ["VEGETAL_DECORATION"]}}: the switch, and the features and
 * decoration steps it leaves out. IrisApi types only.
 */
@Desc("Controls native vanilla, mod, and ingested datapack PLACED FEATURE generation for this dimension. Placed features are ores, trees, plants, springs, geodes and every other decoration entry a biome declares. This is OFF by default: with 'enabled' false Iris generates exactly the terrain it always has and no native feature runs. With it true, every placed feature the biome's vanilla derivative declares runs over Iris terrain, in vanilla step order, on the same worldgen thread vanilla uses. Carvers are never imported - Iris has no NoiseGeneratorSettings, so there is nothing for a carver to carve against. Family matching on 'disabled' uses namespace, slash, or underscore boundaries, so 'minecraft:ore' covers every vanilla ore feature without matching unrelated names.")
@Feature(id = "iris:vanilla-features", name = "Vanilla features", key = "importedFeatures", targets = Level.DIMENSION)
public final class ImportedFeaturesSettings implements IrisFeature {
    @Desc("Master switch. False (the default) means no native placed feature generates and chunk output is identical to a pack without this block at all. True runs the vanilla decoration feature pass over Iris terrain.")
    private boolean enabled = false;

    @MinItems(1)
    @Desc("Placed feature keys to deny explicitly, e.g. 'minecraft:ore_diamond'. A namespace:path prefix also matches, so 'minecraft:ore' denies every vanilla ore and 'minecraft:trees' denies every tree placement. Every key not matched here generates while 'enabled' is true.")
    private List<String> disabled = List.of();

    @MinItems(1)
    @Desc("Restrict feature generation to these decoration steps only. Empty (the default) means every step is eligible. Use this to import ores without importing vegetation: set it to UNDERGROUND_ORES.")
    private List<DecorationStep> steps = List.of();

    @MinItems(1)
    @Desc("Decoration steps to deny. Applied after 'steps', so a step listed here never generates even if 'steps' allows it. VEGETAL_DECORATION is the usual entry for packs that grow their own trees.")
    private List<DecorationStep> disabledSteps = List.of();

    public ImportedFeaturesSettings() {
    }

    /** Whether the platform's placed features run over the dimension's terrain. */
    public boolean enabled() {
        return enabled;
    }

    /** Family patterns of the placed features the dimension leaves out. */
    public List<String> disabled() {
        return listOrEmpty(disabled);
    }

    /** The only decoration steps whose features generate; empty for every step. */
    public List<DecorationStep> steps() {
        return listOrEmpty(steps);
    }

    /** Decoration steps whose features never generate, whatever {@link #steps} says. */
    public List<DecorationStep> disabledSteps() {
        return listOrEmpty(disabledSteps);
    }

    private static <T> List<T> listOrEmpty(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }
}
