package com.volmit;

import com.volmit.block.BlockState;
import com.volmit.host.OptionsMetadata;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.LocateResult;
import com.volmit.service.StructureLocator;
import com.volmit.service.VanillaFeatures;
import com.volmit.service.VanillaStructures;
import com.volmit.vanilla.DecorationStep;
import com.volmit.vanilla.JigsawOverrides;
import com.volmit.vanilla.StartMemo;
import com.volmit.vanilla.StartReach;
import com.volmit.vanilla.TemplateCells;
import com.volmit.vanilla.VanillaStructureContext;
import com.volmit.vanilla.VanillaStructureRule;
import com.volmit.vanilla.VanillaStructureSetup;
import com.volmit.vanilla.VanillaStructureStart;
import com.volmit.world.PackWorld;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** The values a vanilla structures provider answers with and how the host combines several modules' answers. */
class VanillaStructureValuesTest {
    private static final ResourceId VILLAGES = ResourceId.parse("minecraft:villages");
    private static final ResourceId CITY = ResourceId.parse("minecraft:ancient_city");
    private static final ResourceId CASTLE = ResourceId.parse("test:castle");

    @Test
    void jigsawOverridesKeepThePacksJsonNamesAndDefaults() {
        assertEquals(List.of("startPool", "startJigsawName", "maxDepth", "maxDistanceHorizontal", "maxDistanceVertical",
                "useExpansionHack", "projectStartToHeightmap", "dimensionPaddingBottom", "dimensionPaddingTop",
                "liquidSettings"), names(JigsawOverrides.class));
        assertEquals(OptionalInt.empty(), JigsawOverrides.none().maxDepth());
        assertEquals(Optional.empty(), JigsawOverrides.none().projectStartToHeightmap());
        assertEquals(Optional.empty(), JigsawOverrides.none().liquidSettings());
        assertEquals(Optional.empty(), JigsawOverrides.none().startPool());
        assertEquals(Optional.empty(), JigsawOverrides.none().startJigsawName());
        assertFalse(JigsawOverrides.none().removesStartJigsaw());
        assertEquals(Optional.of(JigsawOverrides.Liquids.IGNORE_WATERLOGGING),
                JigsawOverrides.none().withLiquids(JigsawOverrides.Liquids.IGNORE_WATERLOGGING).liquidSettings());
        var pool = com.volmit.resource.ResourceId.parse("minecraft:village/plains/town_centers");
        var overrides = JigsawOverrides.none().withStartPool(pool).withMaxDepth(4)
                .withMaxDistance(80, 120).withHeightmap(JigsawOverrides.Heightmap.NONE);
        assertEquals(Optional.of(pool), overrides.startPool());
        assertTrue(JigsawOverrides.none().withoutStartJigsaw().removesStartJigsaw());
        assertNotEquals(JigsawOverrides.none(), JigsawOverrides.none().withoutStartJigsaw());
        assertEquals(OptionalInt.of(4), overrides.maxDepth());
        assertEquals(OptionalInt.of(120), overrides.maxDistanceVertical());
        assertEquals(Optional.empty(), overrides.useExpansionHack());
        assertNotEquals(JigsawOverrides.none(), overrides);
    }

    @Test
    void reachesAreThePlatformsOrANumberOfBlocks() {
        assertTrue(StartReach.PLATFORM.platform());
        assertEquals(StartReach.blocks(0), StartReach.blocks(0));
        assertFalse(StartReach.blocks(12).platform());
        assertEquals(12, StartReach.blocks(12).blocks());
        assertThrows(IllegalArgumentException.class, () -> StartReach.blocks(-1));
        assertThrows(IllegalArgumentException.class, () -> new StartReach(true, 3));
    }

    @Test
    void templateCellsKeepTheirCellsAndMarkRemovedBlocks() {
        var stone = new BlockState("minecraft:stone");
        var cells = TemplateCells.of(new int[]{1, 2}, new int[]{60, 61}, new int[]{-3, -4}, new BlockState[]{stone, null}, 7);

        assertTrue(cells.resolved());
        assertEquals(2, cells.size());
        assertEquals(7, cells.readBlocks());
        assertEquals(61, cells.y(1));
        assertSame(stone, cells.state(0));
        assertTrue(cells.removed(1));
        assertFalse(TemplateCells.UNRESOLVED.resolved());
        assertEquals(0, TemplateCells.UNRESOLVED.size());
        assertThrows(IllegalArgumentException.class,
                () -> TemplateCells.of(new int[]{1}, new int[0], new int[]{1}, new BlockState[]{stone}, 0));
    }

