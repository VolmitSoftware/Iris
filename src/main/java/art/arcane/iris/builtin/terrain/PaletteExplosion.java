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
package art.arcane.iris.builtin.terrain;

import com.volmit.config.FeatureSettings;
import com.volmit.config.FeatureSettings.Entry;

import java.util.List;

/** Whether a dimension separates the layers of its biomes with barrier blocks to show them apart, and how many barriers it puts between them. */
public record PaletteExplosion(boolean exploded, int size) {
    public static final PaletteExplosion NONE = new PaletteExplosion(false, ExplodeBiomePaletteSizeSettings.DEFAULT_SIZE);

    /** What the dimension at the column of {@code settings} declares. */
    public static PaletteExplosion of(FeatureSettings settings, int worldX, int worldZ) {
        boolean exploded = dimensionValue(settings.at(ExplodeBiomePalettesSettings.class, worldX, worldZ), ExplodeBiomePalettesSettings::explode, false);
        int size = dimensionValue(settings.at(ExplodeBiomePaletteSizeSettings.class, worldX, worldZ), ExplodeBiomePaletteSizeSettings::size,
                ExplodeBiomePaletteSizeSettings.DEFAULT_SIZE);
        return new PaletteExplosion(exploded, size);
    }

    private static <S extends com.volmit.IrisFeature, V> V dimensionValue(List<Entry<S>> entries, java.util.function.Function<S, V> read, V fallback) {
        return entries.stream().filter(entry -> entry.level() == com.volmit.FeatureAttachment.Target.DIMENSION).findFirst()
                .map(entry -> read.apply(entry.value())).orElse(fallback);
    }
}
