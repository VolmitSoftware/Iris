package art.arcane.iris.modded.command;

import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.history.GenerationSemanticQueries;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureQueries;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedLocateRiverTest {
    private static final int DISTANCE = GenerationSemanticQueries.RIVER_SEARCH_DISTANCE;

    @Test
    public void unknownTypeFailsBeforeSearching() {
        Fixture fixture = new Fixture();
        try (MockedStatic<IrisModdedCommands> commands = fixture.commands()) {
            assertEquals(0, ModdedLocateCommands.gotoRiver(fixture.source, "ocean_trench"));

            commands.verify(() -> IrisModdedCommands.fail(eq(fixture.source), anyString()));
            commands.verify(() -> IrisModdedCommands.ok(eq(fixture.source), anyString()), never());
            verify(fixture.server, never()).execute(any());
            verify(fixture.runtime, never()).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any());
        }
    }

    @Test
    public void missingFeatureReportsNotFound() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.runtime.nearestFeature(anyInt(), anyInt(), any(), anyInt(), any())).thenReturn(Optional.empty());
        try (MockedStatic<IrisModdedCommands> commands = fixture.commands()) {
            assertEquals(1, ModdedLocateCommands.gotoRiver(fixture.source, "river"));
            fixture.finishOnServerThread();

            commands.verify(() -> IrisModdedCommands.fail(fixture.source, "No river found within " + DISTANCE + " blocks."));
            verify(fixture.player, never()).teleport(any(), anyDouble(), anyDouble(), anyDouble());
        }
    }

    @Test
    public void plannerFailureIsReportedAsAnErrorNotAsNotFound() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.runtime.nearestFeature(anyInt(), anyInt(), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("window planner exploded"));
        try (MockedStatic<IrisModdedCommands> commands = fixture.commands()) {
            assertEquals(1, ModdedLocateCommands.gotoRiver(fixture.source, "major_river"));
            fixture.finishOnServerThread();

            commands.verify(() -> IrisModdedCommands.fail(fixture.source,
                    "Could not search for major river: IllegalStateException - window planner exploded"));
            commands.verify(() -> IrisModdedCommands.fail(eq(fixture.source), eq("No major river found within " + DISTANCE + " blocks.")),
                    never());
            verify(fixture.player, never()).teleport(any(), anyDouble(), anyDouble(), anyDouble());
        }
    }

    @Test
    public void foundFeatureTeleportsAboveItFromThePlayersPosition() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.runtime.nearestFeature(anyInt(), anyInt(), any(), anyInt(), any()))
                .thenReturn(Optional.of(new HydrologyFeatureRef(9L, HydrologyFeatureType.WATERFALL, 400, 130, -250)));
        when(fixture.player.teleport(fixture.world, 400.5D, 68D, -249.5D)).thenReturn(true);
        try (MockedStatic<IrisModdedCommands> commands = fixture.commands();
             MockedConstruction<NativeStructureQueries> queries = mockConstruction(NativeStructureQueries.class)) {
            assertEquals(1, ModdedLocateCommands.gotoRiver(fixture.source, "waterfall"));
            fixture.finishOnServerThread();

            ArgumentCaptor<HydrologyFeatureQuery> query = ArgumentCaptor.forClass(HydrologyFeatureQuery.class);
            verify(fixture.runtime).nearestFeature(eq(120), eq(-75), query.capture(), eq(DISTANCE), any());
            assertEquals(HydrologyFeatureQuery.of(HydrologyFeatureType.WATERFALL), query.getValue());
            verify(queries.constructed().getFirst()).loadChunk(400 >> 4, -250 >> 4);
            verify(fixture.player).teleport(fixture.world, 400.5D, 68D, -249.5D);
            commands.verify(() -> IrisModdedCommands.ok(eq(fixture.source), anyString()), times(2));
            commands.verify(() -> IrisModdedCommands.fail(eq(fixture.source), anyString()), never());
        }
    }

    private static final class Fixture {
        private final NativeCommandSource source = mock(NativeCommandSource.class);
        private final NativeWorld world = mock(NativeWorld.class);
        private final NativeProtocolPlayer player = mock(NativeProtocolPlayer.class);
        private final NativeModdedServer server = mock(NativeModdedServer.class);
        private final Engine engine = mock(Engine.class);
        private final HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        private final CompletableFuture<Runnable> completion = new CompletableFuture<>();

        private Fixture() {
            IrisComplex complex = mock(IrisComplex.class);
            IrisDimension dimension = mock(IrisDimension.class);
            when(source.player()).thenReturn(player);
            when(source.world()).thenReturn(world);
            when(source.server()).thenReturn(server);
            when(player.blockX()).thenReturn(120);
            when(player.blockZ()).thenReturn(-75);
            when(player.connected()).thenReturn(true);
            when(player.inWorld(world)).thenReturn(true);
            when(world.minHeight()).thenReturn(-64);
            when(world.maxHeight()).thenReturn(320);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getDimension()).thenReturn(dimension);
            when(dimension.getMinHeight()).thenReturn(-64);
            when(complex.getHydrologyRuntime()).thenReturn(runtime);
            when(runtime.enabled()).thenReturn(true);
            doAnswer(invocation -> {
                completion.complete(invocation.getArgument(0));
                return null;
            }).when(server).execute(any());
        }

        private MockedStatic<IrisModdedCommands> commands() {
            MockedStatic<IrisModdedCommands> commands = mockStatic(IrisModdedCommands.class);
            commands.when(() -> IrisModdedCommands.engineFor(world)).thenReturn(engine);
            return commands;
        }

        private void finishOnServerThread() throws Exception {
            completion.get(10L, TimeUnit.SECONDS).run();
        }
    }
}
