package art.arcane.iris.engine.history;

import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.GenerationSessionManager;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.spi.PlatformBlockState;
import org.junit.BeforeClass;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

abstract class GenerationHistoryRuntimeRouterSupport extends GenerationHistorySupport {
    @BeforeClass
    public static void initializeMantleBlockState() throws Exception {
        PlatformBlockState air = mock(PlatformBlockState.class);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("AIR")).thenReturn(air);
            Class.forName(EngineMantle.class.getName());
        }
    }

    static void routeUntilClosed(GenerationHistoryRuntimeRouter router, CountDownLatch routing) {
        boolean started = false;
        while (!Thread.currentThread().isInterrupted()) {
            try (GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(0, 0)) {
                assertEquals(1L, route.activation().activationId());
                if (!started) {
                    started = true;
                    routing.countDown();
                }
            } catch (IOException failure) {
                throw new AssertionError(failure);
            } catch (IllegalStateException closed) {
                assertEquals("Generation-history runtime router is closed.", closed.getMessage());
                return;
            }
        }
    }

    AtomicReference<IrisEngine.GenerationRuntimeBinding> installScopeTracking(IrisEngine engine) {
        AtomicReference<IrisEngine.GenerationRuntimeBinding> scoped = new AtomicReference<>();
        when(engine.openGenerationRuntimeScope(any())).thenAnswer(invocation -> {
            IrisEngine.GenerationRuntimeBinding binding = invocation.getArgument(0);
            scoped.set(binding);
            IrisEngine.GenerationRuntimeScope scope = mock(IrisEngine.GenerationRuntimeScope.class);
            doAnswer(ignored -> {
                scoped.compareAndSet(binding, null);
                return null;
            }).when(scope).close();
            return scope;
        });
        return scoped;
    }

    CompletableFuture<GenerationHistoryRuntimeRouter.RuntimeRoute> openRouteAsync(
            GenerationHistoryRuntimeRouter router,
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            int chunkX,
            int chunkZ
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent route acquisition.");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted before concurrent route acquisition.", interrupted);
            }
            return openRouteUnchecked(router, chunkX, chunkZ);
        }, executor);
    }

    static GenerationHistoryRuntimeRouter.RuntimeRoute openRouteUnchecked(
            GenerationHistoryRuntimeRouter router,
            int chunkX,
            int chunkZ
    ) {
        try {
            return router.openRoute(chunkX, chunkZ);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static void awaitClosed(GenerationHistoryRuntimeRouter router) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!router.isClosed() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(router.isClosed());
    }

    GenerationHistory createThreeActivationHistory(Path world, String prefix) throws IOException {
        Path packA = createPack(prefix + "-a", "alpha");
        Path packB = createPack(prefix + "-b", "beta");
        Path packC = createPack(prefix + "-c", "gamma");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{0, 0}});
        stage(history, packB);
        promoteWithSignatures(history);
        writeRegion(region, new int[][]{{0, 0}, {1, 0}});
        stage(history, packC);
        promoteWithSignatures(history);
        return history;
    }

    static GenerationHistory createHistory(
            Path world,
            Path pack,
            GenerationKernelRegistry.Version version,
            GenerationKernelRegistry kernels
    ) throws IOException {
        return GenerationHistory.create(
                world,
                pack,
                GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION),
                42L,
                contract(),
                GenerationRegistryContract.empty(),
                version,
                kernels
        );
    }

    static GenerationKernelRegistry kernels(GenerationKernelRegistry.Version current) {
        return new GenerationKernelRegistry(
                current,
                Set.of(
                        new GenerationKernelRegistry.Kernel(
                                1,
                                "1".repeat(64),
                                Map.of(
                                        new GenerationKernelRegistry.AlgorithmVersion(1, 1),
                                        (engine, transitionPlan) -> {
                                            throw new AssertionError("Mock runtime factory owns this test.");
                                        }
                                )
                        ),
                        new GenerationKernelRegistry.Kernel(
                                2,
                                "2".repeat(64),
                                Map.of(
                                        new GenerationKernelRegistry.AlgorithmVersion(1, 1),
                                        (engine, transitionPlan) -> {
                                            throw new AssertionError("Mock runtime factory owns this test.");
                                        }
                                )
                        )
                )
        );
    }

    static GenerationActivation promoteWithSignatures(GenerationHistory history) throws IOException {
        return history.promotePending(boundary -> GenerationHistorySupport::signature);
    }

    static final class FakeRuntimeFactory
            implements GenerationHistoryRuntimeRouter.ActivationRuntimeFactory {
        final Map<Long, IrisEngine.GenerationRuntimeBinding> bindings = new HashMap<>();
        final Map<Long, Path> mantles = new HashMap<>();
        final Map<Long, AtomicInteger> loadCounts = new HashMap<>();
        GenerationKernelRegistry.Version loadedVersion;
        long blockedActivation = -1L;
        CountDownLatch loadStarted = new CountDownLatch(0);
        CountDownLatch releaseLoad = new CountDownLatch(0);

        @Override
        public void validateBase(
                IrisEngine engine,
                GenerationHistory history,
                GenerationActivation activation,
                GenerationEpoch epoch,
                IrisEngine.GenerationRuntimeBinding binding
        ) {
            if (engine.getGenerationSessions() == null) {
                when(engine.getGenerationSessions()).thenReturn(new GenerationSessionManager(true));
            }
        }

        @Override
        public IrisEngine.GenerationRuntimeBinding load(
                IrisEngine engine,
                GenerationHistory history,
                GenerationActivation activation,
                GenerationEpoch epoch
        ) throws IOException {
            if (!epoch.kernelVersion().equals(history.currentKernelVersion())
                    || !history.usesCurrentGenerator()) {
                throw new AssertionError("Archived generator implementations must never load.");
            }
            synchronized (loadCounts) {
                loadCounts.computeIfAbsent(activation.activationId(), ignored -> new AtomicInteger()).incrementAndGet();
            }
            if (activation.activationId() == blockedActivation) {
                loadStarted.countDown();
                try {
                    if (!releaseLoad.await(5L, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to release the blocked runtime load.");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while loading a test runtime.", interrupted);
                }
            }
            GenerationKernelRegistry.Version version = loadedVersion == null
                    ? epoch.kernelVersion()
                    : loadedVersion;
            return binding(history, activation, version);
        }

        void blockLoad(long activationId) {
            blockedActivation = activationId;
            loadStarted = new CountDownLatch(1);
            releaseLoad = new CountDownLatch(1);
        }

        int loadCount(long activationId) {
            synchronized (loadCounts) {
                AtomicInteger count = loadCounts.get(activationId);
                return count == null ? 0 : count.get();
            }
        }

        IrisEngine.GenerationRuntimeBinding binding(
                GenerationHistory history,
                GenerationActivation activation
        ) throws IOException {
            return binding(history, activation, requireEpoch(history, activation).kernelVersion());
        }

        IrisEngine.GenerationRuntimeBinding binding(
                GenerationHistory history,
                GenerationActivation activation,
                GenerationKernelRegistry.Version version
        ) throws IOException {
            GenerationEpoch epoch = requireEpoch(history, activation);
            IrisEngine.GenerationRuntimeBinding binding = mock(IrisEngine.GenerationRuntimeBinding.class);
            Path mantle = history.paths().activationMantleRoot(activation.activationId());
            TransitionGenerationPlan plan = activation.isInitial()
                    ? null
                    : history.transitionPlan(activation.activationId());
            when(binding.kernelVersion()).thenReturn(version);
            when(binding.runtimeKernel()).thenReturn(new GenerationKernelRegistry.RuntimeKernel(
                    version,
                    epoch.kernelImplementationFingerprint(),
                    (engine, transitionPlan) -> mock(IrisComplex.class)
            ));
            when(binding.mantleStorageDirectory()).thenReturn(mantle);
            when(binding.transitionPlan()).thenReturn(plan);
            when(binding.runtimeId()).thenReturn(Math.toIntExact(activation.activationId()));
            bindings.put(activation.activationId(), binding);
            mantles.put(activation.activationId(), mantle);
            return binding;
        }

        static GenerationEpoch requireEpoch(
                GenerationHistory history,
                GenerationActivation activation
        ) {
            return history.manifest().epoch(activation.epochId()).orElseThrow();
        }
    }
}
