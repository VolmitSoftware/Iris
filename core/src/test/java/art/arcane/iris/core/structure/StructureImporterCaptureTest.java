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

package art.arcane.iris.core.structure;

import art.arcane.iris.core.structure.authoring.StructureCapability;
import art.arcane.iris.engine.UsableBlockPlatform;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.StructureTemplate;
import com.volmit.tooling.TemplateBlock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructureImporterCaptureTest {
    @Before
    public void bindBlocks() {
        UsableBlockPlatform.bind();
    }

    @After
    public void unbindBlocks() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void captureReturnsAnInMemorySnapshotWithDistinctBlockMarkerLoss() {
        StructureTemplate structure = new StructureTemplate(
                new BlockPosition(2, 3, 4),
                2,
                1,
                List.of(block("minecraft:structure_block", 0)));

        StructureImporter.CapturedStructure captured = StructureImporter.captureStructure(structure);

        assertEquals(2, captured.width());
        assertEquals(3, captured.height());
        assertEquals(4, captured.depth());
        assertEquals(1, captured.structureMarkers());
        assertEquals(0, captured.blocks());
        assertEquals(0, captured.nonAirBlocks());
        assertTrue(captured.capabilities().contains(StructureCapability.BLOCKS));
        assertFalse(captured.capabilities().contains(StructureCapability.CONNECTORS));
        assertTrue(captured.losses().stream().anyMatch(loss ->
                loss.capability() == StructureCapability.BLOCKS
                        && loss.code().equals("structure_markers_resolved")));
        assertFalse(captured.losses().stream().anyMatch(loss -> loss.code().equals("connectors_not_imported")));
    }

    @Test
    public void captureCountsStoredAirSeparatelyFromPhysicalBlocks() {
        StructureTemplate structure = new StructureTemplate(
                new BlockPosition(2, 1, 1),
                1,
                0,
                List.of(block("minecraft:air", 0), block("minecraft:stone", 1)));

        StructureImporter.CapturedStructure captured = StructureImporter.captureStructure(structure);

        assertEquals(2, captured.blocks());
        assertEquals(1, captured.nonAirBlocks());
    }

    private static TemplateBlock block(String state, int x) {
        return TemplateBlock.of(new BlockPosition(x, 0, 0), BlockState.parse(state), Optional.empty());
    }
}
