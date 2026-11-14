package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObjectSeparationTest {
    private static final long SEED = 1337L;
    private static final int SEPARATION_BLOCKS = 40;
    private static final int GRID_HALF_WIDTH_CHUNKS = 24;

    @Test
    public void keptObjectsStayApartAcrossBiomeBorders() {
        IrisObjectPlacement westBiome = separated(1, SEPARATION_BLOCKS, "tree");
        IrisObjectPlacement eastBiome = separated(1, SEPARATION_BLOCKS, "tree");
        ObjectSeparation separation = separation(
                (chunkX, chunkZ) -> List.of(chunkX < 0 ? westBiome : eastBiome));

        List<ObjectSeparation.Candidate> kept = keptOnGrid(separation, (chunkX, chunkZ) -> chunkX < 0 ? westBiome : eastBiome);

        assertTrue(kept.size() > 10);
        for (int first = 0; first < kept.size(); first++) {
            for (int second = first + 1; second < kept.size(); second++) {
                assertFalse(kept.get(first).isCloserThan(kept.get(second), SEPARATION_BLOCKS));
            }
        }
    }

    @Test
    public void keptObjectsDoNotDependOnQueryOrder() {
        IrisObjectPlacement placement = separated(1, SEPARATION_BLOCKS, "tree");
        ObjectSeparation separation = separation((chunkX, chunkZ) -> List.of(placement));

        List<ObjectSeparation.Candidate> forward = keptOnGrid(separation, (chunkX, chunkZ) -> placement);
        List<Long> reversedRanks = new ArrayList<>();
        for (int chunkX = GRID_HALF_WIDTH_CHUNKS; chunkX >= -GRID_HALF_WIDTH_CHUNKS; chunkX--) {
            for (int chunkZ = GRID_HALF_WIDTH_CHUNKS; chunkZ >= -GRID_HALF_WIDTH_CHUNKS; chunkZ--) {
                separation.keptCandidate(chunkX, chunkZ, placement).ifPresent(candidate -> reversedRanks.add(candidate.rank()));
            }
        }

        List<Long> forwardRanks = forward.stream().map(ObjectSeparation.Candidate::rank).sorted().toList();
        assertEquals(forwardRanks, reversedRanks.stream().sorted().toList());
    }

    @Test
    public void placementsWithOtherObjectsDoNotKeepEachOtherApart() {
        IrisObjectPlacement trees = separated(1, SEPARATION_BLOCKS, "tree");
        IrisObjectPlacement rocks = separated(1, SEPARATION_BLOCKS, "rock");
        ObjectSeparation treesOnly = separation((chunkX, chunkZ) -> List.of(trees));
        ObjectSeparation both = separation((chunkX, chunkZ) -> List.of(trees, rocks));

        assertEquals(
                keptOnGrid(treesOnly, (chunkX, chunkZ) -> trees).size(),
                keptOnGrid(both, (chunkX, chunkZ) -> trees).size());
    }

    @Test
    public void zeroChanceKeepsNothing() {
        IrisObjectPlacement placement = separated(0, SEPARATION_BLOCKS, "tree");
        ObjectSeparation separation = separation((chunkX, chunkZ) -> List.of(placement));

        assertTrue(keptOnGrid(separation, (chunkX, chunkZ) -> placement).isEmpty());
    }

    @Test
    public void wideSeparationThinsObjectsOut() {
        IrisObjectPlacement narrow = separated(1, 8, "tree");
        IrisObjectPlacement wide = separated(1, 96, "tree");

        int narrowCount = keptOnGrid(separation((chunkX, chunkZ) -> List.of(narrow)), (chunkX, chunkZ) -> narrow).size();
        int wideCount = keptOnGrid(separation((chunkX, chunkZ) -> List.of(wide)), (chunkX, chunkZ) -> wide).size();

        assertTrue(narrowCount > wideCount * 2);
    }

    @Test
    public void placementsStayUnchangedByDefault() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setChance(0.35);

        assertFalse(placement.isSeparated());
        assertEquals(0.35, separation((chunkX, chunkZ) -> List.of()).chanceAt(placement, 12, -40), 0D);
    }

    @Test
    public void chanceStyleScalesChanceWithoutRaisingIt() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setChance(0.5);
        placement.setChanceStyle(new IrisGeneratorStyle(NoiseStyle.STATIC));
        ObjectSeparation separation = separation((chunkX, chunkZ) -> List.of());

        Optional<Double> lowest = Optional.empty();
        Optional<Double> highest = Optional.empty();
        for (int x = 0; x < 200; x += 7) {
            double chance = separation.chanceAt(placement, x, x * 3);
            assertTrue(chance >= 0D && chance <= 0.5);
            lowest = Optional.of(Math.min(chance, lowest.orElse(chance)));
            highest = Optional.of(Math.max(chance, highest.orElse(chance)));
        }
        assertTrue(highest.get() - lowest.get() > 0.1);
    }

    private static List<ObjectSeparation.Candidate> keptOnGrid(ObjectSeparation separation, PlacementAt placementAt) {
        List<ObjectSeparation.Candidate> kept = new ArrayList<>();
        for (int chunkX = -GRID_HALF_WIDTH_CHUNKS; chunkX <= GRID_HALF_WIDTH_CHUNKS; chunkX++) {
            for (int chunkZ = -GRID_HALF_WIDTH_CHUNKS; chunkZ <= GRID_HALF_WIDTH_CHUNKS; chunkZ++) {
                separation.keptCandidate(chunkX, chunkZ, placementAt.at(chunkX, chunkZ)).ifPresent(kept::add);
            }
        }
        return kept;
    }

    private static ObjectSeparation separation(ObjectSeparation.SurfacePlacements surfacePlacements) {
        return new ObjectSeparation(SEED, null, surfacePlacements);
    }

    private static IrisObjectPlacement separated(double chance, int separationBlocks, String... objects) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(new KList<>(objects));
        placement.setChance(chance);
        placement.setSeparation(separationBlocks);
        return placement;
    }

    @FunctionalInterface
    private interface PlacementAt {
        IrisObjectPlacement at(int chunkX, int chunkZ);
    }
}