    @Test
    void onlyTheUndergroundStepsAreUnderground() {
        assertEquals(List.of(DecorationStep.UNDERGROUND_STRUCTURES, DecorationStep.STRONGHOLDS, DecorationStep.UNDERGROUND_DECORATION),
                java.util.Arrays.stream(DecorationStep.values()).filter(DecorationStep::isUnderground).toList());
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
    void startsSitOnTheSurfaceWithoutAMemoUnlessTheModuleSaysOtherwise() {
        var start = VanillaStructureStart.at(CITY, 12, -7, 40);

        assertFalse(start.isUnderground());
        assertFalse(start.replacesPlatformStart());
        assertEquals(Optional.empty(), start.memo());
        var memo = new StartMemo("{\"mode\":\"VACUUM\"}");
        assertEquals(Optional.of(memo), start.withMemo(memo).memo());
        assertNotEquals(start, start.withMemo(memo));
        assertEquals(Optional.empty(), start.jigsaw());
        var underground = start.underground().replacingPlatformStart()
                .withJigsaw(JigsawOverrides.none().withMaxDepth(3));
        assertTrue(underground.isUnderground());
        assertTrue(underground.replacesPlatformStart());
        assertEquals(Optional.of(JigsawOverrides.none().withMaxDepth(3)), underground.jigsaw());
        assertEquals(underground, start.replacingPlatformStart().underground()
                .withJigsaw(JigsawOverrides.none().withMaxDepth(3)));
    }

    @Test
    void aProviderThatOnlyRulesChangesNothingElse() {
        VanillaStructures provider = new VanillaStructures() {
            @Override
            public VanillaStructureRule rule(ResourceId structure, VanillaStructureContext context) {
                return VanillaStructureRule.BLOCK;
            }
        };

        assertEquals(VanillaStructureRule.BLOCK, provider.rule(CITY, null));
        assertEquals(List.of(), provider.starts(0, 0, null));
        assertEquals(0, provider.shift(null, null));
        assertEquals(StartReach.PLATFORM, provider.reach(CITY, true, Optional.empty(), null));
        assertEquals(VanillaStructureSetup.UNCHANGED, provider.setup(null));
        assertEquals(VanillaStructureRule.GENERATE, new VanillaStructures() { }.rule(CITY, null));
    }

    @Test
    public void aLocatorPlacesWhatItCanLocateUnlessItSaysMore() {
        StructureLocator locator = new StructureLocator() {
            @Override
            public Set<ResourceId> structures(PackResources pack) {
                return Set.of(CASTLE, ResourceId.parse("test:tower"));
            }

            @Override
            public Set<ResourceId> locatable(PackWorld world) {
                return Set.of(CASTLE);
            }

            @Override
            public LocateResult locate(PackWorld world, ResourceId structure, int fromX, int fromZ, int maxRadiusChunks) {
                return LocateResult.NOT_FOUND;
            }
        };

        assertEquals(Set.of(CASTLE), locator.placed(null));
        assertEquals(Set.of(), locator.startedStructures(null), "a locator that starts no platform structure finds none");
        assertEquals(Set.of(), locator.locatableStarts(null));
        assertEquals(LocateResult.NOT_FOUND, locator.locateStart(null, CITY, 0, 0, 8, (chunkX, chunkZ) -> true));
    }

    @Test
    void aFeatureProviderThatSaysNothingLeavesThePlatformsFeaturesOff() {
        VanillaFeatures silent = new VanillaFeatures() { };

        assertFalse(silent.generates(null));
        assertTrue(silent.generatesStep(null, Optional.of(DecorationStep.UNDERGROUND_ORES)));
        assertTrue(silent.generatesStep(null, Optional.empty()));
        assertTrue(silent.generatesFeature(null, ResourceId.parse("minecraft:ore_diamond")));
    }

    @Test
    void everyDecorationStepDescribesItselfForStudio() throws NoSuchFieldException {
        assertNotNull(DecorationStep.class.getAnnotation(com.volmit.config.Desc.class));
        for (DecorationStep step : DecorationStep.values()) {
            assertNotNull(DecorationStep.class.getField(step.name()).getAnnotation(com.volmit.config.Desc.class), step.name());
        }
    }

    private static List<String> names(Class<?> type) {
        return OptionsMetadata.inspect(type).options().stream().map(OptionsMetadata.Option::name).toList();
    }
}
