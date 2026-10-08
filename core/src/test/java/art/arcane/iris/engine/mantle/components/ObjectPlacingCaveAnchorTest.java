package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObjectPlacingCaveAnchorTest {
    @Test
    public void biomeOwnedPlacementsRejectForeignCaveBands() {
        IrisBiome frozen = biome("carving/ice");
        IrisBiome deepDark = biome("carving/standard-deepdark");
        IrisBiome surface = biome("frozen/ice-spikes");

        assertFalse(ObjectPlacing.caveAnchorBiomeConflicts(frozen, surface, "carving/ice"));
        assertTrue(ObjectPlacing.caveAnchorBiomeConflicts(deepDark, surface, "carving/ice"));
        assertFalse(ObjectPlacing.caveAnchorBiomeConflicts(deepDark, surface, null));
    }

    @Test
    public void dryPlacementsRejectFluidAndDefaultLavaCells() {
        MatterCavern air = new MatterCavern(true, "", (byte) 0);
        MatterCavern water = new MatterCavern(true, "", (byte) 1);
        MatterCavern lava = new MatterCavern(true, "", (byte) 2);

        assertFalse(ObjectPlacing.acceptsCaveAnchorFluid(false, water, 20, 8));
        assertFalse(ObjectPlacing.acceptsCaveAnchorFluid(false, lava, 20, 8));
        assertFalse(ObjectPlacing.acceptsCaveAnchorFluid(false, air, 8, 8));
        assertTrue(ObjectPlacing.acceptsCaveAnchorFluid(false, air, 9, 8));
    }

    @Test
    public void explicitWetAndForcedAirPlacementsRemainEligible() {
        MatterCavern water = new MatterCavern(true, "", (byte) 1);
        MatterCavern forcedAir = new MatterCavern(true, "", (byte) 3);

        assertTrue(ObjectPlacing.acceptsCaveAnchorFluid(true, water, 20, 8));
        assertTrue(ObjectPlacing.acceptsCaveAnchorFluid(false, forcedAir, 0, 8));
        assertFalse(ObjectPlacing.acceptsCaveAnchorFluid(
                true,
                new MatterCavern(false, "", (byte) 0),
                20,
                8
        ));
    }

    private static IrisBiome biome(String loadKey) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(loadKey);
        return biome;
    }
}
