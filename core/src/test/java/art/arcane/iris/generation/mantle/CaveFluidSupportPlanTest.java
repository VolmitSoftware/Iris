package art.arcane.iris.generation.mantle;

import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaveFluidSupportPlanTest {
    private static final int WORLD_HEIGHT = 64;
    private static final byte LIQUID_AIR = 0;
    private static final byte LIQUID_FLUID = 1;
    private static final int ROOM_FLOOR_Y = 20;
    private static final int ROOM_CEILING_Y = 30;
    private static final int SUSPENDED_Y = 25;

    private final MatterCavern firstCaveFluid = new MatterCavern(true, "", LIQUID_FLUID);
    private final MatterCavern firstCaveAir = new MatterCavern(true, "", LIQUID_AIR);
    private final MatterCavern secondCaveFluid = new MatterCavern(true, "", LIQUID_FLUID);
    private final MatterCavern secondCaveAir = new MatterCavern(true, "", LIQUID_AIR);

    private Matter[] sections;
    private MantleChunk<Matter> chunk;
    private MantleCaveCellSink sink;

    @Before
    @SuppressWarnings("unchecked")
    public void createChunk() {
        IrisMatterSupport.ensureRegistered();
        sections = new Matter[WORLD_HEIGHT >> 4];
        chunk = mock(MantleChunk.class);
        when(chunk.sectionCount()).thenReturn(sections.length);
        when(chunk.get(anyInt())).thenAnswer(call -> sections[call.<Integer>getArgument(0)]);
        when(chunk.getOrCreate(anyInt())).thenAnswer(call -> {
            int index = call.getArgument(0);
            if (sections[index] == null) {
                sections[index] = new IrisMatter(16, 16, 16);
            }
            return sections[index];
        });
        sink = new MantleCaveCellSink(chunk, WORLD_HEIGHT);
    }

    @Test
    public void suspendedFluidOfEveryCaveDrainsWhenCavesWriteEqualFluid() {
        carveRoom(firstCaveAir);
        CaveFluidSupportPlan plan = new CaveFluidSupportPlan();
        writeFluid(plan, 5, SUSPENDED_Y, 5, firstCaveFluid, firstCaveAir);
        writeFluid(plan, 9, SUSPENDED_Y, 9, secondCaveFluid, secondCaveAir);

        plan.resolve(chunk);

        assertTrue("first cave's suspended fluid drains", cellAt(5, SUSPENDED_Y, 5).isAir());
        assertTrue("second cave's suspended fluid drains", cellAt(9, SUSPENDED_Y, 9).isAir());
    }

    @Test
    public void cuppedFluidOfALaterCaveIsKept() {
        CaveFluidSupportPlan plan = new CaveFluidSupportPlan();
        writeFluid(plan, 3, SUSPENDED_Y, 3, firstCaveFluid, firstCaveAir);
        writeFluid(plan, 9, SUSPENDED_Y, 9, secondCaveFluid, secondCaveAir);
        sink.set(9, SUSPENDED_Y + 1, 9, secondCaveAir);

        plan.resolve(chunk);

        assertTrue("fluid in a solid cup stays", cellAt(9, SUSPENDED_Y, 9).isWater());
    }

    private void carveRoom(MatterCavern air) {
        for (int localX = 1; localX <= 14; localX++) {
            for (int y = ROOM_FLOOR_Y; y <= ROOM_CEILING_Y; y++) {
                for (int localZ = 1; localZ <= 14; localZ++) {
                    sink.set(localX, y, localZ, air);
                }
            }
        }
    }

    private void writeFluid(CaveFluidSupportPlan plan, int localX, int y, int localZ, MatterCavern fluid, MatterCavern air) {
        sink.set(localX, y, localZ, fluid);
        plan.add(localX, y, localZ, fluid, air);
    }

    private MatterCavern cellAt(int localX, int y, int localZ) {
        return sections[y >> 4].<MatterCavern>slice(MatterCavern.class).get(localX, y & 15, localZ);
    }
}
