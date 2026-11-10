package art.arcane.iris.engine.framework;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.builtin.structure.StructureStilts;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.engine.framework.structure.NativeStartPlanner;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import com.volmit.host.LoadedPack;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.JigsawOverrides;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.nativeSource;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.declaring;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.withAdded;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The starts the pack's nativeStructures placements make, now through iris:structure's vanilla structure hook, are the
 * ones the core planner made before the move: the same chunks, structures, base heights and placements for a
 * fixed seed (pinned from the planner), and fits whose persisted terrain and stilts JSON is unchanged.
 */
public class NativeStructureStartsTest {
    private static final long SEED = 1337L;
    private static final int MIN_HEIGHT = -64;
    private static final int GRID_RADIUS_CHUNKS = 6;
    private static final Gson GSON = new Gson();

    /**
     * chunkX chunkZ structure baseY placement, as NativeStructurePlacementPlanner.plansAt returned them. Batch 6 dropped
     * the trailing placement identity column (one value per placement: villages -2304053147360248982, cities
     * 7830921123394837354, outposts 3980124831785354812) when starts stopped carrying it; the other columns are unchanged.
     */
    private static final List<String> PLANNER_STARTS = List.of(
            "-6 -4 minecraft:village_plains 72 villages",
            "-6 0 minecraft:ancient_city -11 cities",
            "-6 1 minecraft:pillager_outpost 92 outposts",
            "-6 3 minecraft:village_plains 88 villages",
            "-6 3 minecraft:ancient_city -14 cities",
            "-5 -4 minecraft:pillager_outpost 64 outposts",
            "-5 -3 minecraft:ancient_city -40 cities",
            "-5 5 minecraft:pillager_outpost 76 outposts",
            "-4 -5 minecraft:pillager_outpost 88 outposts",
            "-4 -1 minecraft:village_plains 80 villages",
            "-4 -1 minecraft:pillager_outpost 80 outposts",
            "-4 1 minecraft:village_plains 76 villages",
            "-4 1 minecraft:pillager_outpost 76 outposts",
            "-3 -1 minecraft:village_plains 72 villages",
            "-3 1 minecraft:pillager_outpost 68 outposts",
            "-3 6 minecraft:village_plains 88 villages",
            "-2 0 minecraft:village_plains 92 villages",
            "-2 6 minecraft:pillager_outpost 80 outposts",
            "-1 -4 minecraft:village_plains 92 villages",
            "-1 4 minecraft:ancient_city -12 cities",
            "-1 6 minecraft:ancient_city -23 cities",
            "0 -6 minecraft:village_plains 88 villages",
            "0 -2 minecraft:village_plains 80 villages",
            "0 4 minecraft:pillager_outpost 68 outposts",
            "0 6 minecraft:village_plains 64 villages",
            "1 -6 minecraft:ancient_city -32 cities",
            "1 -4 minecraft:pillager_outpost 76 outposts",
            "1 0 minecraft:ancient_city -37 cities",
            "1 2 minecraft:pillager_outpost 64 outposts",
            "1 3 minecraft:ancient_city -38 cities",
            "1 5 minecraft:ancient_city -12 cities",
            "2 -2 minecraft:pillager_outpost 64 villages",
            "2 5 minecraft:village_plains 80 villages",
            "2 5 minecraft:pillager_outpost 80 outposts",
            "3 -5 minecraft:village_plains 92 villages",
            "3 1 minecraft:village_plains 80 villages",
            "3 5 minecraft:pillager_outpost 72 villages",
            "4 1 minecraft:ancient_city -16 cities",
            "4 1 minecraft:pillager_outpost 72 outposts",
            "4 3 minecraft:pillager_outpost 68 outposts",
            "4 5 minecraft:pillager_outpost 64 outposts",
            "4 6 minecraft:ancient_city -18 cities",
            "5 -5 minecraft:village_plains 76 villages",
            "5 -5 minecraft:ancient_city -24 cities",
            "5 1 minecraft:pillager_outpost 64 outposts",
            "5 2 minecraft:ancient_city -33 cities",
            "5 4 minecraft:village_plains 88 villages",
            "5 6 minecraft:village_plains 84 villages",
            "6 -5 minecraft:pillager_outpost 68 outposts",
            "6 -3 minecraft:village_plains 64 villages",
            "6 -2 minecraft:pillager_outpost 92 outposts",
            "6 2 minecraft:ancient_city -18 cities");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<LoadedPack> packs = new ArrayList<>();

    @After
    public void closePacks() {
        packs.forEach(LoadedPack::close);
    }

