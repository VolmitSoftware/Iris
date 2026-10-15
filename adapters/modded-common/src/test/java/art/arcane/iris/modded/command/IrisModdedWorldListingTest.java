package art.arcane.iris.modded.command;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngineData;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisModdedWorldListingTest {
    private static final long SERVER_SEED = 1111L;

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void worldsListsEachIrisWorldWithItsOwnSeedAndPersistedChunkCount() {
        NativeWorld alpha = level("iris:alpha");
        NativeWorld beta = level("iris:beta");
        NativeWorld vanilla = level("minecraft:the_end");
        NativeModdedServer server = mock(NativeModdedServer.class);
        when(server.worlds()).thenReturn(List.of(alpha, beta, vanilla));
        List<String> output = new ArrayList<>();
        NativeCommandSource source = source(server, null, true, output);
        IrisModdedChunkGenerator alphaGenerator = generator("overworld:overworld", engine(424242L, 1089));
        IrisModdedChunkGenerator betaGenerator = generator("overworld:overworld", engine(-77L, 0));

        try (MockedStatic<NativeWorldGenerators> generators = mockStatic(NativeWorldGenerators.class)) {
            generators.when(() -> NativeWorldGenerators.find(alpha, IrisModdedChunkGenerator.class))
                    .thenReturn(alphaGenerator);
            generators.when(() -> NativeWorldGenerators.find(beta, IrisModdedChunkGenerator.class))
                    .thenReturn(betaGenerator);

            assertEquals(1, IrisModdedCommands.info(source, null));
        }

        String alphaRow = row(output, "iris:alpha");
        String betaRow = row(output, "iris:beta");
        assertTrue(alphaRow, alphaRow.contains(" seed=424242 "));
        assertTrue(alphaRow, alphaRow.contains(" generated=1089 "));
        assertTrue(betaRow, betaRow.contains(" seed=-77 "));
        assertTrue(betaRow, betaRow.contains(" generated=0 "));
        for (String line : output) {
            assertFalse(line, line.contains("seed=" + SERVER_SEED));
        }
    }

    @Test
    public void worldsLabelsEachRowWithItsOwnWorldKey() {
        NativeWorld alpha = level("iris:alpha");
        NativeWorld beta = level("iris:beta");
        NativeWorld gamma = level("iris:gamma");
        NativeModdedServer server = mock(NativeModdedServer.class);
        when(server.worlds()).thenReturn(List.of(alpha, beta, gamma));
        List<String> output = new ArrayList<>();
        NativeCommandSource source = source(server, null, true, output);
        IrisModdedChunkGenerator alphaGenerator = generator("overworld:overworld", engine(424242L, 1089));
        IrisModdedChunkGenerator betaGenerator = generator("overworld:overworld", engine(-77L, 0));
        IrisModdedChunkGenerator gammaGenerator = generator("overworld:overworld", null);

        try (MockedStatic<NativeWorldGenerators> generators = mockStatic(NativeWorldGenerators.class)) {
            generators.when(() -> NativeWorldGenerators.find(alpha, IrisModdedChunkGenerator.class))
                    .thenReturn(alphaGenerator);
            generators.when(() -> NativeWorldGenerators.find(beta, IrisModdedChunkGenerator.class))
                    .thenReturn(betaGenerator);
            generators.when(() -> NativeWorldGenerators.find(gamma, IrisModdedChunkGenerator.class))
                    .thenReturn(gammaGenerator);

            assertEquals(1, IrisModdedCommands.info(source, null));
        }

        assertTrue(row(output, "iris:alpha").startsWith("iris:alpha: pack=overworld "));
        assertTrue(row(output, "iris:beta").startsWith("iris:beta: pack=overworld "));
        assertEquals("iris:gamma: pack=overworld:overworld (engine not started yet)", row(output, "iris:gamma"));
        for (String line : output) {
            assertFalse(line, line.startsWith("iris:overworld"));
            assertFalse(line, line.contains(" world="));
        }
    }

    @Test
    public void worldsOmitsTheSeedForPlayersBelowGamemaster() {
        NativeWorld alpha = level("iris:alpha");
        NativeModdedServer server = mock(NativeModdedServer.class);
        when(server.worlds()).thenReturn(List.of(alpha));
        List<String> output = new ArrayList<>();
        NativeCommandSource source = source(server, null, false, output);
        IrisModdedChunkGenerator alphaGenerator = generator("overworld:overworld", engine(424242L, 1089));

        try (MockedStatic<NativeWorldGenerators> generators = mockStatic(NativeWorldGenerators.class)) {
            generators.when(() -> NativeWorldGenerators.find(alpha, IrisModdedChunkGenerator.class))
                    .thenReturn(alphaGenerator);

            assertEquals(1, IrisModdedCommands.info(source, null));
        }

        String alphaRow = row(output, "iris:alpha");
        assertFalse(alphaRow, alphaRow.contains("seed="));
        assertTrue(alphaRow, alphaRow.contains(" generated=1089 "));
    }

    @Test
    public void seedReportsTheIrisWorldSeedRatherThanTheServerSeed() {
        NativeWorld alpha = level("iris:alpha");
        List<String> output = new ArrayList<>();
        NativeCommandSource source = source(mock(NativeModdedServer.class), alpha, true, output);
        Engine engine = engine(424242L, 1089);
        IrisModdedChunkGenerator generator = generator("overworld:overworld", engine);
        when(generator.commandEngine()).thenReturn(engine);

        try (MockedStatic<NativeWorldGenerators> generators = mockStatic(NativeWorldGenerators.class)) {
            generators.when(() -> NativeWorldGenerators.find(alpha, IrisModdedChunkGenerator.class))
                    .thenReturn(generator);

            assertEquals(1, IrisModdedCommands.seed(source));
        }

        assertTrue(output.toString(), output.contains(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_WORLD_SEED,
                MessageArgument.untrusted("value", 424242L))));
        for (String line : output) {
            assertFalse(line, line.contains(Long.toString(SERVER_SEED)));
        }
    }

    private static NativeWorld level(String name) {
        NativeWorld level = mock(NativeWorld.class);
        when(level.name()).thenReturn(name);
        when(level.seed()).thenReturn(SERVER_SEED);
        return level;
    }

    private static NativeCommandSource source(NativeModdedServer server, NativeWorld world, boolean gamemaster, List<String> output) {
        NativeCommandSource source = mock(NativeCommandSource.class);
        when(source.server()).thenReturn(server);
        when(source.world()).thenReturn(world);
        when(source.isGameMaster()).thenReturn(gamemaster);
        doAnswer((InvocationOnMock invocation) -> {
            Supplier<NativeCommandText> text = invocation.getArgument(0);
            output.add(text.get().getString());
            return null;
        }).when(source).sendSuccess(any(), anyBoolean());
        return source;
    }

    private static IrisModdedChunkGenerator generator(String dimensionKey, Engine engine) {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class);
        when(generator.dimensionKey()).thenReturn(dimensionKey);
        when(generator.engineIfBound()).thenReturn(engine);
        return generator;
    }

    private static Engine engine(long worldSeed, int chunksGenerated) {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getLoadKey()).thenReturn("overworld");
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(new File("overworld"));
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getSeed()).thenReturn(worldSeed);
        IrisEngineData engineData = new IrisEngineData();
        engineData.getStatistics().setChunksGenerated(chunksGenerated);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().name("alpha").seed(worldSeed).minHeight(-64).maxHeight(320).build());
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getMaxHeight()).thenReturn(320);
        when(engine.getEngineData()).thenReturn(engineData);
        when(engine.getGenerated()).thenReturn(0);
        return engine;
    }

    private static String row(List<String> output, String label) {
        for (String line : output) {
            if (line.startsWith(label + ": ")) {
                return line;
            }
        }
        throw new AssertionError("No row labeled '" + label + "' in " + output);
    }
}
