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

package art.arcane.iris.modules.objects;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * One region's or biome's declaration of settings, named by the id a {@link com.volmit.world.BiomeSample} gives the
 * region or biome ({@code overworld:desert/dunes}) rather than the source id of its settings entry
 * ({@code overworld:biome/desert/dunes}).
 */
public record Declaration<F extends IrisFeature>(Level level, ResourceId id, F settings) {
    private static final char KIND_SEPARATOR = '/';

    /** Every region and every reachable biome of {@code world}'s dimension that declares the settings, regions first. */
    public static <F extends IrisFeature> List<Declaration<F>> allOf(PackWorld world, Class<F> type) {
        List<Declaration<F>> found = new ArrayList<>();
        for (Entry<F> entry : world.settings().declared(type)) {
            if (entry.level() == Level.REGION || entry.level() == Level.BIOME) {
                found.add(new Declaration<>(entry.level(), sampleId(entry.source()), entry.value()));
            }
        }
        return found;
    }

    private static ResourceId sampleId(ResourceId source) {
        String path = source.path();
        return new ResourceId(source.namespace(), path.substring(path.indexOf(KIND_SEPARATOR) + 1));
    }
}