    @Test
    public void startsMatchThePlannersForAFixedSeed() throws IOException {
        KList<StructurePlacement> placements = new KList<>();
        placements.add(placement("villages", false, -64, 320, 0.2D, source("minecraft:village_plains", 3),
                source("minecraft:pillager_outpost", 1)));
        placements.add(placement("cities", true, -50, -10, 0.15D, source("minecraft:ancient_city", 1)));
        placements.add(placement("outposts", false, -64, 320, 0.2D, source("minecraft:pillager_outpost", 1)));
        Engine engine = engine(placements);

        List<String> starts = new ArrayList<>();
        for (int chunkX = -GRID_RADIUS_CHUNKS; chunkX <= GRID_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = -GRID_RADIUS_CHUNKS; chunkZ <= GRID_RADIUS_CHUNKS; chunkZ++) {
                List<NativeStructureStartPlan> plans = NativeStructureStarts.plansAt(engine, chunkX, chunkZ);
                List<NativeStartPlanner.Plan> modulePlans = NativeStartPlanner.plansAt(engine.getGeneration().world(), chunkX, chunkZ);
                assertEquals(modulePlans.size(), plans.size());
                for (int index = 0; index < plans.size(); index++) {
                    NativeStructureStartPlan plan = plans.get(index);
                    NativeStartPlanner.Plan modulePlan = modulePlans.get(index);
                    assertEquals(modulePlan.source().structure(), plan.structureKey());
                    starts.add(plan.chunkX() + " " + plan.chunkZ() + " " + plan.structureKey() + " " + plan.baseY() + " "
                            + modulePlan.placement().placementId());
                }
            }
        }

        assertEquals(PLANNER_STARTS, starts);
    }

    @Test
    public void fitsAndJigsawOverridesReachThePlatformWithTheirJsonUnchanged() throws IOException {
        StructureTerrain terrain = GSON.fromJson("{\"mode\":\"FORCE_CARVE\",\"shape\":\"ERODED\",\"horizontalPadding\":12,"
                + "\"erosionStrength\":0.4,\"encasePalette\":{\"palette\":[{\"block\":\"minecraft:deepslate\"},"
                + "{\"block\":\"minecraft:tuff\"}]}}", StructureTerrain.class);
        StructureStilts stilt = GSON.fromJson("{\"maxDepth\":40,\"spacing\":2,"
                + "\"palette\":{\"palette\":[{\"block\":\"minecraft:stone_bricks\"}]}}", StructureStilts.class);
        JigsawOverrides jigsaw = JigsawOverrides.none().withStartPool(ResourceId.parse("minecraft:village/plains/town_centers"))
                .withMaxDepth(5).withHeightmap(JigsawOverrides.Heightmap.OCEAN_FLOOR_WG).withExpansionHack(false);
        StructurePlacement fitted = TestSettings.with(placement("fitted", true, -40, -20, 1D,
                TestSettings.with(source("minecraft:ancient_city", 1), "jigsaw", jigsaw)), "terrain", terrain, "stilt", stilt);
        KList<StructurePlacement> placements = new KList<>();
        placements.add(fitted);
        Engine engine = engine(placements);

        NativeStructureStartPlan plan = NativeStructureStarts.matchingPlan(engine, "MINECRAFT:ANCIENT_CITY", 0, 0);

        assertEquals(new NativeStartMemo("{\"terrain\":" + GSON.toJson(terrain) + ",\"stilts\":" + GSON.toJson(stilt) + "}"),
                plan.memo());
        assertEquals(new NativeJigsawOverrides("minecraft:village/plains/town_centers", "", 5, null, null, false,
                "OCEAN_FLOOR_WG", null, null, NativeJigsawOverrides.SOURCE), plan.jigsaw());
        assertNull(NativeStructureStarts.matchingPlan(engine, "minecraft:stronghold", 0, 0));
    }

    private static NativeStructureSource source(String structure, int weight) {
        return nativeSource(structure, weight);
    }

    private static StructurePlacement placement(String id, boolean underground, int minHeight, int maxHeight, double density,
                                                NativeStructureSource... sources) {
        StructurePlacement placement = StructurePlacementFixtures.placement("placementId", id, "distribution", StructureDistribution.DENSITY, "density", density, "underground", underground, "minHeight", minHeight, "maxHeight", maxHeight);
        for (NativeStructureSource source : sources) {
            withAdded(placement, "nativeStructures", source);
        }
        return placement;
    }

    /** An engine whose pack serves the built-in modules, with terrain heights that vary per column and a high sea. */
    private Engine engine(KList<StructurePlacement> placements) throws IOException {
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        packs.add(BuiltInPack.serveWithResources(data, folder, List.of()));
        when(engine.getData()).thenReturn(data);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getSeedManager()).thenReturn(new SeedManager(SEED));
        when(engine.getMinHeight()).thenReturn(MIN_HEIGHT);
        when(engine.getHeight()).thenReturn(384);
        when(engine.getCacheID()).thenReturn(1);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenAnswer(call -> {
            int x = call.getArgument(0);
            int z = call.getArgument(1);
            return 100 + Math.floorMod(x * 7 + z * 13, 60);
        });
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getFluidHeight()).thenReturn(127);
        when(dimension.getModuleSettings()).thenReturn(declaring(placements));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }
}
