package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRoom;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleObjectComponentCaveAnchorTest {
    @Test
    public void biomeOwnedPlacementsRejectForeignCaveBands() {
        IrisBiome frozen = biome("carving/ice");
        IrisBiome deepDark = biome("carving/standard-deepdark");
        IrisBiome surface = biome("frozen/ice-spikes");

        assertFalse(MantleObjectComponent.caveAnchorBiomeConflicts(frozen, surface, "carving/ice"));
        assertTrue(MantleObjectComponent.caveAnchorBiomeConflicts(deepDark, surface, "carving/ice"));
        assertFalse(MantleObjectComponent.caveAnchorBiomeConflicts(deepDark, surface, null));
    }

    @Test
    public void dryPlacementsRejectFluidAndDefaultLavaCells() {
        MatterCavern air = new MatterCavern(true, "", (byte) 0);
        MatterCavern water = new MatterCavern(true, "", (byte) 1);
        MatterCavern lava = new MatterCavern(true, "", (byte) 2);

        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(false, water, 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(false, lava, 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(false, air, 8, 8));
        assertTrue(MantleObjectComponent.acceptsCaveAnchorFluid(false, air, 9, 8));
    }

    @Test
    public void explicitWetAndForcedAirPlacementsRemainEligible() {
        MatterCavern water = new MatterCavern(true, "", (byte) 1);
        MatterCavern forcedAir = new MatterCavern(true, "", (byte) 3);
        MatterCavern lava = new MatterCavern(true, "", (byte) 2);

        assertTrue(MantleObjectComponent.acceptsCaveAnchorFluid(true, water, 20, 8));
        assertTrue(MantleObjectComponent.acceptsCaveAnchorFluid(true, lava, 20, 8));
        assertTrue(MantleObjectComponent.acceptsCaveAnchorFluid(false, forcedAir, 0, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                true,
                new MatterCavern(false, "", (byte) 0),
                20,
                8
        ));
    }

    @Test
    public void plannedHydrologyCannotBecomeAnObjectAnchor() {
        MatterCavern water = new MatterCavern(true, "", (byte) 1);
        MatterCavern forcedAir = new MatterCavern(true, "", (byte) 3);

        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                true, water, HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE), 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                true, water, HydrologyCaveCell.of(HydrologyCaveAction.FALLING_FLUID), 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                true, null, HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD), 20, 8));
        assertFalse(MantleObjectComponent.acceptsCaveAnchorFluid(
                false, forcedAir, HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR), 20, 8));
    }

    @Test
    public void authoredWetAnchorsAcceptWaterAndLavaWhileDryAnchorsRequireAir() throws Exception {
        for (SubterrainCell.Kind kind : new SubterrainCell.Kind[]{SubterrainCell.Kind.WATER, SubterrainCell.Kind.LAVA}) {
            SubterrainCell cell = featureCell(kind, false);
            assertTrue(acceptsAuthoredAnchor(cell, true));
            assertFalse(acceptsAuthoredAnchor(cell, false));
        }
        SubterrainCell air = featureCell(SubterrainCell.Kind.AIR, false);
        assertTrue(acceptsAuthoredAnchor(air, false));
        assertFalse(acceptsAuthoredAnchor(air, true));
    }

    @Test
    public void authoredWetAnchorsPreserveReservedPassageAndSolidGuards() throws Exception {
        for (SubterrainCell.Kind kind : new SubterrainCell.Kind[]{SubterrainCell.Kind.AIR, SubterrainCell.Kind.WATER, SubterrainCell.Kind.LAVA}) {
            SubterrainCell reserved = featureCell(kind, true);
            assertFalse(acceptsAuthoredAnchor(reserved, false));
            assertFalse(acceptsAuthoredAnchor(reserved, true));
        }
        SubterrainCell solid = featureCell(SubterrainCell.Kind.SOLID, false);
        assertFalse(acceptsAuthoredAnchor(solid, false));
        assertFalse(acceptsAuthoredAnchor(solid, true));
    }

    private static boolean acceptsAuthoredAnchor(SubterrainCell cell, boolean underwater) throws Exception {
        Engine engine = mock(Engine.class);
        EngineMantle mantle = mock(EngineMantle.class);
        when(mantle.getEngine()).thenReturn(engine);
        when(engine.getSubterrainCell(17, 4, -9)).thenReturn(cell);
        MantleObjectComponent component = new MantleObjectComponent(mantle);
        Method method = MantleObjectComponent.class.getDeclaredMethod("acceptsCaveAnchorAt",
                int.class, int.class, int.class, boolean.class, MatterCavern.class, HydrologyCaveCell.class);
        method.setAccessible(true);
        return (boolean) method.invoke(component, 17, 4, -9, underwater, null, null);
    }

    private static SubterrainCell featureCell(SubterrainCell.Kind kind, boolean reservedPassage) {
        SubterrainRoom room = new SubterrainRoom("basin:0:0", "subterrain/basin", IrisSubterrainFamily.CENOTE,
                17, 4, -9, 17, 4, -9, 0, 20, 8, 8,
                reservedPassage, kind == SubterrainCell.Kind.SOLID, kind);
        String material = switch (kind) {
            case AIR -> "minecraft:air";
            case WATER -> "minecraft:water";
            case LAVA -> "minecraft:lava";
            case SOLID -> "minecraft:stone";
            case OUTSIDE -> "";
        };
        return new SubterrainCell(kind, material, room);
    }

    private static IrisBiome biome(String loadKey) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(loadKey);
        return biome;
    }
}
