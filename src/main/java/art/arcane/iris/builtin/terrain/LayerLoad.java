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

import java.util.List;

/** The layers of a biome's surface, which studio's layer view counts. */
public final class LayerLoad {
    private static final LayersSettings DEFAULT_LAYERS = new LayersSettings();

    private LayerLoad() {
    }

    /** The layers of the biome's surface, top first; a biome that declares no layers has the one layer of grass the settings default to. */
    public static List<PaletteLayer> layersOf(LayersSettings declared) {
        return (declared == null ? DEFAULT_LAYERS : declared).layers();
    }
}
