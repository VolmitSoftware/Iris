package art.arcane.iris.core.runtime;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BukkitEngineLifecycleContractTest {
    @Test
    public void closeFutureIsPublishedBeforeCloseWorkIsScheduled() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.bukkitChunkGeneratorSource")));
        String closeAsync = method(source, "public CompletableFuture<Void> closeAsync()");

        assertBefore(closeAsync, "closeFuture.compareAndSet(null, future)", "withExclusiveControlFuture(");
        assertTrue(closeAsync.contains("while (!closeFuture.compareAndSet(null, future))"));
        assertTrue(closeAsync.contains("return existing;"));
        assertTrue(closeAsync.contains("operation.whenComplete("));
        assertFalse(closeAsync.contains("!existing.isDone()"));

        String exclusiveFuture = method(source, "public CompletableFuture<Void> withExclusiveControlFuture(Runnable r)");
        assertTrue(exclusiveFuture.contains("J.a(() -> completeExclusiveControlFuture(loadLock, r, future))"));
        String exclusiveCompletion = method(source, "static void completeExclusiveControlFuture(");
        assertBefore(exclusiveCompletion, "activeGate.releaseExclusive();", "outward.complete(null);");
        assertBefore(exclusiveCompletion, "activeGate.releaseExclusive();", "outward.completeExceptionally(failure);");

        String baseHeight = method(source, "public int getBaseHeight(");
        assertTrue(baseHeight.contains("currentEngine.acquireGenerationLease(\"bukkit_base_height\")"));
        assertTrue(baseHeight.contains("IrisContext.open(currentEngine, lease.sessionId(), null)"));
        assertTrue(baseHeight.contains("catch (GenerationSessionException | IOException e)"));

        String generation = method(source, "public void generateNoise(");
        assertTrue(generation.contains("throw new IllegalStateException"));
        assertTrue(generation.contains("engine.acquireGenerationLease(\"bukkit_terrain_stage\")"));
        assertTrue(generation.contains("IrisContext.open(engine, lease.sessionId(), null)"));
        assertBefore(generation, "engine.acquireGenerationLease(\"bukkit_terrain_stage\")", "blocks.apply()");
        assertFalse(generation.contains("RED_GLAZED_TERRACOTTA"));
    }

    @Test
    public void targetedPregeneratorShutdownChecksWorldIdentity() throws IOException {
        String jobSource = Files.readString(Path.of(System.getProperty("iris.pregeneratorJobSource")));
        String targetedShutdown = method(jobSource,
                "public static boolean shutdownInstanceForWorld(String worldIdentity)");

        assertBefore(targetedShutdown, "inst.targetsWorldIdentity(worldIdentity)",
                "shutdownAndWait(inst, WORLD_SHUTDOWN_TIMEOUT_MILLIS)");

        String waitedShutdown = method(jobSource,
                "private static boolean shutdownAndWait(PregeneratorJob inst, long timeoutMs)");
        assertBefore(waitedShutdown, "inst.requestStop()", "inst.worker.join(");
        assertBefore(waitedShutdown, "inst.worker.join(", "finishShutdown(inst, timeoutMs)");
        String finishedShutdown = method(jobSource,
                "private static boolean finishShutdown(PregeneratorJob inst, long timeoutMs)");
        assertBefore(finishedShutdown, "inst.worker.isAlive()", "instance.compareAndSet(inst, null)");

        String hooksSource = Files.readString(Path.of(System.getProperty("iris.bukkitEnginePlatformHooksSource")));
        String hookShutdown = method(hooksSource, "public void shutdownPregenerator(Engine engine)");
        assertTrue(hookShutdown.contains("PregeneratorJob.shutdownInstanceForWorld(world.identity());"));
        assertFalse(hookShutdown.contains("PregeneratorJob.shutdownInstance();"));
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue("Missing source contract token: " + first, firstIndex >= 0);
        assertTrue("Missing source contract token: " + second, secondIndex >= 0);
        assertTrue(first + " must occur before " + second, firstIndex < secondIndex);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing source contract signature: " + signature, start >= 0);
        int openBrace = source.indexOf('{', start);
        assertTrue("Missing source contract method body: " + signature, openBrace >= 0);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new IllegalArgumentException("Unclosed source contract method: " + signature);
    }
}
