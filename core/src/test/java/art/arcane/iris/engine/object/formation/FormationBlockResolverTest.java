package art.arcane.iris.engine.object.formation;

import art.arcane.iris.engine.object.IrisBlockData;
import art.arcane.iris.engine.object.IrisFormation;
import art.arcane.iris.engine.object.IrisMaterialPalette;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FormationBlockResolverTest {
    @Test
    public void sharedResolverMatchesFreshPaletteSeedsAcrossBands() {
        IrisFormation formation = new IrisFormation().setSeed(43191L).setStrataThickness(3);
        IrisMaterialPalette body = palette("stone", "andesite");
        IrisMaterialPalette cap = palette("granite", "diorite");
        IrisMaterialPalette strata = palette("terracotta", "red_terracotta");
        formation.setBlockPalette(body);
        List<Vector3i> positions = new ArrayList<>();
        for (int x = -8; x <= 8; x++) {
            for (int y = -12; y <= 24; y++) {
                positions.add(new Vector3i(x, y, x - y));
            }
        }
        Set<String> materials = new HashSet<>();
        for (boolean withStrata : new boolean[]{false, true}) {
            formation.setStrataPalette(withStrata ? strata : null);
            for (boolean withCap : new boolean[]{false, true}) {
                formation.setCapPalette(withCap ? cap : null);
                FormationBlockResolver resolver = new FormationBlockResolver(formation, null);
                for (int pass = 0; pass < 2; pass++) {
                    for (Vector3i position : positions) {
                        FormationCanvas.Role role = position.getBlockX() % 2 == 0
                                ? FormationCanvas.Role.CAP : FormationCanvas.Role.BODY;
                        IrisMaterialPalette expectedPalette = withCap && role == FormationCanvas.Role.CAP
                                ? cap : withStrata ? strata : body;
                        int sampleY = expectedPalette == strata
                                ? Math.floorDiv(position.getBlockY(), formation.getStrataThickness()) : position.getBlockY();
                        long sampleSeed = formation.getSeed() + (expectedPalette == strata ? sampleY * 31L : 0L);
                        RNG fresh = new RNG(sampleSeed);
                        PlatformBlockState expected = expectedPalette.get(fresh,
                                position.getBlockX(), sampleY, position.getBlockZ(), null);
                        assertSame(expected, resolver.resolve(role, position));
                        assertEquals(new RNG(sampleSeed).nextLong(), fresh.nextLong());
                        materials.add(expected.key());
                    }
                    Collections.reverse(positions);
                }
            }
        }
        assertTrue(materials.size() == 6);
    }

    private static IrisMaterialPalette palette(String first, String second) {
        return new IrisMaterialPalette().setPalette(new KList<>(block(first), block(second)));
    }

    private static IrisBlockData block(String material) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn("minecraft:" + material);
        IrisBlockData block = mock(IrisBlockData.class);
        when(block.getWeight()).thenReturn(1);
        when(block.getBlockData(null)).thenReturn(state);
        return block;
    }
}
