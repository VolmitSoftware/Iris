package art.arcane.iris.generation.decoration.formation;

import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FormationShapeBuilderTest {
    @Test
    public void magicalFormsAreDeterministicAndGrounded() {
        assertDeterministicAndGrounded(this::iceberg);
        assertDeterministicAndGrounded(this::fissure);
        assertDeterministicAndGrounded(this::spiral);
        assertDeterministicAndGrounded(this::overhang);
    }

    @Test
    public void icebergBuildsWideBodyWithMultipleSummits() {
        FormationCanvas canvas = iceberg(41L);

        assertTrue(maxY(canvas) >= 12);
        assertTrue(horizontalDiameter(canvas) >= 12);
        assertTrue(localSummits(canvas) >= 2);
    }

    @Test
    public void fissureKeepsShardsSeparatedByOpenCracks() {
        FormationCanvas canvas = fissure(73L);

        assertTrue(connectedComponents(canvas) >= 3);
        assertTrue(maxY(canvas) >= 10);
    }

    @Test
    public void spiralLeavesAnOpenCenterAndCirclesMultipleQuadrants() {
        FormationCanvas canvas = spiral(101L);
        int middleY = 9;

        assertFalse(canvas.has(0, middleY, 0));
        assertTrue(hasQuadrant(canvas, true, true));
        assertTrue(hasQuadrant(canvas, true, false));
        assertTrue(hasQuadrant(canvas, false, true));
        assertTrue(hasQuadrant(canvas, false, false));
    }

    @Test
    public void overhangCantileversBeyondItsGroundedFoot() {
        FormationCanvas canvas = overhang(131L);
        double footReach = maxHorizontalReach(canvas, 0, 2);
        double upperReach = maxHorizontalReach(canvas, 9, Integer.MAX_VALUE);

        assertTrue(upperReach >= footReach + 5.0);
    }

    @Test
    public void thinLeaningSpireRetainsAConnectedPointedTip() {
        IrisFormation formation = baseFormation();
        formation.setRoughness(0.12);
        formation.setJitter(0.02);
        formation.setTopWidth(0);
        formation.setLean(10);
        formation.setLeanAzimuth(18);
        FormationCanvas canvas = new FormationCanvas();
        int height = 48;
        FormationShapeBuilder.spire(canvas, formation, height, 1.0, new RNG(137L));
        double shear = Math.tan(Math.toRadians(formation.getLean())) * (height - 1);
        int tipX = (int) Math.round(Math.cos(Math.toRadians(formation.getLeanAzimuth())) * shear);
        int tipZ = (int) Math.round(Math.sin(Math.toRadians(formation.getLeanAzimuth())) * shear);

        assertEquals(FormationCanvas.Role.CAP, canvas.getCells().get(new Vector3i(tipX, height - 1, tipZ)));
        assertEquals(height - 1, maxY(canvas));
        assertEquals(1, connectedComponents(canvas));
        assertTrue(maxLayerDiameter(canvas) <= 3);
    }

    @Test
    public void archRetainsGroundedLegsAndAnOpenCenter() {
        IrisFormation formation = baseFormation();
        formation.setArchSpan(10);
        formation.setArchThickness(2);
        formation.setArchAsymmetry(0.8);
        FormationCanvas canvas = arch(formation, 149L);
        FormationCanvas repeated = arch(formation, 149L);
        FormationCanvas different = arch(formation, 151L);

        assertEquals(canvas.getCells(), repeated.getCells());
        assertFalse(canvas.getCells().equals(different.getCells()));
        assertEquals(1, connectedComponents(canvas));
        assertTrue(layerComponents(canvas, 0) >= 2);
        assertTrue(openCenterColumn(canvas, 3, 6));
        assertTrue(axisDiameter(canvas, false) >= 4);
        assertTrue(maxY(canvas) >= 16);
        assertTrue(maxY(canvas) <= 18);
        assertTrue(unmirroredCells(canvas) >= 20);
    }

    @Test
    public void zeroAsymmetryKeepsArchMirroredAndThicknessScales() {
        IrisFormation formation = baseFormation();
        formation.setRoughness(0.0);
        formation.setJitter(0.0);
        formation.setArchSpan(10);
        formation.setArchAsymmetry(0.0);

        formation.setArchThickness(1);
        FormationCanvas thin = arch(formation, 173L);
        formation.setArchThickness(2);
        FormationCanvas medium = arch(formation, 173L);
        formation.setArchThickness(3);
        FormationCanvas thick = arch(formation, 173L);

        assertEquals(0, unmirroredCells(medium));
        assertTrue(thin.getCells().size() < medium.getCells().size());
        assertTrue(medium.getCells().size() < thick.getCells().size());
    }

    private void assertDeterministicAndGrounded(CanvasFactory factory) {
        FormationCanvas first = factory.create(31L);
        FormationCanvas second = factory.create(31L);

        assertFalse(first.isEmpty());
        assertEquals(first.getCells(), second.getCells());
        assertTrue(hasLayer(first, 0));
    }

    private FormationCanvas iceberg(long seed) {
        IrisFormation formation = baseFormation();
        formation.setIcebergPeaks(4);
        FormationCanvas canvas = new FormationCanvas();
        FormationShapeBuilder.iceberg(canvas, formation, 20, 5.0, new RNG(seed));
        return canvas;
    }

    private FormationCanvas fissure(long seed) {
        IrisFormation formation = baseFormation();
        formation.setFractureCount(3);
        formation.setFractureSeparation(4);
        FormationCanvas canvas = new FormationCanvas();
        FormationShapeBuilder.fissure(canvas, formation, 18, 4.0, new RNG(seed));
        return canvas;
    }

    private FormationCanvas spiral(long seed) {
        IrisFormation formation = baseFormation();
        formation.setSpiralTurns(1.75);
        formation.setSpiralRadius(6);
        formation.setSpiralThickness(1);
        FormationCanvas canvas = new FormationCanvas();
        FormationShapeBuilder.spiral(canvas, formation, 20, 4.0, new RNG(seed));
        return canvas;
    }

    private FormationCanvas overhang(long seed) {
        IrisFormation formation = baseFormation();
        formation.setOverhangReach(14);
        formation.setOverhangDrop(4);
        FormationCanvas canvas = new FormationCanvas();
        FormationShapeBuilder.overhang(canvas, formation, 20, 3.0, new RNG(seed));
        return canvas;
    }

    private FormationCanvas arch(IrisFormation formation, long seed) {
        FormationCanvas canvas = new FormationCanvas();
        FormationShapeBuilder.arch(canvas, formation, 18, 4.0, new RNG(seed));
        return canvas;
    }

    private IrisFormation baseFormation() {
        IrisFormation formation = new IrisFormation();
        formation.setRoughness(0.2);
        formation.setJitter(0.05);
        return formation;
    }

    private boolean hasLayer(FormationCanvas canvas, int y) {
        for (Vector3i position : canvas.getCells().keySet()) {
            if (position.getBlockY() == y) {
                return true;
            }
        }
        return false;
    }

    private int maxY(FormationCanvas canvas) {
        int max = Integer.MIN_VALUE;
        for (Vector3i position : canvas.getCells().keySet()) {
            max = Math.max(max, position.getBlockY());
        }
        return max;
    }

    private int horizontalDiameter(FormationCanvas canvas) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Vector3i position : canvas.getCells().keySet()) {
            minX = Math.min(minX, position.getBlockX());
            maxX = Math.max(maxX, position.getBlockX());
            minZ = Math.min(minZ, position.getBlockZ());
            maxZ = Math.max(maxZ, position.getBlockZ());
        }
        return Math.max(maxX - minX + 1, maxZ - minZ + 1);
    }

    private int axisDiameter(FormationCanvas canvas, boolean xAxis) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Vector3i position : canvas.getCells().keySet()) {
            int coordinate = xAxis ? position.getBlockX() : position.getBlockZ();
            min = Math.min(min, coordinate);
            max = Math.max(max, coordinate);
        }
        return max - min + 1;
    }

    private int maxLayerDiameter(FormationCanvas canvas) {
        int maximum = 0;
        for (int y = 0; y <= maxY(canvas); y++) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (Vector3i position : canvas.getCells().keySet()) {
                if (position.getBlockY() != y) {
                    continue;
                }
                minX = Math.min(minX, position.getBlockX());
                maxX = Math.max(maxX, position.getBlockX());
                minZ = Math.min(minZ, position.getBlockZ());
                maxZ = Math.max(maxZ, position.getBlockZ());
            }
            if (minX != Integer.MAX_VALUE) {
                maximum = Math.max(maximum, Math.max(maxX - minX + 1, maxZ - minZ + 1));
            }
        }
        return maximum;
    }

    private boolean openCenterColumn(FormationCanvas canvas, int halfWidth, int maxY) {
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = 0; y <= maxY; y++) {
                if (canvas.has(x, y, 0)) {
                    return false;
                }
            }
        }
        return true;
    }

    private int unmirroredCells(FormationCanvas canvas) {
        int unmatched = 0;
        for (Vector3i position : canvas.getCells().keySet()) {
            if (!canvas.has(-position.getBlockX(), position.getBlockY(), position.getBlockZ())) {
                unmatched++;
            }
        }
        return unmatched;
    }

    private int localSummits(FormationCanvas canvas) {
        Set<String> summits = new HashSet<>();
        for (Vector3i position : canvas.getCells().keySet()) {
            if (!canvas.has(position.getBlockX(), position.getBlockY() + 1, position.getBlockZ())) {
                int bucketX = Math.floorDiv(position.getBlockX(), 3);
                int bucketZ = Math.floorDiv(position.getBlockZ(), 3);
                if (position.getBlockY() >= 8) {
                    summits.add(bucketX + ":" + bucketZ);
                }
            }
        }
        return summits.size();
    }

    private int connectedComponents(FormationCanvas canvas) {
        Set<Vector3i> remaining = new HashSet<>(canvas.getCells().keySet());
        int components = 0;
        while (!remaining.isEmpty()) {
            Vector3i start = remaining.iterator().next();
            remaining.remove(start);
            ArrayDeque<Vector3i> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                Vector3i current = queue.removeFirst();
                for (Vector3i neighbor : neighbors(current)) {
                    if (remaining.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            components++;
        }
        return components;
    }

    private int layerComponents(FormationCanvas canvas, int y) {
        FormationCanvas layer = new FormationCanvas();
        for (Map.Entry<Vector3i, FormationCanvas.Role> entry : canvas.getCells().entrySet()) {
            Vector3i position = entry.getKey();
            if (position.getBlockY() == y) {
                layer.setBody(position.getBlockX(), position.getBlockY(), position.getBlockZ());
            }
        }
        return connectedComponents(layer);
    }

    private Set<Vector3i> neighbors(Vector3i position) {
        Set<Vector3i> neighbors = new HashSet<>();
        neighbors.add(new Vector3i(position.getBlockX() + 1, position.getBlockY(), position.getBlockZ()));
        neighbors.add(new Vector3i(position.getBlockX() - 1, position.getBlockY(), position.getBlockZ()));
        neighbors.add(new Vector3i(position.getBlockX(), position.getBlockY() + 1, position.getBlockZ()));
        neighbors.add(new Vector3i(position.getBlockX(), position.getBlockY() - 1, position.getBlockZ()));
        neighbors.add(new Vector3i(position.getBlockX(), position.getBlockY(), position.getBlockZ() + 1));
        neighbors.add(new Vector3i(position.getBlockX(), position.getBlockY(), position.getBlockZ() - 1));
        return neighbors;
    }

    private boolean hasQuadrant(FormationCanvas canvas, boolean positiveX, boolean positiveZ) {
        for (Vector3i position : canvas.getCells().keySet()) {
            boolean matchesX = positiveX ? position.getBlockX() >= 3 : position.getBlockX() <= -3;
            boolean matchesZ = positiveZ ? position.getBlockZ() >= 3 : position.getBlockZ() <= -3;
            if (matchesX && matchesZ) {
                return true;
            }
        }
        return false;
    }

    private double maxHorizontalReach(FormationCanvas canvas, int minY, int maxY) {
        double max = 0.0;
        for (Vector3i position : canvas.getCells().keySet()) {
            if (position.getBlockY() < minY || position.getBlockY() > maxY) {
                continue;
            }
            max = Math.max(max, Math.hypot(position.getBlockX(), position.getBlockZ()));
        }
        return max;
    }

    private interface CanvasFactory {
        FormationCanvas create(long seed);
    }
}
