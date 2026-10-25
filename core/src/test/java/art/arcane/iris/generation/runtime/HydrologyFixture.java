package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.TransitionDisplacementField;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

final class HydrologyFixture implements AutoCloseable {
    static final double NATURAL = 150.3D;
    static final int POND_LEVEL = 156;
    static final int SEA_LEVEL = 127;

    final Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
    final EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
    final AtomicBoolean mainThread = new AtomicBoolean();
    final IrisData data;
    final IrisComplex complex;
    final FakeHydrology hydrology;
    private final MockedStatic<B> blocks;
    private final MockedStatic<IrisImageMapRuntime> imageRuntimes;
    private final List<IrisComplex> extras = new ArrayList<>();

    HydrologyFixture(Path pack) {
        this(pack, null, true);
    }

    HydrologyFixture(Path pack, File worldFolder) {
        this(pack, worldFolder, false);
    }

    private HydrologyFixture(Path pack, File worldFolder, boolean fake) {
        blocks = mockStatic(B.class);
        imageRuntimes = mockStatic(IrisImageMapRuntime.class);
        NativeBlockState block = mock(NativeBlockState.class);
        when(block.isFluid()).thenReturn(true);
        blocks.when(() -> B.getState(anyString())).thenReturn(block);
        blocks.when(() -> B.getStateOrNull(anyString(), eq(false))).thenReturn(block);
        IrisImageMapRuntime images = mock(IrisImageMapRuntime.class);
        when(images.has(IrisImageMapApplication.BIOME)).thenReturn(false);
        when(images.sampleTerrainHeight(anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> invocation.<Double>getArgument(2));
        imageRuntimes.when(() -> IrisImageMapRuntime.compile(engine)).thenReturn(images);
        data = IrisData.openRuntime(pack.toFile());
        IrisDimension dimension = data.getDimensionLoader().load("main");
        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();
        doReturn(384).when(engine).getHeight();
        doReturn(-64).when(engine).getMinHeight();
        doReturn(320).when(engine).getMaxHeight();
        when(hooks.isMainThread()).thenAnswer(ignored -> mainThread.get());
        doReturn(hooks).when(engine).getPlatformHooks();
        doReturn(new SeedManager(FakeHydrology.SEED)).when(engine).getSeedManager();
        doReturn(worldFolder == null ? null
                : IrisWorld.builder().name("hydrology-world").worldFolder(worldFolder).minHeight(-64).maxHeight(320).build()).when(engine).getWorld();
        AtomicReference<IrisComplex> built = new AtomicReference<>();
        hydrology = fake ? new FakeHydrology(mainThread::get, (x, z) -> built.get().naturalTrueHeight(x, z), SEA_LEVEL, POND_LEVEL) : null;
        complex = fake ? construct(hydrology.runtime, () -> new IrisComplex(engine)) : new IrisComplex(engine);
        built.set(complex);
        doReturn(complex).when(engine).getComplex();
        complex.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> NATURAL));
    }

    IrisComplex complex(HydrologyRuntime runtime) {
        IrisComplex built = construct(runtime, () -> new IrisComplex(engine));
        built.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> NATURAL));
        extras.add(built);
        return built;
    }

    IrisComplex transitionComplex(HydrologyRuntime runtime, TransitionGenerationPlan plan, TransitionDisplacementField displacement) {
        try (MockedConstruction<TransitionDisplacementField> displacements = mockConstruction(TransitionDisplacementField.class,
                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(displacement)))) {
            IrisComplex built = construct(runtime, () -> mock(IrisComplex.class,
                    withSettings().useConstructor(engine, plan, false).defaultAnswer(CALLS_REAL_METHODS)));
            if (displacements.constructed().size() != 1) {
                throw new IllegalStateException("The transition complex built " + displacements.constructed().size() + " displacement fields");
            }
            built.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> NATURAL));
            extras.add(built);
            return built;
        }
    }

    private IrisComplex construct(HydrologyRuntime runtime, Supplier<IrisComplex> constructor) {
        IrisDimension dimension = engine.getDimension();
        if (dimension.getHydrology() == null) {
            dimension.setHydrology(new IrisHydrology());
        }
        try (MockedStatic<HydrologyRuntime> runtimes = mockStatic(HydrologyRuntime.class,
                invocation -> invocation.getMethod().getName().equals("create") ? runtime : invocation.callRealMethod())) {
            return constructor.get();
        }
    }

    IrisComplex detachedComplex() {
        IrisComplex detached = new IrisComplex(engine, null, true);
        extras.add(detached);
        return detached;
    }

    static Path pack(Path pack, String regionPolicy, String biomePolicy) throws IOException {
        return pack(pack, regionPolicy, biomePolicy, false);
    }

    static Path pack(Path pack, String regionPolicy, String biomePolicy, boolean hydrology) throws IOException {
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("regions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.createDirectories(pack.resolve("generators"));
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"regions":["plain"],"landChance":1,%s
                 "carvingEnabled":false,
                 "hydrologyPolicy":{"shoreBiomes":["dune"],"shoreBiomeWidth":6}}
                """.formatted(hydrology ? "\"hydrology\":{\"enabled\":true,\"prefetchRings\":0}," : ""));
        Files.writeString(pack.resolve("regions/plain.json"), "{\"landBiomes\":[\"plain\"]" + policy(regionPolicy) + "}");
        Files.writeString(pack.resolve("generators/flat.json"), "{\"composite\":[]}");
        Files.writeString(pack.resolve("biomes/plain.json"),
                "{\"generators\":[{\"generator\":\"flat\",\"min\":10,\"max\":10}]" + policy(biomePolicy) + "}");
        Files.writeString(pack.resolve("biomes/sand.json"), "{\"generators\":[{\"generator\":\"flat\",\"min\":10,\"max\":10}]}");
        Files.writeString(pack.resolve("biomes/dune.json"), "{\"generators\":[{\"generator\":\"flat\",\"min\":10,\"max\":10}]}");
        return pack;
    }

    private static String policy(String policy) {
        return policy == null ? "" : ",\"hydrologyPolicy\":" + policy;
    }

    @Override
    public void close() {
        try {
            if (hydrology != null) {
                hydrology.close();
            }
            for (IrisComplex extra : extras) {
                extra.close();
            }
            complex.close();
            data.close();
        } finally {
            imageRuntimes.close();
            blocks.close();
            Mockito.framework().clearInlineMock(engine);
            Mockito.framework().clearInlineMock(hooks);
        }
    }
}
