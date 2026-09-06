package art.arcane.iris.engine.mantle;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EnginePlatformHooks;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface MatterGenerator {
    MultiBurst DISPATCHER = MultiBurst.burst;
    ConcurrentHashMap<MatterTaskKey, MatterComponentTask> IN_FLIGHT_COMPONENTS = new ConcurrentHashMap<>();
    long COMPONENT_TASK_POLL_MS = 1000L;
    long COMPONENT_TASK_TIMEOUT_MS = Long.getLong("iris.mantle.componentTimeout", 120000L);

    Engine getEngine();

    Mantle<Matter> getMantle();

    int getRadius();

    int getRealRadius();

    List<MantlePass> getComponents();

    @ChunkCoordinates
    default void generateMatter(int x, int z, boolean multicore, ChunkContext context) {
        generateMatterPhase(x, z, multicore, context, MatterGenerationPhase.ALL);
    }

    @ChunkCoordinates
    default void generateTerrainMatter(int x, int z, boolean multicore, ChunkContext context) {
        generateMatterPhase(x, z, multicore, context, MatterGenerationPhase.TERRAIN);
    }

    @ChunkCoordinates
    default void generateContentMatter(int x, int z, boolean multicore, ChunkContext context) {
        generateMatterPhase(x, z, multicore, context, MatterGenerationPhase.CONTENT);
    }

    private void generateMatterPhase(int x, int z, boolean multicore, ChunkContext context,
                                     MatterGenerationPhase phase) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }
        IrisComplex complex = context == null ? null : context.getComplex();
        if (complex == null) {
            complex = getEngine().getComplex();
        }
        if (complex != null && !complex.allowsMantleChunkWrite(x, z)) {
            return;
        }
        generateMatterWindow(x, z, multicore, context, complex, phase);
    }

    private void generateMatterWindow(
            int x,
            int z,
            boolean multicore,
            ChunkContext context,
            IrisComplex complex,
            MatterGenerationPhase phase
    ) {

        MatterGenerationPlan generationPlan = resolveGenerationPlan(x, z, context, phase);
        MatterPassPlan[] passPlans = generationPlan.passPlans();
        if (passPlans.length == 0) {
            return;
        }
        int prefetchRadius = passPlans[0].passChunkRadius();
        LongOpenHashSet partialChunks = new LongOpenHashSet();
        List<MantleComponent> requiredComponents = enabledComponents();
        List<MantleFlag> terrainFlags = terrainFlags(requiredComponents);

        try (MantleWriter writer = new MantleWriter(
                getEngine().getMantle(),
                getMantle(),
                x,
                z,
                prefetchRadius,
                generationPlan.writerAccessRadius(),
                multicore)) {
            // Every task launched below writes through this writer. They must all be finished before
            // close() releases the cached chunks, even when a pass throws, or detached pool threads
            // write into released chunks.
            List<MatterComponentTask> outstandingTasks = null;

            try {
                for (MatterPassPlan passPlan : passPlans) {
                    MantlePass pass = passPlan.pass();
                    int passRadius = passPlan.passChunkRadius();
                    List<MantleComponent> passComponents = pass.components();
                    MantleComponent[] enabledComponents = new MantleComponent[passComponents.size()];
                    int[] componentPassRadii = new int[passComponents.size()];
                    int enabledComponentCount = 0;
                    for (MantleComponent component : passComponents) {
                        if (phase.includes(component) && shouldGenerateComponent(component)) {
                            // A component must cover its own reach plus every later pass' reach, or a
                            // later pass reads this component's data from chunks it never wrote.
                            int componentReach = component.getOutputRadius() + passPlan.downstreamBlockRadius();
                            componentPassRadii[enabledComponentCount] = componentReach > 0 ? Math.ceilDiv(componentReach, 16) : 0;
                            enabledComponents[enabledComponentCount++] = component;
                        }
                    }

                    if (enabledComponentCount == 0) {
                        continue;
                    }

                    // Every multicore generation claims its components through the in-flight map.
                    // A caller outside the pool spreads its window across the pool; a pool thread
                    // runs its claims inline. Either way a component another generation already
                    // owns is joined at the pass barrier instead of blocking on that chunk's lock
                    // before the caller's own remaining chunks are done.
                    boolean asyncComponents = multicore;
                    if (asyncComponents && outstandingTasks == null) {
                        outstandingTasks = new ArrayList<>();
                    }
                    MantleComponent[] eligibleComponents = new MantleComponent[enabledComponentCount];

                    for (int i = -passRadius; i <= passRadius; i++) {
                        int absI = Math.abs(i);
                        for (int j = -passRadius; j <= passRadius; j++) {
                            int absJ = Math.abs(j);
                            int passX = x + i;
                            int passZ = z + j;
                            long passKey = chunkKey(passX, passZ);
                            boolean partial = false;
                            boolean anyComponentInRadius = false;

                            for (int componentIndex = 0; componentIndex < enabledComponentCount; componentIndex++) {
                                int componentPassRadius = componentPassRadii[componentIndex];
                                if (absI > componentPassRadius || absJ > componentPassRadius) {
                                    partial = true;
                                } else {
                                    anyComponentInRadius = true;
                                }
                            }

                            if (!anyComponentInRadius) {
                                partialChunks.add(passKey);
                                continue;
                            }

                            if (complex != null && !complex.allowsMantleChunkWrite(passX, passZ)) {
                                partialChunks.add(passKey);
                                continue;
                            }

                            if (partial) {
                                partialChunks.add(passKey);
                            }

                            MantleChunk<Matter> chunk = writer.acquireChunk(passX, passZ);
                            if (chunk == null) {
                                throw new IllegalStateException("Mantle pass chunk " + passX + "," + passZ
                                        + " is outside the writer prepared at " + x + "," + z
                                        + " with radius " + generationPlan.writerAccessRadius());
                            }

                            if (chunk.isFlagged(MantleFlag.PLANNED)) {
                                continue;
                            }

                            if (phase == MatterGenerationPhase.CONTENT) {
                                requireTerrainPhase(chunk, passX, passZ, terrainFlags);
                            }
                            int eligibleComponentCount = 0;
                            for (int componentIndex = 0; componentIndex < enabledComponentCount; componentIndex++) {
                                MantleComponent component = enabledComponents[componentIndex];
                                int componentPassRadius = componentPassRadii[componentIndex];
                                if (absI > componentPassRadius || absJ > componentPassRadius) {
                                    continue;
                                }

                                if (chunk.isFlagged(component.getFlag())) {
                                    continue;
                                }

                                MantleFlag[] prerequisites = component.getPrerequisiteFlags();
                                if (prerequisites.length > 0) {
                                    boolean prerequisitesMet = true;
                                    for (MantleFlag prereq : prerequisites) {
                                        if (!chunk.isFlagged(prereq)) {
                                            prerequisitesMet = false;
                                            break;
                                        }
                                    }
                                    if (!prerequisitesMet) {
                                        partialChunks.add(passKey);
                                        continue;
                                    }
                                }

                                eligibleComponents[eligibleComponentCount++] = component;
                            }

                            if (eligibleComponentCount == 0) {
                                continue;
                            }

                            int finalPassX = passX;
                            int finalPassZ = passZ;
                            if (asyncComponents) {
                                for (int componentIndex = 0; componentIndex < eligibleComponentCount; componentIndex++) {
                                    MantleComponent component = eligibleComponents[componentIndex];
                                    outstandingTasks.add(runComponentAsync(chunk, component, writer, finalPassX, finalPassZ, context));
                                }
                            } else {
                                for (int componentIndex = 0; componentIndex < eligibleComponentCount; componentIndex++) {
                                    MantleComponent component = eligibleComponents[componentIndex];
                                    runComponentInline(chunk, component, writer, finalPassX, finalPassZ, context);
                                }
                            }
                        }
                    }

                    if (asyncComponents) {
                        awaitComponentTasks(outstandingTasks);
                    }
                }

                if (phase == MatterGenerationPhase.TERRAIN) {
                    return;
                }
                int realRadius = passPlans[passPlans.length - 1].passChunkRadius();
                for (int i = -realRadius; i <= realRadius; i++) {
                    for (int j = -realRadius; j <= realRadius; j++) {
                        int realX = x + i;
                        int realZ = z + j;
                        long realKey = chunkKey(realX, realZ);
                        if (partialChunks.contains(realKey)) {
                            continue;
                        }
                        if (complex != null && !complex.allowsMantleChunkWrite(realX, realZ)) {
                            continue;
                        }
                        MantleChunk<Matter> chunk = writer.acquireChunk(realX, realZ);
                        if (hasCompletedComponents(chunk, requiredComponents)) {
                            chunk.flag(MantleFlag.PLANNED, true);
                        }
                    }
                }
            } finally {
                abandonComponentTasks(outstandingTasks);
            }
        }
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private MatterGenerationPlan resolveGenerationPlan(int x, int z, ChunkContext context, MatterGenerationPhase phase) {
        List<MantlePass> passes = getComponents();
        MatterPassPlan[] plans = new MatterPassPlan[passes.size()];
        int accessDownstreamBlockRadius = 0;
        int generationDownstreamBlockRadius = 0;
        for (int passIndex = passes.size() - 1; passIndex >= 0; passIndex--) {
            MantlePass pass = passes.get(passIndex);
            int passBlockRadius = 0;
            int passAccessInputRadius = 0;
            int passGenerationInputRadius = 0;
            for (MantleComponent component : pass.components()) {
                if (!shouldGenerateComponent(component)) {
                    continue;
                }
                int componentOutputRadius = component.getOutputRadius();
                passBlockRadius = Math.max(passBlockRadius, componentOutputRadius);
                int componentReach = componentOutputRadius + generationDownstreamBlockRadius;
                int invocationChunkRadius = componentReach > 0
                        ? Math.ceilDiv(componentReach, 16)
                        : 0;
                int componentInputRadius = component.getInputRadius(x, z, invocationChunkRadius, context);
                passAccessInputRadius = Math.max(passAccessInputRadius, componentInputRadius);
                if (!component.isInputGenerationLazy()) {
                    passGenerationInputRadius = Math.max(passGenerationInputRadius, componentInputRadius);
                }
            }
            int accessInvocationRadius = accessDownstreamBlockRadius + passBlockRadius;
            int generationInvocationRadius = generationDownstreamBlockRadius + passBlockRadius;
            int passChunkRadius = generationInvocationRadius > 0 ? Math.ceilDiv(generationInvocationRadius, 16) : 0;
            plans[passIndex] = new MatterPassPlan(pass, passChunkRadius, generationDownstreamBlockRadius);
            accessDownstreamBlockRadius = accessInvocationRadius + passAccessInputRadius;
            generationDownstreamBlockRadius = generationInvocationRadius + passGenerationInputRadius;
        }
        int writerChunkRadius = accessDownstreamBlockRadius > 0
                ? Math.ceilDiv(accessDownstreamBlockRadius, 16)
                : 0;
        return new MatterGenerationPlan(plans, writerChunkRadius * 2, phase);
    }

    private List<MantleComponent> enabledComponents() {
        List<MantleComponent> components = new ArrayList<>();
        for (MantlePass pass : getComponents()) {
            for (MantleComponent component : pass.components()) {
                if (shouldGenerateComponent(component)) {
                    components.add(component);
                }
            }
        }
        return List.copyOf(components);
    }

    private static List<MantleFlag> terrainFlags(List<MantleComponent> components) {
        List<MantleFlag> flags = new ArrayList<>();
        for (MantleComponent component : components) {
            if (component.getGenerationPhase() == MatterGenerationPhase.TERRAIN) {
                flags.add(component.getFlag());
            }
        }
        return List.copyOf(flags);
    }

    private static void requireTerrainPhase(MantleChunk<Matter> chunk, int chunkX, int chunkZ,
                                            List<MantleFlag> flags) {
        for (MantleFlag flag : flags) {
            if (!chunk.isFlagged(flag)) {
                throw new IllegalStateException("Content generation at " + chunkX + "," + chunkZ
                        + " requires completed terrain component " + flag.name());
            }
        }
    }

    private static boolean hasCompletedComponents(MantleChunk<Matter> chunk, List<MantleComponent> components) {
        for (MantleComponent component : components) {
            if (!chunk.isFlagged(component.getFlag())) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldGenerateComponent(MantleComponent component) {
        if (!component.isEnabled()) {
            return false;
        }
        Engine engine = getEngine();
        EnginePlatformHooks hooks = engine.getPlatformHooks();
        return hooks == null || hooks.shouldGenerateMantleComponent(engine, component);
    }

    private MatterComponentTask runComponentAsync(
            MantleChunk<Matter> chunk,
            MantleComponent component,
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            ChunkContext context
    ) {
        MantleFlag flag = component.getFlag();
        MatterTaskKey key = new MatterTaskKey(getMantle(), chunkX, chunkZ, flag);
        MatterComponentTask task = new MatterComponentTask(key);
        if (chunk.isFlagged(flag)) {
            task.finish(null);
            return task;
        }

        MatterComponentTask existing = IN_FLIGHT_COMPONENTS.putIfAbsent(key, task);
        if (existing != null) {
            return existing;
        }

        try {
            if (DISPATCHER.ownsCurrentThread()) {
                // A pool thread runs its claim inline: a task queued behind pool threads that block
                // on unmanaged waits (structure builds, cache locks) could starve the whole pool.
                completeComponentTask(task, chunk, component, writer, chunkX, chunkZ, context, IrisContext.get());
                return task;
            }
            IrisContext callerContext = IrisContext.get();
            task.setSubmission(DISPATCHER.submit(() -> completeComponentTask(
                    task,
                    chunk,
                    component,
                    writer,
                    chunkX,
                    chunkZ,
                    context,
                    callerContext)));
            return task;
        } catch (Throwable throwable) {
            task.cancel(throwable);
            throw throwable;
        }
    }

    /**
     * Pass barrier. Waits for every launched task, even if one of them failed, so that no task is
     * still writing through the writer once this returns. The first failure is rethrown.
     */
    private static void awaitComponentTasks(List<MatterComponentTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }

        Throwable failure = null;
        for (MatterComponentTask task : tasks) {
            try {
                awaitComponentTask(task);
            } catch (Throwable throwable) {
                if (failure == null) {
                    failure = throwable;
                } else if (failure != throwable) {
                    failure.addSuppressed(throwable);
                }
            }
        }
        tasks.clear();

        if (failure != null) {
            throw failure instanceof RuntimeException runtime ? runtime : new CompletionException(failure);
        }
    }

    /**
     * Drains tasks that never reached their pass barrier because the pass threw. The generation
     * attempt is already failing, so the wait itself is the point and failures are dropped rather
     * than masking the original throwable.
     */
    private static void abandonComponentTasks(List<MatterComponentTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        try {
            awaitComponentTasks(tasks);
        } catch (Throwable ignored) {
        }
    }

    private static void awaitComponentTask(MatterComponentTask task) {
        CompletableFuture<Void> future = task.future;
        if (future.isDone() && !future.isCompletedExceptionally()) {
            return;
        }
        if (DISPATCHER.ownsCurrentThread()) {
            // A pool thread parked on another generation's task must not starve the pool that has
            // to run that task: managed blocking lets the pool compensate with another worker.
            try {
                ForkJoinPool.managedBlock(new ComponentTaskBlocker(task));
            } catch (InterruptedException interruption) {
                try {
                    task.await(COMPONENT_TASK_TIMEOUT_MS);
                } finally {
                    Thread.currentThread().interrupt();
                }
            } catch (RejectedExecutionException exhaustedPool) {
                task.await(COMPONENT_TASK_TIMEOUT_MS);
            }
            return;
        }
        task.await(COMPONENT_TASK_TIMEOUT_MS);
    }

    /**
     * Blocks a pool thread on a component task while letting its pool spawn a replacement worker.
     */
    final class ComponentTaskBlocker implements ForkJoinPool.ManagedBlocker {
        private final MatterComponentTask task;

        private ComponentTaskBlocker(MatterComponentTask task) {
            this.task = task;
        }

        @Override
        public boolean block() {
            task.await(COMPONENT_TASK_TIMEOUT_MS);
            return true;
        }

        @Override
        public boolean isReleasable() {
            if (!task.future.isDone()) {
                return false;
            }
            task.future.join();
            return true;
        }
    }

    private void completeComponentTask(
            MatterComponentTask task,
            MantleChunk<Matter> chunk,
            MantleComponent component,
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            ChunkContext context,
            IrisContext callerContext
    ) {
        if (!task.start()) {
            return;
        }
        Throwable failure = null;
        try {
            runComponentWithContext(chunk, component, writer, chunkX, chunkZ, context, callerContext);
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            task.finish(failure);
        }
    }

    private void runComponentWithContext(
            MantleChunk<Matter> chunk,
            MantleComponent component,
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            ChunkContext context,
            IrisContext callerContext
    ) {
        if (callerContext == null) {
            runComponentInline(chunk, component, writer, chunkX, chunkZ, context);
            return;
        }

        try (IrisContext.Scope ignored = IrisContext.open(
                callerContext.getEngine(),
                callerContext.getGenerationSessionId(),
                callerContext.getChunkContext())) {
            runComponentInline(chunk, component, writer, chunkX, chunkZ, context);
        }
    }

    private void runComponentInline(
            MantleChunk<Matter> chunk,
            MantleComponent component,
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            ChunkContext context
    ) {
        chunk.raiseFlagSuspend(component.getFlag(), () -> writer.withComponentPriority(
                component.getPriority(),
                () -> component.generateLayer(writer, chunkX, chunkZ, context)
        ));
    }

    final class MatterComponentTask {
        private final MatterTaskKey key;
        private final CompletableFuture<Void> future = new CompletableFuture<>();
        private volatile Future<?> submission;
        private volatile Throwable failure;
        private boolean started;

        MatterComponentTask(MatterTaskKey key) {
            this.key = key;
        }

        synchronized boolean start() {
            if (started || future.isDone()) {
                return false;
            }
            started = true;
            return true;
        }

        synchronized void setSubmission(Future<?> submission) {
            this.submission = submission;
            if (failure != null) {
                submission.cancel(true);
            }
        }

        synchronized boolean cancel(Throwable cause) {
            if (future.isDone() || failure != null) {
                return false;
            }
            failure = cause;
            if (!started) {
                finish(null);
            }
            if (submission != null) {
                submission.cancel(true);
            }
            return true;
        }

        synchronized void finish(Throwable cause) {
            if (failure == null) {
                failure = cause;
            } else if (cause != null && cause != failure) {
                failure.addSuppressed(cause);
            }
            IN_FLIGHT_COMPONENTS.remove(key, this);
            if (failure == null) {
                future.complete(null);
            } else {
                future.completeExceptionally(failure);
            }
        }

        void await(long timeoutMillis) {
            long start = System.nanoTime();
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        future.get(COMPONENT_TASK_POLL_MS, TimeUnit.MILLISECONDS);
                        return;
                    } catch (InterruptedException interruption) {
                        // The writer cannot close while this task still writes through it, so the wait is
                        // uninterruptible and the flag is restored on the way out.
                        interrupted = true;
                    } catch (TimeoutException timeout) {
                        if (future.isDone() || failure != null) {
                            continue;
                        }
                        Future<?> submitted = submission;
                        boolean dropped = submitted != null && submitted.isDone();
                        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                        if (!dropped && waited < timeoutMillis) {
                            continue;
                        }
                        IllegalStateException timeoutFailure = new IllegalStateException("Mantle component " + key
                                + (dropped ? " was dropped by the dispatcher" : " did not complete in " + waited + "ms"));
                        if (cancel(timeoutFailure)) {
                            IrisLogging.error(timeoutFailure.getMessage());
                        }
                    } catch (ExecutionException executionFailure) {
                        Throwable cause = executionFailure.getCause();
                        throw new CompletionException(cause != null ? cause : executionFailure);
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    record MatterPassPlan(MantlePass pass, int passChunkRadius, int downstreamBlockRadius) {
    }

    record MatterGenerationPlan(MatterPassPlan[] passPlans, int writerAccessRadius, MatterGenerationPhase phase) {
    }

    final class MatterTaskKey {
        private final Mantle<Matter> mantle;
        private final int chunkX;
        private final int chunkZ;
        private final MantleFlag flag;

        MatterTaskKey(Mantle<Matter> mantle, int chunkX, int chunkZ, MantleFlag flag) {
            this.mantle = mantle;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.flag = flag;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof MatterTaskKey other)) {
                return false;
            }

            return mantle == other.mantle
                    && chunkX == other.chunkX
                    && chunkZ == other.chunkZ
                    && flag.ordinal() == other.flag.ordinal();
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(mantle);
            result = 31 * result + chunkX;
            result = 31 * result + chunkZ;
            result = 31 * result + flag.ordinal();
            return result;
        }

        @Override
        public String toString() {
            return flag.name() + " at " + chunkX + "," + chunkZ;
        }
    }
}
