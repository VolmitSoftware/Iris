package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CellularLibraryTest {
    private static final String NAMESPACE = "iris_export";
    private static final String INSTANCE = "test/cells";
    private static final double CELL = 64.0D;
    private static final int LATTICE_LIMIT = 798_000;

    @Test
    public void neighbourCellsShareHash() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        CellularOutputs outputs = CellularLibrary.build(INSTANCE, CELL, null, null, CellularMode.GRID_3X3, registry);
        Map<String, Field> bodies = FieldWalk.sharedBodies(roots(outputs));
        Field cellX = bodies.get("cell/" + INSTANCE + "/ik");
        Field cellZ = bodies.get("cell/" + INSTANCE + "/jk");
        List<Field.Noise> hashes = hashSamples(outputs, registry);
        assertEquals(9, hashes.size());
        for (Field.Noise hash : hashes) {
            assertEquals(0.0D, hash.xzScale(), 0.0D);
            assertEquals(0.0D, hash.yScale(), 0.0D);
            assertTrue(hash.shiftY() instanceof Field.Const);
            assertLatticeShift(hash.shiftX(), cellX);
            assertLatticeShift(hash.shiftZ(), cellZ);
        }
        float spacing = (float) CellHashNoise.LATTICE_SPACING;
        assertEquals(CellHashNoise.LATTICE_SPACING, spacing, 0.0D);
        assertEquals(0.0D, CellHashNoise.LATTICE_SPACING * 8.0D % 1.0D, 0.0D);
        for (int cell = -LATTICE_LIMIT; cell <= LATTICE_LIMIT; cell += 997) {
            assertNeighbourHashInputs(cell, spacing);
        }
        for (int cell = -4096; cell <= 4096; cell++) {
            assertNeighbourHashInputs(cell, spacing);
        }
    }

    @Test
    public void quadrantNeighbourShiftsAreDyadic() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        CellularOutputs outputs = CellularLibrary.build(INSTANCE, CELL, null, null, CellularMode.QUADRANT_2X2, registry);
        Map<String, Field> bodies = FieldWalk.sharedBodies(roots(outputs));
        Field.Binary shift = (Field.Binary) bodies.get("cell/" + INSTANCE + "/quadrant/ik_s");
        assertEquals(Field.BinaryOp.ADD, shift.op());
        Field.Binary step = (Field.Binary) shift.right();
        assertEquals(Field.BinaryOp.MUL, step.op());
        assertEquals(new Field.Const(CellHashNoise.LATTICE_SPACING), step.right());
        assertEquals(4, hashSamples(outputs, registry).size());
    }

    @Test
    public void everyRootIsTwoDimensionalExceptVolume() {
        for (CellularMode mode : CellularMode.values()) {
            NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
            CellularOutputs outputs = CellularLibrary.build(INSTANCE, CELL, null, null, mode, registry);
            for (Field root : roots(outputs)) {
                if (mode == CellularMode.GRID_3X3X3) {
                    assertTrue(mode + " output should depend on Y", root.axes().contains(Axis.Y));
                } else {
                    FieldValidator.requireTwoDimensional(root, mode.name());
                    assertTrue(root.axes().contains(Axis.X));
                    assertTrue(root.axes().contains(Axis.Z));
                }
            }
        }
    }

    @Test
    public void volumeOutputOnlyInsideGrid() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        CellularOutputs outputs = CellularLibrary.build(INSTANCE, CELL, null, null, CellularMode.GRID_3X3X3, registry);
        for (Field root : roots(outputs)) {
            Field.Grid grid = (Field.Grid) root;
            assertEquals(CellularLibrary.VOLUME_CELL_XZ, grid.cellXZ());
            assertEquals(CellularLibrary.VOLUME_CELL_Y, grid.cellY());
        }
        assertEquals(27, hashSamples(outputs, registry).size());
        for (Field.Noise hash : hashSamples(outputs, registry)) {
            assertTrue(hash.axes().contains(Axis.Y));
        }
        for (CellularMode mode : List.of(CellularMode.GRID_3X3, CellularMode.QUADRANT_2X2)) {
            CellularOutputs planar = CellularLibrary.build(INSTANCE, CELL, null, null, mode, registry);
            for (Field root : roots(planar)) {
                assertTrue(FieldWalk.nodesOf(root, Field.Grid.class).isEmpty());
            }
        }
    }

    @Test
    public void tournamentPlaysOneMatchPerEliminatedCandidate() {
        assertEquals(8, matches(CellularMode.GRID_3X3));
        assertEquals(3, matches(CellularMode.QUADRANT_2X2));
        assertEquals(26, matches(CellularMode.GRID_3X3X3));
    }

    @Test
    public void constantsStayWithinLimit() {
        for (CellularMode mode : CellularMode.values()) {
            for (double cell : new double[]{0.5D, 64.0D, 137.0D, 4096.0D}) {
                NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
                CellularOutputs outputs = CellularLibrary.build(INSTANCE, cell, null, null, mode, registry);
                for (Field root : roots(outputs)) {
                    FieldWalk.assertConstantsWithinLimit(root);
                }
            }
        }
    }

    @Test
    public void everyNoiseIsRegistered() {
        for (CellularMode mode : CellularMode.values()) {
            NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
            CellularOutputs outputs = CellularLibrary.build(INSTANCE, CELL, null, null, mode, registry);
            assertEquals(1, registry.definitions().size());
            assertEquals(CellHashNoise.NOISE_ID, registry.definitions().getFirst().id());
            for (Field root : roots(outputs)) {
                FieldWalk.assertNoisesRegistered(root, registry);
            }
        }
    }

    @Test
    public void hashSamplesAndOutputsAreShared() {
        for (CellularMode mode : CellularMode.values()) {
            NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
            CellularOutputs outputs = CellularLibrary.build(INSTANCE, CELL, null, null, mode, registry);
            Set<Field> sharedBodies = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Field root : roots(outputs)) {
                Field output = root instanceof Field.Grid grid ? grid.expr() : root;
                assertTrue(mode + " output is not shared", output instanceof Field.Shared);
                for (Field.Shared shared : FieldWalk.nodesOf(root, Field.Shared.class)) {
                    sharedBodies.add(shared.expr());
                }
            }
            for (Field.Noise hash : hashSamples(outputs, registry)) {
                assertTrue(sharedBodies.contains(hash));
            }
        }
    }

    @Test
    public void identifiersAreDeterministic() {
        for (CellularMode mode : CellularMode.values()) {
            CellularOutputs first = CellularLibrary.build(INSTANCE, CELL, null, null, mode, new NoiseRegistry(NAMESPACE));
            CellularOutputs second = CellularLibrary.build(INSTANCE, CELL, null, null, mode, new NoiseRegistry(NAMESPACE));
            assertEquals(first, second);
            assertEquals(FieldWalk.sharedBodies(roots(first)).keySet(), FieldWalk.sharedBodies(roots(second)).keySet());
            CellularOutputs other = CellularLibrary.build("test/other", CELL, null, null, mode, new NoiseRegistry(NAMESPACE));
            Set<String> overlap = new HashSet<>(FieldWalk.sharedBodies(roots(first)).keySet());
            overlap.retainAll(FieldWalk.sharedBodies(roots(other)).keySet());
            for (String name : overlap) {
                assertTrue("Instance names overlap on " + name, name.startsWith("cell/coord/"));
            }
        }
        assertEquals(CellHashNoise.plane(INSTANCE), CellHashNoise.plane(INSTANCE), 0.0D);
        assertNotEquals(CellHashNoise.plane(INSTANCE), CellHashNoise.plane("test/other"), 0.0D);
    }

    @Test
    public void modesOfOneInstanceAgreeOnSharedBodies() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        List<Field> roots = new ArrayList<>();
        for (CellularMode mode : CellularMode.values()) {
            roots.addAll(roots(CellularLibrary.build(INSTANCE, CELL, null, null, mode, registry)));
        }
        assertFalse(FieldWalk.sharedBodies(roots).isEmpty());
    }

    @Test
    public void cellSizeAndWarpScaleTheCellCoordinate() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field warp = new Field.Noise(registry.register(new NoiseDef("test/warp", -5, List.of(1.0D))), 1.0D, 0.0D, null, null, null);
        CellularOutputs outputs = CellularLibrary.build(INSTANCE, 128.0D, warp, null, CellularMode.GRID_3X3, registry);
        Map<String, Field> bodies = FieldWalk.sharedBodies(roots(outputs));
        Field.Binary xs = (Field.Binary) bodies.get("cell/" + INSTANCE + "/xs");
        assertEquals(Field.BinaryOp.ADD, xs.op());
        Field.Binary scaled = (Field.Binary) xs.left();
        assertEquals(new Field.Const(0.5D), scaled.right());
        Field.Binary warped = (Field.Binary) xs.right();
        assertEquals(warp, warped.left());
        assertEquals(new Field.Const(1.0D / 128.0D), warped.right());
        Field.Binary zs = (Field.Binary) bodies.get("cell/" + INSTANCE + "/zs");
        assertEquals(Field.BinaryOp.MUL, zs.op());
    }

    @Test
    public void invalidInputsAreRejected() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        assertThrows(IllegalArgumentException.class, () -> CellularLibrary.build("Bad Id", CELL, null, null, CellularMode.GRID_3X3, registry));
        assertThrows(IllegalArgumentException.class, () -> CellularLibrary.build(INSTANCE, 0.0D, null, null, CellularMode.GRID_3X3, registry));
        assertThrows(IllegalArgumentException.class, () -> CellularLibrary.build(INSTANCE, Double.NaN, null, null, CellularMode.GRID_3X3, registry));
    }

    private static void assertNeighbourHashInputs(int cell, float spacing) {
        float base = (float) cell * spacing;
        assertEquals(Float.floatToIntBits((float) (cell + 1) * spacing), Float.floatToIntBits(base + spacing));
        assertEquals(Float.floatToIntBits((float) (cell - 1) * spacing), Float.floatToIntBits(base + -spacing));
    }

    private static void assertLatticeShift(Field shift, Field lattice) {
        Field.Shared shared = (Field.Shared) shift;
        if (shared.expr().equals(lattice)) {
            return;
        }
        Field.Binary neighbour = (Field.Binary) shared.expr();
        assertEquals(Field.BinaryOp.ADD, neighbour.op());
        assertEquals(lattice, ((Field.Shared) neighbour.left()).expr());
        double offset = ((Field.Const) neighbour.right()).value();
        assertEquals(CellHashNoise.LATTICE_SPACING, Math.abs(offset), 0.0D);
    }

    private static int matches(CellularMode mode) {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        CellularOutputs outputs = CellularLibrary.build(INSTANCE, CELL, null, null, mode, registry);
        int count = 0;
        for (String name : FieldWalk.sharedBodies(roots(outputs)).keySet()) {
            if (name.endsWith("_diff")) {
                count++;
            }
        }
        return count;
    }

    private static List<Field.Noise> hashSamples(CellularOutputs outputs, NoiseRegistry registry) {
        String hashId = registry.namespace() + ":" + CellHashNoise.NOISE_ID;
        List<Field.Noise> hashes = new ArrayList<>();
        Set<Field> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Field root : roots(outputs)) {
            for (Field.Noise noise : FieldWalk.nodesOf(root, Field.Noise.class)) {
                if (noise.noiseId().equals(hashId) && seen.add(noise)) {
                    hashes.add(noise);
                }
            }
        }
        return hashes;
    }

    static List<Field> roots(CellularOutputs outputs) {
        return List.of(outputs.cellValue(), outputs.f1(), outputs.f2(), outputs.winnerX(), outputs.winnerZ());
    }
}
