package com.volmit;

import com.volmit.block.BlockSpec;
import com.volmit.block.PaletteSpec;
import com.volmit.config.OptionsMetadata;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.JigsawOverrides;
import com.volmit.vanilla.StructureStilts;
import com.volmit.vanilla.StructureTerrain;
import com.volmit.vanilla.VanillaStructureAdjustment;
import com.volmit.vanilla.VanillaStructureFit;
import com.volmit.vanilla.VanillaStructureHook;
import com.volmit.vanilla.VanillaStructureRule;
import com.volmit.vanilla.VanillaStructureSetup;
import com.volmit.vanilla.VanillaStructureStart;
import com.volmit.vanilla.YBand;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;

/** The values a vanilla structure hook answers with and how the host combines several modules' answers. */
class VanillaStructureValuesTest {
    private static final ResourceId VILLAGES = ResourceId.parse("minecraft:villages");
    private static final ResourceId CITY = ResourceId.parse("minecraft:ancient_city");

    @Test
    void fitValuesKeepThePacksJsonNamesAndDefaults() {
        assertEquals(List.of("mode", "horizontalPadding", "ceilingPadding", "floorPadding", "shape", "encasePalette",
                "erosionStrength", "erosionFrequency", "lobeFrequency", "lobeStrength"), names(StructureTerrain.class));
        assertEquals(List.of("maxDepth", "palette", "spacing", "supportNonOccluding"), names(StructureStilts.class));
        assertEquals(List.of("min", "max"), names(YBand.class));
        assertEquals(List.of("startPool", "startJigsawName", "maxDepth", "maxDistanceHorizontal", "maxDistanceVertical",
                "useExpansionHack", "projectStartToHeightmap", "dimensionPaddingBottom", "dimensionPaddingTop",
                "liquidSettings"), names(JigsawOverrides.class));

        var terrain = StructureTerrain.of(StructureTerrain.Mode.FORCE_CARVE);
        assertEquals(StructureTerrain.Shape.BOX, terrain.shape());
        assertEquals(0.8, terrain.erosionStrength());
        assertEquals(0.07, terrain.erosionFrequency());
        assertEquals(Optional.empty(), terrain.encasePalette());
        var stilts = StructureStilts.of(PaletteSpec.of(List.of(BlockSpec.of("minecraft:cobblestone"))));
        assertEquals(64, stilts.maxDepth());
        assertEquals(1, stilts.spacing());
        assertEquals(OptionalInt.empty(), JigsawOverrides.none().maxDepth());
        assertEquals(JigsawOverrides.Heightmap.SOURCE, JigsawOverrides.none().projectStartToHeightmap());
    }

    @Test
    void fitValuesAreValuesChangedOnlyThroughWithers() {
        var carve = StructureTerrain.of(StructureTerrain.Mode.FORCE_CARVE).withPadding(4, 2, 1)
                .withShape(StructureTerrain.Shape.ERODED).withErosion(0.5, 0.1).withLobes(0.2, 0.4);

        assertEquals(carve, StructureTerrain.of(StructureTerrain.Mode.FORCE_CARVE).withShape(StructureTerrain.Shape.ERODED)
                .withLobes(0.2, 0.4).withErosion(0.5, 0.1).withPadding(4, 2, 1));
        assertEquals(4, carve.horizontalPadding());
        assertEquals(0.4, carve.lobeStrength());
        assertNotEquals(carve, carve.withPadding(4, 2, 0));
        assertEquals(YBand.of(-20, -120), YBand.of(-120, -20), "reversed bounds are the same band");
        assertEquals(-120, YBand.of(-20, -120).lowest());
        var overrides = JigsawOverrides.none().withStartPool("minecraft:village/plains/town_centers").withMaxDepth(4)
                .withMaxDistance(80, 120).withHeightmap(JigsawOverrides.Heightmap.NONE);
        assertEquals(OptionalInt.of(4), overrides.maxDepth());
        assertEquals(OptionalInt.of(120), overrides.maxDistanceVertical());
        assertEquals(Optional.empty(), overrides.useExpansionHack());
        assertNotEquals(JigsawOverrides.none(), overrides);
    }

