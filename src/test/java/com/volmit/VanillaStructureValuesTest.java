package com.volmit;

import com.volmit.block.BlockState;
import com.volmit.config.OptionsMetadata;
import com.volmit.resource.ResourceId;
import com.volmit.service.VanillaStructures;
import com.volmit.vanilla.JigsawOverrides;
import com.volmit.vanilla.StartMemo;
import com.volmit.vanilla.StartReach;
import com.volmit.vanilla.StructureStep;
import com.volmit.vanilla.TemplateCells;
import com.volmit.vanilla.VanillaStructureContext;
import com.volmit.vanilla.VanillaStructureRule;
import com.volmit.vanilla.VanillaStructureSetup;
import com.volmit.vanilla.VanillaStructureStart;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;

/** The values a vanilla structures provider answers with and how the host combines several modules' answers. */
class VanillaStructureValuesTest {
    private static final ResourceId VILLAGES = ResourceId.parse("minecraft:villages");
    private static final ResourceId CITY = ResourceId.parse("minecraft:ancient_city");

    @Test
    void jigsawOverridesKeepThePacksJsonNamesAndDefaults() {
        assertEquals(List.of("startPool", "startJigsawName", "maxDepth", "maxDistanceHorizontal", "maxDistanceVertical",
                "useExpansionHack", "projectStartToHeightmap", "dimensionPaddingBottom", "dimensionPaddingTop",
                "liquidSettings"), names(JigsawOverrides.class));
        assertEquals(OptionalInt.empty(), JigsawOverrides.none().maxDepth());
        assertEquals(JigsawOverrides.Heightmap.SOURCE, JigsawOverrides.none().projectStartToHeightmap());
        var overrides = JigsawOverrides.none().withStartPool("minecraft:village/plains/town_centers").withMaxDepth(4)
                .withMaxDistance(80, 120).withHeightmap(JigsawOverrides.Heightmap.NONE);
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
        assertEquals(List.of(StructureStep.UNDERGROUND_STRUCTURES, StructureStep.STRONGHOLDS, StructureStep.UNDERGROUND_DECORATION),
                java.util.Arrays.stream(StructureStep.values()).filter(StructureStep::isUnderground).toList());
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
        var memo = new StartMemo("{\"mode\":\"VACUUM\"}", "null");
        assertEquals(Optional.of(memo), start.withMemo(memo).memo());
        assertNotEquals(start, start.withMemo(memo));
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

    private static List<String> names(Class<?> type) {
        return OptionsMetadata.inspect(type).options().stream().map(OptionsMetadata.Option::name).toList();
    }
}
