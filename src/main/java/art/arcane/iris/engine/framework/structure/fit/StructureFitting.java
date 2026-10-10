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

package art.arcane.iris.engine.framework.structure.fit;

import art.arcane.iris.builtin.structure.StructureStilts;
import art.arcane.iris.engine.framework.structure.ImportedStructures;
import art.arcane.iris.engine.framework.structure.NativeStructureFit;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSpec;
import com.volmit.vanilla.StructureFit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * How {@code iris:structure} fits the platform's starts into one chunk. Before any piece is placed it fits the
 * surface around SOURCE beard and VACUUM starts, integrates the terrain of every start and bridges short gaps between
 * rigid pieces; after each start's pieces it builds that start's stilts, and after the last one it repairs the gaps
 * left under VACUUM foundations. A start the module placed itself is fitted the way its memo records; one of the
 * platform's own starts the way the dimension's {@code importedStructures} adjust it today.
 */
public final class StructureFitting {
    private StructureFitting() {
    }

    public static void beforePieces(StructureFit fit) {
        chunkOf(fit).prepare();
    }

    public static void afterPieces(StructureFit fit, StructureFit.Start placed) {
        ChunkFit chunk = chunkOf(fit);
        chunk.buildStilts(placed);
        if (placed == fit.starts().getLast()) {
            chunk.repairFoundations();
        }
    }

    private static ChunkFit chunkOf(StructureFit fit) {
        return fit.state(ChunkFit.class, () -> new ChunkFit(fit));
    }

    /** One chunk's fit: its starts with their resolved fits, and the foundations VACUUM raised the terrain to. */
    private static final class ChunkFit {
        private final StructureFit fit;
        private final FitBlocks blocks;
        private final Box area;
        private final List<FitStart> starts;
        private final IntBinaryOperator surfaceHeight;
        private SurfaceFitting.VacuumFoundationPlan vacuumFoundations = SurfaceFitting.VacuumFoundationPlan.EMPTY;

        private ChunkFit(StructureFit fit) {
            this.fit = fit;
            this.blocks = new FitBlocks(fit.blocks(), fit.bottomY());
            this.area = Box.of(fit.area(), fit.bottomY());
            this.starts = resolvedStarts(fit);
            this.surfaceHeight = (x, z) -> fit.surfaceY(x, z) + fit.bottomY();
        }

        private static List<FitStart> resolvedStarts(StructureFit fit) {
            List<FitStart> starts = new ArrayList<>(fit.starts().size());
            for (StructureFit.Start start : fit.starts()) {
                starts.add(new FitStart(start, fitOf(fit, start), fit.bottomY()));
            }
            return List.copyOf(starts);
        }

        private static NativeStructureFit fitOf(StructureFit fit, StructureFit.Start start) {
            return start.memo()
                    .map(NativeStructureFit::of)
                    .orElseGet(() -> ImportedStructures.of(fit.world().settings())
                            .adjustment(start.structure().toString(), start.step().isUnderground()));
        }

        private void prepare() {
            vacuumFoundations = SurfaceFitting.prepareSurfaceStructures(blocks, area, starts, surfaceHeight,
                    SurfaceFitting.MAX_SURFACE_TEMPLATE_CELLS);
            TerrainIntegration.prepareTerrain(blocks, area, starts, fit.seed(), this::naturalBlock, this::paletteBlock);
            RigidSupport.bridgeRigidPieceSupport(blocks, area, starts);
        }

        private void buildStilts(StructureFit.Start placed) {
            FitStart start = fitStartOf(placed);
            StructureStilts stilts = start.fit().stilts();
            if (stilts == null) {
                return;
            }
            FoundationStilts.placeStilts(blocks, area, start, stilts, fit.seed(), this::paletteBlock, surfaceHeight,
                    !start.step().isUnderground());
        }

        private void repairFoundations() {
            SurfaceFitting.repairVacuumFoundations(blocks, area, vacuumFoundations);
        }

        private FitStart fitStartOf(StructureFit.Start placed) {
            for (FitStart start : starts) {
                if (start.start() == placed) {
                    return start;
                }
            }
            throw new IllegalArgumentException("Start " + placed.structure() + " is not one of the chunk's starts");
        }

        private BlockState naturalBlock(int worldY) {
            return fit.naturalBlock(worldY - fit.bottomY());
        }

        private BlockState paletteBlock(PaletteSpec palette, long seed, int x, int y, int z) {
            return fit.world().resources().blocks().palette(palette, seed).at(x, y, z);
        }
    }
}