    @Test
    void adjustmentsAddShiftsAndLetTheLaterBandAndFitPartsWin() {
        var stilts = StructureStilts.of(PaletteSpec.of(List.of(BlockSpec.of("stone"))));
        var carve = StructureTerrain.of(StructureTerrain.Mode.BORE);
        var first = VanillaStructureAdjustment.NONE.shifted(-32).withYBand(YBand.of(-60, -20))
                .withFit(VanillaStructureFit.SOURCE.withStilts(stilts));
        var second = VanillaStructureAdjustment.NONE.shifted(8).keepingSourceY()
                .withFit(VanillaStructureFit.SOURCE.withTerrain(carve));

        var combined = first.then(second);

        assertEquals(-24, combined.yShift());
        assertEquals(Optional.of(YBand.of(-60, -20)), combined.yBand(), "the later adjustment has no band");
        assertTrue(combined.keepsSourceY());
        assertEquals(VanillaStructureFit.SOURCE.withStilts(stilts).withTerrain(carve), combined.fit());
        assertEquals(Optional.of(YBand.of(0, 10)), combined.then(VanillaStructureAdjustment.NONE.withYBand(YBand.of(0, 10))).yBand());
        assertEquals(first, first.then(VanillaStructureAdjustment.NONE), "NONE changes nothing");
    }

    @Test
    void setupsMultiplyFrequenciesAndProtectTheDefinitionsWhenEitherDoes() {
        var doubled = VanillaStructureSetup.UNCHANGED.withFrequency(VILLAGES, 2);
        var protecting = VanillaStructureSetup.UNCHANGED.withFrequency(VILLAGES, 1.5).protectingVanillaDefinitions();

        assertEquals(3, doubled.and(protecting).frequency(VILLAGES));
        assertEquals(doubled.and(protecting), protecting.and(doubled));
        assertTrue(doubled.and(protecting).protectsVanillaDefinitions());
        assertEquals(1, doubled.frequency(CITY), "a set the setup leaves alone");
        assertEquals(4, doubled.withFrequency(VILLAGES, 4).frequency(VILLAGES), "the later multiplier of one setup wins");
        assertEquals(Map.of(VILLAGES, 2.0), doubled.frequencies());
    }

    @Test
    void startsSitOnTheSurfaceWithTheStructuresOwnFitUnlessTheModuleSaysOtherwise() {
        var start = VanillaStructureStart.at(CITY, 12, -7, 40);

        assertFalse(start.isUnderground());
        assertFalse(start.replacesPlatformStart());
        assertEquals(VanillaStructureFit.SOURCE, start.fit());
        assertEquals(Optional.empty(), start.jigsaw());
        var underground = start.underground().replacingPlatformStart().withIdentity(-5L)
                .withJigsaw(JigsawOverrides.none().withMaxDepth(3));
        assertTrue(underground.isUnderground());
        assertTrue(underground.replacesPlatformStart());
        assertEquals(-5L, underground.identity());
        assertEquals(Optional.of(JigsawOverrides.none().withMaxDepth(3)), underground.jigsaw());
        assertEquals(underground, start.withIdentity(-5L).replacingPlatformStart().underground()
                .withJigsaw(JigsawOverrides.none().withMaxDepth(3)));
    }

    @Test
    void aHookThatOnlyRulesChangesNothingElse() {
        VanillaStructureHook hook = (structure, context) -> VanillaStructureRule.BLOCK;

        assertEquals(VanillaStructureAdjustment.NONE, hook.adjustment(CITY, true, null));
        assertEquals(List.of(), hook.starts(0, 0, null));
        assertEquals(VanillaStructureSetup.UNCHANGED, hook.setup(null));
    }

    private static List<String> names(Class<?> type) {
        return OptionsMetadata.inspect(type).options().stream().map(OptionsMetadata.Option::name).toList();
    }
}
