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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.structure.StructureHost;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.mantle.components.StructurePlacing;
import art.arcane.iris.engine.object.structure.IrisStructure;
import com.volmit.ModuleLog;
import com.volmit.resource.ResourceId;
import com.volmit.stage.MantleStageContext;

import java.util.List;
import java.util.Optional;

/** Iris's {@link StructureHost}: the engine's {@link StructurePlacing}, one per engine runtime, places and sizes its structures. */
final class IrisStructureHost implements StructureHost {
    private final Engine engine;
    private final ModuleLog log;

    IrisStructureHost(Engine engine, ModuleLog log) {
        this.engine = engine;
        this.log = log;
    }

    @Override
    public void placeChunk(MantleStageContext context) {
        placing().place(context);
    }

    @Override
    public int reach() {
        return placing().reach();
    }

    @Override
    public Optional<List<String>> lootTables(ResourceId structureId) {
        IrisPackResources resources = engine.getData().getPackResources();
        IrisStructure structure = StructureResources.structure(resources, resources.keyOf(structureId));
        if (structure == null) {
            return Optional.empty();
        }
        if (structure.getLoot() == null) {
            return Optional.of(List.of());
        }
        return Optional.of(structure.getLoot().stream().filter(table -> table != null && !table.isBlank()).toList());
    }

    private StructurePlacing placing() {
        return engine.getGeneration().world().state(StructurePlacing.class, generating -> new StructurePlacing(generating, log));
    }
}
