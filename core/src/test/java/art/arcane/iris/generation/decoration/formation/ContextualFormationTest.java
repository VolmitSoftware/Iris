package art.arcane.iris.generation.decoration.formation;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRoom;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ContextualFormationTest {
    @Test
    public void roomVaultSizesFormationAfterAnchorWithoutUsingOrdinaryHeightRange() {
        IrisFormation formation = new IrisFormation().setName("vault-spire").setRoomHeightFraction(0.5)
                .setHeightMin(80).setHeightMax(80).setBaseWidthMin(2).setBaseWidthMax(2)
                .setRoughness(0).setLean(0);
        NativeBlockState rock = mock(NativeBlockState.class);
        when(rock.key()).thenReturn("minecraft:stone");
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getStateOrNull("minecraft:stone", false)).thenReturn(rock);
            IrisData data = mock(IrisData.class);
            IrisObject shortRoom = formation.getVariantObject(data, new RNG(143L), room(12));
            IrisObject tallRoom = formation.getVariantObject(data, new RNG(143L), room(40));
            IrisObject repeat = formation.getVariantObject(data, new RNG(143L), room(40));
            assertNotNull(shortRoom);
            assertNotNull(tallRoom);
            assertTrue(shortRoom.getH() <= 12);
            assertTrue(tallRoom.getH() <= 40);
            assertTrue(tallRoom.getH() > shortRoom.getH());
            assertNotNull(repeat);
            assertEquals(tallRoom.getW(), repeat.getW());
            assertEquals(tallRoom.getH(), repeat.getH());
            assertEquals(tallRoom.getD(), repeat.getD());
            assertEquals(tallRoom.getBlocks().size(), repeat.getBlocks().size());
            for (Map.Entry<IrisBlockVector, NativeBlockState> entry : tallRoom.getBlocks()) {
                NativeBlockState repeated = repeat.getBlocks().get(entry.getKey());
                assertNotNull(repeated);
                assertEquals(entry.getValue().key(), repeated.key());
            }
            assertEquals("procedural/vault-spire/cenote-room", tallRoom.getLoadKey());
        }
    }

    private SubterrainRoom room(int vaultHeight) {
        return new SubterrainRoom("cenote-room", "cave-biome", IrisSubterrainFamily.CENOTE,
                0, 20, 0, 0, 12, 0, 10, 11 + vaultHeight, 20, 10,
                false, false, SubterrainCell.Kind.AIR);
    }
}
