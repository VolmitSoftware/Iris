package art.arcane.iris.modded.command;

import art.arcane.iris.modded.MissingPackDimensionException;
import art.arcane.iris.modded.ModdedDimensionCreations;
import art.arcane.iris.pack.BrokenPackException;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

public class ModdedWorldCreateCommandTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void createAcknowledgesAtOnceAndReportsTheLiveWorldFromALaterServerTask() throws Exception {
        CompletableFuture<Void> creation = new CompletableFuture<>();
        Harness harness = harness();

        try (MockedStatic<ModdedPackCommands> packs = mockStatic(ModdedPackCommands.class);
             MockedStatic<ModdedDimensionCreations> creations = mockStatic(ModdedDimensionCreations.class)) {
            packs.when(ModdedPackCommands::packsRoot).thenReturn(harness.packs());
            creations.when(() -> ModdedDimensionCreations.create(harness.server(), "irisworldgen:moon", "overworld", "overworld", 7L))
                    .thenReturn(creation);

            assertEquals(1, ModdedWorldCommands.createWorld(harness.source(), "moon", "overworld", 7L));
            assertTrue(String.join("\n", harness.success()), harness.success().stream()
                    .anyMatch((String line) -> line.contains("irisworldgen:moon") && !line.startsWith("Created")));
            assertFalse(harness.success().stream().anyMatch((String line) -> line.startsWith("Created Iris world")));

            creation.complete(null);
            harness.runServerTasks();
        }

        assertTrue(String.join("\n", harness.success()), harness.success().stream()
                .anyMatch((String line) -> line.startsWith("Created Iris world irisworldgen:moon from pack 'overworld'")));
        assertTrue(harness.failure().isEmpty());
    }

    @Test
    public void creationFailureIsReportedAsAFailedInjection() throws Exception {
        Harness harness = harness();
        List<String> failures = run(harness, new IllegalStateException("Iris generation history is unusable for 'irisworldgen:moon'."));

        assertTrue(String.join("\n", failures), failures.stream().anyMatch((String line) ->
                line.startsWith("Failed to inject Iris world 'irisworldgen:moon'") && line.contains("generation history is unusable")));
    }

    @Test
    public void brokenPackIsReportedWithItsValidationReasons() throws Exception {
        Harness harness = harness();
        List<String> failures = run(harness, new BrokenPackException("overworld", List.of("biomes/plains.json is invalid")));

        assertTrue(String.join("\n", failures), failures.stream().anyMatch((String line) -> line.contains("irisworldgen:moon")
                && line.contains("overworld")));
        assertTrue(String.join("\n", failures), failures.stream().anyMatch((String line) -> line.contains("biomes/plains.json is invalid")));
    }

    @Test
    public void missingPackDimensionIsReportedAsAMissingDimensionFile() throws Exception {
        Harness harness = harness();
        List<String> failures = run(harness, new MissingPackDimensionException("overworld", "overworld"));

        assertTrue(String.join("\n", failures), failures.stream().anyMatch((String line) ->
                line.contains("dimensions/overworld.json")));
    }

    private List<String> run(Harness harness, RuntimeException failure) throws Exception {
        CompletableFuture<Void> creation = new CompletableFuture<>();
        try (MockedStatic<ModdedPackCommands> packs = mockStatic(ModdedPackCommands.class);
             MockedStatic<ModdedDimensionCreations> creations = mockStatic(ModdedDimensionCreations.class)) {
            packs.when(ModdedPackCommands::packsRoot).thenReturn(harness.packs());
            creations.when(() -> ModdedDimensionCreations.create(harness.server(), "irisworldgen:moon", "overworld", "overworld", 7L))
                    .thenReturn(creation);

            assertEquals(1, ModdedWorldCommands.createWorld(harness.source(), "moon", "overworld", 7L));
            assertTrue(harness.failure().isEmpty());
            creation.completeExceptionally(failure);
            harness.runServerTasks();
        }
        return harness.failure();
    }

    private Harness harness() throws Exception {
        File packs = temporaryFolder.newFolder("packs");
        assertTrue(new File(packs, "overworld").mkdirs());
        NativeModdedServer server = mock(NativeModdedServer.class);
        List<Runnable> serverTasks = new ArrayList<>();
        doAnswer((InvocationOnMock invocation) -> {
            serverTasks.add(invocation.getArgument(0));
            return null;
        }).when(server).execute(any());
        List<String> success = new ArrayList<>();
        List<String> failure = new ArrayList<>();
        NativeCommandSource source = mock(NativeCommandSource.class);
        when(source.server()).thenReturn(server);
        when(source.isGameMaster()).thenReturn(true);
        doAnswer((InvocationOnMock invocation) -> {
            Supplier<NativeCommandText> text = invocation.getArgument(0);
            success.add(text.get().getString());
            return null;
        }).when(source).sendSuccess(any(), anyBoolean());
        doAnswer((InvocationOnMock invocation) -> {
            NativeCommandText text = invocation.getArgument(0);
            failure.add(text.getString());
            return null;
        }).when(source).sendFailure(any());
        return new Harness(packs, server, source, serverTasks, success, failure);
    }

    private record Harness(
            File packs,
            NativeModdedServer server,
            NativeCommandSource source,
            List<Runnable> serverTasks,
            List<String> success,
            List<String> failure
    ) {
        void runServerTasks() {
            while (!serverTasks.isEmpty()) {
                serverTasks.removeFirst().run();
            }
        }
    }
}
