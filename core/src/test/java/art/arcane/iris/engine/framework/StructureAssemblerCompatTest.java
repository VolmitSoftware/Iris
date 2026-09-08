/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.engine.framework;

import art.arcane.iris.core.compat.CompatAction;
import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.CompatRegistry;
import art.arcane.iris.core.compat.CompatStatus;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.structure.StructureAssemblyResult;
import art.arcane.iris.engine.framework.structure.StructureAssemblyStatus;
import art.arcane.iris.engine.object.IrisDirection;
import art.arcane.iris.engine.object.IrisJigsawConnector;
import art.arcane.iris.engine.object.IrisJigsawPiece;
import art.arcane.iris.engine.object.IrisJigsawPieceEntry;
import art.arcane.iris.engine.object.IrisJigsawPool;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.object.JigsawJoint;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.math.RNG;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructureAssemblerCompatTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    @Test
    public void anExcludedPieceIsNeverDrawnFromItsPool() {
        IrisData data = mock(IrisData.class);
        IrisJigsawPiece good = piece("good-object");
        IrisJigsawPiece bad = excluded(piece("bad-object"));
        when(data.load(IrisJigsawPool.class, "start", false)).thenReturn(pool("good-piece", "bad-piece"));
        when(data.load(IrisJigsawPool.class, "terminal", false)).thenReturn(new IrisJigsawPool());
        when(data.load(IrisJigsawPiece.class, "good-piece", false)).thenReturn(good);
        when(data.load(IrisJigsawPiece.class, "bad-piece", false)).thenReturn(bad);
        when(data.load(IrisObject.class, "good-object", false)).thenReturn(new IrisObject(1, 1, 1));
        when(data.load(IrisObject.class, "bad-object", false)).thenReturn(new IrisObject(1, 1, 1));

        for (int seed = 0; seed < 32; seed++) {
            StructureAssemblyResult result = StructureAssembler
                    .forData(data, structure("start"), new IrisPosition(0, 0, 0))
                    .assemble(new RNG(seed));

            assertTrue(result.detail(), result.hasOutput());
            assertTrue(result.pieces().toString(), result.pieces().stream()
                    .allMatch(placed -> "good-object".equals(placed.getPiece().getObject())));
        }
    }

    @Test
    public void aPoolWithOnlyExcludedPiecesStopsTheAssemblyWithoutThrowing() {
        IrisData data = mock(IrisData.class);
        IrisJigsawPool startPool = pool("bad-piece");
        startPool.setCompat(CompatStatus.excludedBy(List.of(finding(CompatAction.EXCLUDED))));
        when(data.load(IrisJigsawPool.class, "start", false)).thenReturn(startPool);
        when(data.load(IrisJigsawPool.class, "terminal", false)).thenReturn(new IrisJigsawPool());
        when(data.load(IrisJigsawPiece.class, "bad-piece", false)).thenReturn(excluded(piece("bad-object")));
        when(data.load(IrisObject.class, "bad-object", false)).thenReturn(new IrisObject(1, 1, 1));

        StructureAssemblyResult result = StructureAssembler
                .forData(data, structure("start"), new IrisPosition(0, 0, 0))
                .assemble(new RNG(3));

        assertFalse(result.hasOutput());
        assertEquals(StructureAssemblyStatus.FAILED_RULES, result.status());
    }

    private static IrisJigsawPiece excluded(IrisJigsawPiece piece) {
        piece.setCompat(CompatStatus.excludedBy(List.of(finding(CompatAction.EXCLUDED))));
        return piece;
    }

    private static CompatFinding finding(CompatAction action) {
        return new CompatFinding(CompatRegistry.BLOCK, "minecraft:sulfur", action, "jigsaw piece", "bad-piece", "object bad-object");
    }

    private static IrisJigsawPiece piece(String object) {
        IrisJigsawPiece piece = new IrisJigsawPiece().setObject(object).setRotatable(false);
        piece.getConnectors().add(new IrisJigsawConnector()
                .setPosition(new IrisPosition())
                .setDirection(IrisDirection.EAST_POSITIVE_X)
                .setPool("terminal")
                .setName("start")
                .setTargetName("door")
                .setJoint(JigsawJoint.ROLLABLE));
        return piece;
    }

    private static IrisJigsawPool pool(String... pieces) {
        IrisJigsawPool pool = new IrisJigsawPool();
        for (String piece : pieces) {
            pool.getPieces().add(new IrisJigsawPieceEntry().setPiece(piece).setWeight(1));
        }
        return pool;
    }

    private static IrisStructure structure(String startPool) {
        return new IrisStructure().setStartPool(startPool).setMaxDepth(1).setMaxSizeChunks(8);
    }
}
