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
package art.arcane.iris.modules.terrain;

import com.volmit.config.FeatureSettings;

/** Whether a dimension separates the layers of its biomes with barrier blocks to show them apart, and how many barriers it puts between them. */
public record PaletteExplosion(boolean exploded, int size) {
    public static final PaletteExplosion NONE = new PaletteExplosion(false, ExplodeBiomePaletteSizeSettings.DEFAULT_SIZE);

    /** What the dimension at the column of {@code settings} declares. */
    public static PaletteExplosion of(FeatureSettings settings, int worldX, int worldZ) {
        boolean exploded = settings.at(ExplodeBiomePalettesSettings.class, worldX, worldZ).dimension()
                .map(ExplodeBiomePalettesSettings::explode).orElse(false);
        int size = settings.at(ExplodeBiomePaletteSizeSettings.class, worldX, worldZ).dimension()
                .map(ExplodeBiomePaletteSizeSettings::size).orElse(ExplodeBiomePaletteSizeSettings.DEFAULT_SIZE);
        return new PaletteExplosion(exploded, size);
    }
}
