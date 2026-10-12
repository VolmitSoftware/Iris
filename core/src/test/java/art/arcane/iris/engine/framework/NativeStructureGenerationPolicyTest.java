package art.arcane.iris.engine.framework;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.builtin.structure.StructureAdjustment;
import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.structure.vanilla.IrisNativeStructure;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.structure.IrisStructurePlacement;
import art.arcane.iris.engine.object.structure.vanilla.NativeStructureSuppression;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.host.LoadedPack;
import com.volmit.stage.StageGraph;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.core.modules.ModuleVanillaStructures;
import com.google.gson.Gson;
import com.volmit.resource.ResourceId;
import com.volmit.service.VanillaStructures;
import com.volmit.vanilla.PlatformStart;
import com.volmit.vanilla.StartMemo;
import com.volmit.vanilla.StartReach;
import com.volmit.vanilla.VanillaStructureContext;
import com.volmit.vanilla.VanillaStructureRule;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureGenerationPolicyTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<LoadedPack> packs = new ArrayList<>();

    @After
    public void closePacks() {
        packs.forEach(LoadedPack::close);
    }

    @Test
    public void generationStatusMessagesAreSharedAcrossPlatforms() {
        assertEquals(
                "Native structure minecraft:village_plains is disabled by this dimension's importedStructures settings or a module of the pack.",
                NativeStructureGenerationPolicy.generationStatusMessage(
                        "minecraft:village_plains", NativeStructureGenerationStatus.DISABLED_BY_PACK));
        assertEquals(
                "Native structure minecraft:ancient_city is replaced by an Iris placement in this pack and locates through that explicit replacement.",
                NativeStructureGenerationPolicy.generationStatusMessage(
                        "minecraft:ancient_city", NativeStructureGenerationStatus.REPLACED_BY_IRIS));
    }

    /**
     * The "blanket-disable a namespace, re-place chosen structures via nativeStructures placements"
     * pattern: generation honors the placement (the planner never consults the disable list), so the
     * policy must report those keys as REPLACED_BY_IRIS — making /iris find|goto and
     * /iris structure verify locate the placement instead of claiming the key is disabled.
     */
    @Test
    public void disabledKeyWithActivePlacementResolvesAsReplacedByIris() throws IOException {
        Engine engine = engineWithDisabledNamespaceAndRegionPlacement("nova_structures:tavern_oak");

        assertEquals(NativeStructureGenerationStatus.REPLACED_BY_IRIS,
                NativeStructureGenerationPolicy.resolve(engine, "nova_structures:tavern_oak"));
    }

    @Test
    public void disabledKeyWithoutPlacementStaysDisabled() throws IOException {
        Engine engine = engineWithDisabledNamespaceAndRegionPlacement("nova_structures:tavern_oak");

        assertEquals(NativeStructureGenerationStatus.DISABLED_BY_PACK,
                NativeStructureGenerationPolicy.resolve(engine, "nova_structures:witch_villa"));
        assertFalse(NativeStructureGenerationPolicy.generates(engine, "nova_structures:witch_villa"));
    }

    @Test
    public void exactDisabledKeyDoesNotDisableSiblingVariant() throws IOException {
        StructureSettings settings = new StructureSettings(List.of(), List.of("minecraft:ruined_portal"), 0, true,
                List.of(), List.of());
        Engine engine = engine(settings, regionPlacement("nova_structures:tavern_oak"), new KList<>(), List.of());

        assertEquals(NativeStructureGenerationStatus.DISABLED_BY_PACK,
                NativeStructureGenerationPolicy.resolve(engine, "minecraft:ruined_portal"));
        assertEquals(NativeStructureGenerationStatus.GENERATE_NATIVE,
                NativeStructureGenerationPolicy.resolve(engine, "minecraft:ruined_portal_nether"));
    }

    /** Suppression comes from the iris:structure module's rule. */
    @Test
    public void aStructureAnIrisPlacementReplacesIsBlockedByTheStructureModule() throws IOException {
        IrisStructurePlacement replacement = new IrisStructurePlacement().setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);
        replacement.getNativeStructures().add(new IrisNativeStructure().setStructure("minecraft:village_plains"));
        KList<IrisStructurePlacement> dimensionPlacements = new KList<>();
        dimensionPlacements.add(replacement);
        Engine engine = engine(allowingEverything(), new KList<>(), dimensionPlacements, List.of());

        assertEquals(NativeStructureGenerationStatus.REPLACED_BY_IRIS,
                NativeStructureGenerationPolicy.resolve(engine, "minecraft:village_plains"));
        assertFalse(NativeStructureGenerationPolicy.generates(engine, "minecraft:village_plains"));
        assertEquals(NativeStructureGenerationStatus.GENERATE_NATIVE,
                NativeStructureGenerationPolicy.resolve(engine, "minecraft:village_desert"));
    }

    @Test
    public void aStructureAPlacementAlsoPlacesStillGeneratesNatively() throws IOException {
        Engine engine = engine(allowingEverything(), regionPlacement("minecraft:village_plains"), new KList<>(), List.of());

        assertTrue(NativeStructureGenerationPolicy.generates(engine, "minecraft:village_plains"));
    }

    @Test
    public void packModulesBlockOrReplaceStructuresThroughTheirRules() throws IOException {
        Engine engine = engine(allowingEverything(), new KList<>(), new KList<>(), List.of(new ProviderModule(ruling((structure, world) ->
                switch (structure.path()) {
                    case "pillager_outpost" -> VanillaStructureRule.BLOCK;
                    case "igloo" -> VanillaStructureRule.REPLACE;
                    default -> VanillaStructureRule.GENERATE;
                }))));

        assertEquals(NativeStructureGenerationStatus.DISABLED_BY_PACK,
                NativeStructureGenerationPolicy.resolve(engine, "minecraft:pillager_outpost"));
        assertEquals(NativeStructureGenerationStatus.REPLACED_BY_IRIS,
                NativeStructureGenerationPolicy.resolve(engine, "minecraft:igloo"));
        assertEquals(NativeStructureGenerationStatus.GENERATE_NATIVE,
                NativeStructureGenerationPolicy.resolve(engine, "minecraft:mansion"));
    }

    @Test
    public void rulesAreAskedOncePerStructureAndPackSnapshot() throws IOException {
        AtomicInteger asked = new AtomicInteger();
        Engine engine = engine(allowingEverything(), new KList<>(), new KList<>(), List.of(new ProviderModule(ruling((structure, world) -> {
            asked.incrementAndGet();
            return VanillaStructureRule.GENERATE;
        }))));

        NativeStructureGenerationPolicy.resolve(engine, "minecraft:igloo");
        NativeStructureGenerationPolicy.resolve(engine, "minecraft:igloo");
        assertEquals(1, asked.get());

        IrisData data = engine.getData();
        IrisPackResources reloaded = new IrisPackResources(data);
        when(data.getPackResources()).thenReturn(reloaded);
        NativeStructureGenerationPolicy.resolve(engine, "minecraft:igloo");
        assertEquals("a new snapshot asks again", 2, asked.get());
    }

    @Test
    public void aProviderThatFailsNamesItsModule() throws IOException {
        Engine engine = engine(allowingEverything(), new KList<>(), new KList<>(), List.of(new ProviderModule(ruling((structure, world) -> {
            throw new IllegalArgumentException("broken");
        }))));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> NativeStructureGenerationPolicy.resolve(engine, "minecraft:igloo"));
        assertTrue(failure.getMessage(), failure.getMessage().startsWith(
                "Module test:hook from host classpath failed deciding over vanilla structure minecraft:igloo: "));
    }

    @Test
    public void shiftsAndReachesOfTheSettingsAndOfPackModulesAddUpPerModule() throws IOException {
        StructureTerrain bore = new Gson().fromJson("{\"mode\":\"BORE\",\"horizontalPadding\":6}", StructureTerrain.class);
        StructureSettings settings = new StructureSettings(List.of(), List.of(), -32, true, List.of(), List.of(
                new StructureAdjustment(List.of("minecraft:stronghold"), 4, null, true, null, bore)));
        Engine engine = engine(settings, new KList<>(), new KList<>(), List.of(new ProviderModule(new VanillaStructures() {
            @Override
            public int shift(PlatformStart start, VanillaStructureContext context) {
                return -1;
            }

            @Override
            public StartReach reach(ResourceId structure, boolean underground, Optional<StartMemo> memo,
                                    VanillaStructureContext context) {
                return StartReach.blocks(3);
            }
        })));

        assertEquals(-29, ModuleVanillaStructures.shiftOf(engine, placement("minecraft:stronghold", true)));
        assertEquals(List.of(new NativeStructureReach(false, 6), new NativeStructureReach(false, 3)),
                ModuleVanillaStructures.reachesOf(engine, "minecraft:stronghold", true, null));
        assertEquals(-1, ModuleVanillaStructures.shiftOf(engine, placement("minecraft:village_plains", false)));
        assertEquals(List.of(NativeStructureReach.PLATFORM, new NativeStructureReach(false, 3)),
                ModuleVanillaStructures.reachesOf(engine, "minecraft:village_plains", false, null));
    }

    private static NativeStartPlacement placement(String structureKey, boolean underground) {
        return new NativeStartPlacement(structureKey, 0, 0, 0, 10, 0, 20, 30, 20, underground, false, -64, (x, z) -> 100);
    }

    private static VanillaStructures ruling(RuleAnswer rule) {
        return new VanillaStructures() {
            @Override
            public VanillaStructureRule rule(ResourceId structure, VanillaStructureContext context) {
                return rule.decide(structure, context);
            }
        };
    }

    private Engine engineWithDisabledNamespaceAndRegionPlacement(String placedKey) throws IOException {
        StructureSettings settings = new StructureSettings(List.of("nova_structures:"), List.of(), 0, true,
                List.of(), List.of());
        return engine(settings, regionPlacement(placedKey), new KList<>(), List.of());
    }

    private static StructureSettings allowingEverything() {
        return new StructureSettings();
    }

    private static KList<IrisStructurePlacement> regionPlacement(String placedKey) {
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getNativeStructures().add(new IrisNativeStructure().setStructure(placedKey));
        KList<IrisStructurePlacement> placements = new KList<>();
        placements.add(placement);
        return placements;
    }

    /** An engine whose pack serves Iris's built-in modules and {@code packModules}, with the given structure placements. */
    private Engine engine(StructureSettings settings, KList<IrisStructurePlacement> regionPlacements,
                          KList<IrisStructurePlacement> dimensionPlacements, List<IrisModule> packModules) throws IOException {
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        IrisDimension dimension = mock(IrisDimension.class);
        IrisRegion region = mock(IrisRegion.class);
        when(region.getStructures()).thenReturn(regionPlacements);
        when(region.getModuleSettings()).thenReturn(ModuleSettings.NONE);
        KList<IrisRegion> regions = new KList<>();
        regions.add(region);

        packs.add(BuiltInPack.serveWithResources(data, folder, packModules));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(settings));
        when(dimension.getStructures()).thenReturn(dimensionPlacements);
        when(dimension.getAllRegions(engine)).thenReturn(regions);
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }

    private interface RuleAnswer {
        VanillaStructureRule decide(ResourceId structure, VanillaStructureContext context);
    }

    /** A pack module whose only part is a vanilla structures provider. */
    private record ProviderModule(VanillaStructures provider) implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("test:hook", "Hook", "1");
        }

        @Override
        public void install(StageGraph graph) {
        }

        @Override
        public void register(FeatureRegistry registry) {
            registry.registerService(VanillaStructures.class, provider);
        }
    }
}
