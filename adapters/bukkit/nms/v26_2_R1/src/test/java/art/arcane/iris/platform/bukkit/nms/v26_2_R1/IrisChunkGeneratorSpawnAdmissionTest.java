package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.GenerationSessionManager;
import art.arcane.iris.generation.runtime.GenerationTransitionGate;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.NativeBiomeSpawnSelection;
import art.arcane.iris.world.history.SavedBiomeRuntime;
import art.arcane.iris.generation.context.IrisContext;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisChunkGeneratorSpawnAdmissionTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void tickSpawnQueryLeavesOwnerThreadAvailableForBoundaryCheckpoint() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService owner = Executors.newSingleThreadExecutor();
        ExecutorService reloader = Executors.newSingleThreadExecutor();
        AtomicInteger snapshots = new AtomicInteger();
        long previousSession = fixture.sessions.currentSessionId();
        try {
            assertSame(fixture.expected, owner.submit(fixture::query).get(2L, TimeUnit.SECONDS));
            assertEquals(previousSession, fixture.readSession.get());
            CompletableFuture<Void> cutover = CompletableFuture.runAsync(() -> {
                try (GenerationTransitionGate.Transition ignored = fixture.sessions.transitionGate().beginTransition(2_000L)) {
                    fixture.sessions.sealAndAwait("Studio generation cutover", 2_000L);
                    Future<?> query = owner.submit(() -> {
                        assertTrue(fixture.query().isEmpty());
                        assertNull(IrisContext.get());
                    });
                    owner.submit(snapshots::incrementAndGet).get(2L, TimeUnit.SECONDS);
                    query.get(2L, TimeUnit.SECONDS);
                    assertEquals(1, fixture.savedReads.get());
                    assertEquals(1, fixture.routes.get());
                    fixture.sessions.activateNextSession();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }, reloader);

            cutover.get(3L, TimeUnit.SECONDS);
            assertEquals(1, snapshots.get());
            assertEquals(0, fixture.sessions.activeLeases());
            assertSame(fixture.expected, owner.submit(fixture::query).get(2L, TimeUnit.SECONDS));
            assertEquals(fixture.sessions.currentSessionId(), fixture.readSession.get());
            assertTrue(fixture.readSession.get() > previousSession);
            assertEquals(2, fixture.savedReads.get());
            assertEquals(2, fixture.routes.get());
            assertEquals(0, fixture.sessions.activeLeases());
        } finally {
            owner.shutdownNow();
            reloader.shutdownNow();
            assertTrue(owner.awaitTermination(2L, TimeUnit.SECONDS));
            assertTrue(reloader.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    private static void setField(IrisChunkGenerator generator, String name, Object value) throws Exception {
        Field field = IrisChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }

    private static final class Fixture {
        private final GenerationSessionManager sessions = new GenerationSessionManager(true);
        private final IrisEngine engine = mock(IrisEngine.class);
        private final IrisChunkGenerator generator = mock(IrisChunkGenerator.class, CALLS_REAL_METHODS);
        private final Holder<Biome> biome = Holder.direct(mock(Biome.class));
        private final StructureManager structures = mock(StructureManager.class);
        private final WeightedList<MobSpawnSettings.SpawnerData> expected = mock(WeightedList.class);
        private final AtomicInteger savedReads = new AtomicInteger();
        private final AtomicInteger routes = new AtomicInteger();
        private final AtomicLong readSession = new AtomicLong();

        private Fixture() throws Exception {
            GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
            SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
            ChunkGenerator delegate = mock(ChunkGenerator.class);
            CustomBiomeSource source = mock(CustomBiomeSource.class);
            when(engine.getGenerationSessions()).thenReturn(sessions);
            when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
            when(router.biomes()).thenReturn(saved);
            when(saved.nativeSpawnSelection(0, 64, 0, "")).thenAnswer(invocation -> {
                assertAdmitted();
                savedReads.incrementAndGet();
                return new NativeBiomeSpawnSelection(NativeBiomeSpawnSelection.Mode.RETAINED, "minecraft:plains");
            });
            when(engine.openGenerationHistoryCoordinateScope(0, 0)).thenAnswer(invocation -> {
                assertAdmitted();
                routes.incrementAndGet();
                return null;
            });
            when(delegate.getMobsAt(biome, structures, MobCategory.MONSTER, new BlockPos(0, 64, 0)))
                    .thenReturn(expected);
            setField(generator, "engine", engine);
            setField(generator, "delegate", delegate);
            setField(generator, "customBiomeSource", source);
        }

        private WeightedList<MobSpawnSettings.SpawnerData> query() {
            return generator.getMobsAt(biome, structures, MobCategory.MONSTER, new BlockPos(0, 64, 0));
        }

        private void assertAdmitted() {
            assertEquals(1, sessions.activeLeases());
            assertSame(engine, IrisContext.get().getEngine());
            readSession.set(IrisContext.get().getGenerationSessionId());
        }
    }
}
