package art.arcane.iris.engine.framework;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.object.material.IrisMaterialPalette;
import art.arcane.iris.engine.object.structure.IrisStructureCarveShape;
import art.arcane.iris.engine.object.structure.IrisStructurePlacement;
import art.arcane.iris.engine.object.structure.IrisStructureStiltSettings;
import art.arcane.iris.engine.object.structure.IrisStructureTerrain;
import art.arcane.iris.engine.object.structure.IrisStructureTerrainMode;
import art.arcane.iris.engine.object.structure.StructureDistribution;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConfiguration;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawHeightmap;
import art.arcane.iris.engine.object.structure.vanilla.IrisNativeStructure;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import com.volmit.host.LoadedPack;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The starts the pack's nativeStructures placements make, now through iris:structure's vanilla structure hook, are the
 * ones the core planner made before the move: the same chunks, structures, base heights and placement identities for
 * a fixed seed (pinned from the planner), and fits whose persisted JSON is unchanged.
 */
public class NativeStructureStartsTest {
    private static final long SEED = 1337L;
    private static final int MIN_HEIGHT = -64;
    private static final int GRID_RADIUS_CHUNKS = 6;
    private static final Gson GSON = new Gson();

    /** chunkX chunkZ structure baseY placement identity, as NativeStructurePlacementPlanner.plansAt returned them. */
    private static final List<String> PLANNER_STARTS = List.of(
            "-6 -4 minecraft:village_plains 72 villages -2304053147360248982",
            "-6 0 minecraft:ancient_city -11 cities 7830921123394837354",
            "-6 1 minecraft:pillager_outpost 92 outposts 3980124831785354812",
            "-6 3 minecraft:village_plains 88 villages -2304053147360248982",
            "-6 3 minecraft:ancient_city -14 cities 7830921123394837354",
            "-5 -4 minecraft:pillager_outpost 64 outposts 3980124831785354812",
            "-5 -3 minecraft:ancient_city -40 cities 7830921123394837354",
            "-5 5 minecraft:pillager_outpost 76 outposts 3980124831785354812",
            "-4 -5 minecraft:pillager_outpost 88 outposts 3980124831785354812",
            "-4 -1 minecraft:village_plains 80 villages -2304053147360248982",
            "-4 -1 minecraft:pillager_outpost 80 outposts 3980124831785354812",
            "-4 1 minecraft:village_plains 76 villages -2304053147360248982",
            "-4 1 minecraft:pillager_outpost 76 outposts 3980124831785354812",
            "-3 -1 minecraft:village_plains 72 villages -2304053147360248982",
            "-3 1 minecraft:pillager_outpost 68 outposts 3980124831785354812",
            "-3 6 minecraft:village_plains 88 villages -2304053147360248982",
            "-2 0 minecraft:village_plains 92 villages -2304053147360248982",
            "-2 6 minecraft:pillager_outpost 80 outposts 3980124831785354812",
            "-1 -4 minecraft:village_plains 92 villages -2304053147360248982",
            "-1 4 minecraft:ancient_city -12 cities 7830921123394837354",
            "-1 6 minecraft:ancient_city -23 cities 7830921123394837354",
            "0 -6 minecraft:village_plains 88 villages -2304053147360248982",
            "0 -2 minecraft:village_plains 80 villages -2304053147360248982",
            "0 4 minecraft:pillager_outpost 68 outposts 3980124831785354812",
            "0 6 minecraft:village_plains 64 villages -2304053147360248982",
            "1 -6 minecraft:ancient_city -32 cities 7830921123394837354",
            "1 -4 minecraft:pillager_outpost 76 outposts 3980124831785354812",
            "1 0 minecraft:ancient_city -37 cities 7830921123394837354",
            "1 2 minecraft:pillager_outpost 64 outposts 3980124831785354812",
            "1 3 minecraft:ancient_city -38 cities 7830921123394837354",
            "1 5 minecraft:ancient_city -12 cities 7830921123394837354",
            "2 -2 minecraft:pillager_outpost 64 villages -2304053147360248982",
            "2 5 minecraft:village_plains 80 villages -2304053147360248982",
            "2 5 minecraft:pillager_outpost 80 outposts 3980124831785354812",
            "3 -5 minecraft:village_plains 92 villages -2304053147360248982",
            "3 1 minecraft:village_plains 80 villages -2304053147360248982",
            "3 5 minecraft:pillager_outpost 72 villages -2304053147360248982",
            "4 1 minecraft:ancient_city -16 cities 7830921123394837354",
            "4 1 minecraft:pillager_outpost 72 outposts 3980124831785354812",
            "4 3 minecraft:pillager_outpost 68 outposts 3980124831785354812",
            "4 5 minecraft:pillager_outpost 64 outposts 3980124831785354812",
            "4 6 minecraft:ancient_city -18 cities 7830921123394837354",
            "5 -5 minecraft:village_plains 76 villages -2304053147360248982",
            "5 -5 minecraft:ancient_city -24 cities 7830921123394837354",
            "5 1 minecraft:pillager_outpost 64 outposts 3980124831785354812",
            "5 2 minecraft:ancient_city -33 cities 7830921123394837354",
            "5 4 minecraft:village_plains 88 villages -2304053147360248982",
            "5 6 minecraft:village_plains 84 villages -2304053147360248982",
            "6 -5 minecraft:pillager_outpost 68 outposts 3980124831785354812",
            "6 -3 minecraft:village_plains 64 villages -2304053147360248982",
            "6 -2 minecraft:pillager_outpost 92 outposts 3980124831785354812",
            "6 2 minecraft:ancient_city -18 cities 7830921123394837354");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<LoadedPack> packs = new ArrayList<>();

