package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.locator.BiomeLocator;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.subterrain.SubterrainPosition;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.SavedBiomeRuntime;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class EngineBukkitOpsCaveDestinationTest {
    @Test
    public void landingWaitsForChunkLoadThenRunsOnDestinationRegion() throws Exception {
        World world = mock(World.class);
        NativeWorld nativeWorld = mock(NativeWorld.class);
        Engine engine = mock(Engine.class);
        GenerationSessionLease lease = mock(GenerationSessionLease.class);
        when(engine.acquireGenerationLease("bukkit_cave_landing")).thenReturn(lease);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().platformWorld(nativeWorld).build());
        BiomeLocator locator = mock(BiomeLocator.class);
        SubterrainPosition target = new SubterrainPosition(-25, -40, 35);
        SubterrainPosition landing = new SubterrainPosition(-24, -42, 36);
        SavedBiomeRuntime.ReadSession readSession = mock(SavedBiomeRuntime.ReadSession.class);
        CompletableFuture<Optional<SavedBiomeRuntime.ReadSession>> prepared = new CompletableFuture<>();
        when(locator.prepareRead(engine, new Position2(-2, 2))).thenReturn(prepared);
        when(locator.landing(engine, nativeWorld, target, Optional.of(readSession))).thenReturn(landing);
        CompletableFuture<Chunk> loaded = new CompletableFuture<>();
        AtomicReference<Runnable> regionTask = new AtomicReference<>();
        try (MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class);
             MockedStatic<J> scheduler = mockStatic(J.class);
             MockedStatic<IrisContext> context = mockStatic(IrisContext.class)) {
            platform.when(() -> BukkitPlatform.chunkAtAsync(world, -2, 2, true)).thenReturn(loaded);
            scheduler.when(() -> J.runRegion(eq(world), eq(-2), eq(2), any(Runnable.class))).thenAnswer(call -> {
                regionTask.set(call.getArgument(3));
                return true;
            });
            context.when(() -> IrisContext.open(eq(engine), anyLong(), any())).thenReturn(mock(IrisContext.Scope.class));
            CompletableFuture<Location> result = EngineBukkitOps.caveDestination(engine, locator, world, target);
            assertFalse(result.isDone());
            verifyNoInteractions(locator);
            loaded.complete(mock(Chunk.class));
            assertFalse(result.isDone());
            assertNull(regionTask.get());
            prepared.complete(Optional.of(readSession));
            assertFalse(result.isDone());
            regionTask.get().run();
            assertTrue(result.isDone());
            assertEquals(-23.5, result.get().getX(), 0);
            assertEquals(-42, result.get().getY(), 0);
            assertEquals(36.5, result.get().getZ(), 0);
            verify(locator).landing(engine, nativeWorld, target, Optional.of(readSession));
        }
    }
}
