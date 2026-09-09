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

package art.arcane.iris.engine.platform.studio.generators;

import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.WrongEngineBroException;
import art.arcane.iris.engine.platform.studio.BiomeBuffetLayout;
import art.arcane.iris.engine.platform.studio.EnginedStudioGenerator;
import art.arcane.iris.util.common.data.BoundBlockState;
import art.arcane.iris.util.project.context.IrisContext;

public class BiomeBuffetGenerator extends EnginedStudioGenerator {
    private static final BoundBlockState FLOOR = BoundBlockState.of("BARRIER");
    private final BiomeBuffetLayout layout;

    public BiomeBuffetGenerator(Engine engine) {
        super(engine);
        layout = new BiomeBuffetLayout(engine.getDimension(), engine);
    }

    @Override
    public void generateChunk(Engine engine, TerrainChunk tc, int x, int z) throws WrongEngineBroException {
        if (layout.chunk(x, z) == null) {
            try (GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_biome_buffet_stage");
                 IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                tc.setRegion(0, 0, 0, 16, 1, 16, FLOOR.get());
            }
            return;
        }

        try (GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_biome_buffet_stage");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            engine.generate(x << 4, z << 4, tc, true);
        }
    }

}
