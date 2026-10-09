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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import com.volmit.resource.PackResources;
import com.volmit.service.LocateResult;
import com.volmit.service.StructureLocator;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * How {@code iris:structure} lets Iris find its structures: the structures of the pack's {@code structures} folder,
 * the ones the world's placements assemble from jigsaw pools, and their nearest start, searched with
 * {@link IrisStructureLocator}. Vanilla structures that Iris places through native placements are not among them; the
 * host's vanilla structure handling finds those.
 */
public final class StructureLocating implements StructureLocator {
    @Override
    public Set<String> structures(PackResources pack) {
        return new LinkedHashSet<>(StructureResources.structureKeysOf(pack));
    }

    @Override
    public Set<String> locatable(PackWorld world) {
        return IrisStructureLocator.locatableEditableKeys(engine(world));
    }

    @Override
    public LocateResult locate(PackWorld world, String key, int fromX, int fromZ, int maxRadiusChunks) {
        Engine engine = engine(world);
        IrisStructureLocator.LocateResult result = IrisStructureLocator.locate(engine, key, fromX, fromZ, maxRadiusChunks);
        return switch (result.status()) {
            case FOUND -> LocateResult.found(new BlockPosition(result.originX(), result.baseY() - engine.getMinHeight(), result.originZ()));
            case NOT_FOUND -> LocateResult.NOT_FOUND;
            case SEARCH_LIMIT_REACHED -> LocateResult.SEARCH_LIMIT_REACHED;
        };
    }

    private static Engine engine(PackWorld world) {
        return IrisPackWorld.of(world).engine();
    }
}