    @After
    public void closePacks() {
        packs.forEach(LoadedPack::close);
    }

    @Test
    public void startsMatchThePlannersForAFixedSeed() throws IOException {
        KList<IrisStructurePlacement> placements = new KList<>();
        placements.add(placement("villages", false, -64, 320, 0.2D, source("minecraft:village_plains", 3),
                source("minecraft:pillager_outpost", 1)));
        placements.add(placement("cities", true, -50, -10, 0.15D, source("minecraft:ancient_city", 1)));
        placements.add(placement("outposts", false, -64, 320, 0.2D, source("minecraft:pillager_outpost", 1)));
        Engine engine = engine(placements);

        List<String> starts = new ArrayList<>();
        for (int chunkX = -GRID_RADIUS_CHUNKS; chunkX <= GRID_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = -GRID_RADIUS_CHUNKS; chunkZ <= GRID_RADIUS_CHUNKS; chunkZ++) {
                for (NativeStructureStartPlan plan : NativeStructureStarts.plansAt(engine, chunkX, chunkZ)) {
                    starts.add(plan.chunkX() + " " + plan.chunkZ() + " " + plan.structureKey() + " " + plan.baseY() + " "
                            + placementId(placements, plan.placementIdentity()) + " " + plan.placementIdentity());
                }
            }
        }

        assertEquals(PLANNER_STARTS, starts);
    }

    @Test
    public void fitsAndJigsawOverridesReachThePlatformWithTheirJsonUnchanged() throws IOException {
        IrisStructureTerrain terrain = new IrisStructureTerrain().setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setShape(IrisStructureCarveShape.ERODED).setHorizontalPadding(12).setErosionStrength(0.4D)
                .setEncasePalette(new IrisMaterialPalette().qclear().qadd("minecraft:deepslate").qadd("minecraft:tuff"));
        IrisStructureStiltSettings stilt = new IrisStructureStiltSettings().setMaxDepth(40).setSpacing(2)
                .setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:stone_bricks"));
        IrisJigsawConfiguration jigsaw = new IrisJigsawConfiguration().setStartPool("minecraft:village/plains/town_centers")
                .setMaxDepth(5).setProjectStartToHeightmap(IrisJigsawHeightmap.OCEAN_FLOOR_WG).setUseExpansionHack(false);
        IrisStructurePlacement fitted = placement("fitted", true, -40, -20, 1D, source("minecraft:ancient_city", 1)
                .setJigsaw(jigsaw)).setTerrain(terrain).setStilt(stilt);
        KList<IrisStructurePlacement> placements = new KList<>();
        placements.add(fitted);
        Engine engine = engine(placements);

        NativeStructureStartPlan plan = NativeStructureStarts.matchingPlan(engine, "MINECRAFT:ANCIENT_CITY", 0, 0);

        assertEquals(GSON.toJson(terrain), GSON.toJson(plan.terrain()));
        assertEquals(GSON.toJson(stilt), GSON.toJson(plan.stilt()));
        assertEquals(GSON.toJson(jigsaw), GSON.toJson(plan.jigsaw()));
        assertEquals(GSON.toJson(new NativeStructureStartPlan(plan.structureKey(), 0, 0, plan.baseY(), true, false,
                StructurePlacementGrid.placementIdentity(fitted), jigsaw, stilt, terrain).decision()), GSON.toJson(plan.decision()));
        assertNull(NativeStructureStarts.matchingPlan(engine, "minecraft:stronghold", 0, 0));
    }

    private static IrisNativeStructure source(String structure, int weight) {
        return new IrisNativeStructure().setStructure(structure).setWeight(weight);
    }

    private static IrisStructurePlacement placement(String id, boolean underground, int minHeight, int maxHeight, double density,
                                                    IrisNativeStructure... sources) {
        IrisStructurePlacement placement = new IrisStructurePlacement().setPlacementId(id)
                .setDistribution(StructureDistribution.DENSITY).setDensity(density)
                .setUnderground(underground).setMinHeight(minHeight).setMaxHeight(maxHeight);
        for (IrisNativeStructure source : sources) {
            placement.getNativeStructures().add(source);
        }
        return placement;
    }

    private static String placementId(KList<IrisStructurePlacement> placements, long identity) {
        return placements.stream().filter(placement -> StructurePlacementGrid.placementIdentity(placement) == identity)
                .findFirst().orElseThrow().getPlacementId();
    }

    /** An engine whose pack serves the built-in modules, with terrain heights that vary per column and a high sea. */
    private Engine engine(KList<IrisStructurePlacement> placements) throws IOException {
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
        when(dimension.getModuleSettings()).thenReturn(ModuleSettings.NONE);
        when(dimension.getStructures()).thenReturn(placements);
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }
}
