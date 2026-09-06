package art.arcane.iris.engine;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class EngineShutdownOwnershipContractTest {
    @Test
    public void attachedHistoryRouterClosesBeforeRuntimeOwnershipReleases() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineShutdownSequence.java")).replace("\r\n", "\n");
        int closeStart = source.indexOf("void close()");
        int routerClose = source.indexOf("engine::closeAttachedGenerationHistoryRuntimeRouter", closeStart);
        int runtimeRelease = source.indexOf("releaseRuntime(failure)", routerClose);

        assertTrue(routerClose > closeStart);
        assertTrue(runtimeRelease > routerClose);
    }

    @Test
    public void ownershipCloseMustSucceedBeforeRuntimeRelease() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineShutdownSequence.java")).replace("\r\n", "\n");
        int ownershipClose = source.indexOf("NativeStructureOwnershipStore.close(engine)");
        int ownershipGate = source.indexOf("if (ownershipFailure == null)", ownershipClose);
        int runtimeRelease = source.indexOf("releaseRuntime(failure)", ownershipGate);

        assertTrue(ownershipClose >= 0);
        assertTrue(ownershipGate > ownershipClose);
        assertTrue(runtimeRelease > ownershipGate);
    }

    @Test
    public void failedConstructionKeepsMantleOpenWhileOwnershipWritesRemain() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineShutdownSequence.java")).replace("\r\n", "\n");
        int cleanupStart = source.indexOf("void cleanupFailedConstruction");
        int cleanupEnd = source.indexOf("Throwable closeAssembly", cleanupStart);
        String cleanup = source.substring(cleanupStart, cleanupEnd);
        int ownershipClose = cleanup.indexOf("NativeStructureOwnershipStore.close(engine)");
        int ownershipGate = cleanup.indexOf("if (ownershipFailure == null)", ownershipClose);
        int runtimeRelease = cleanup.indexOf("releaseRuntime(cleanupFailure)", ownershipGate);
        int targetRelease = cleanup.indexOf("releaseTarget(cleanupFailure)", runtimeRelease);
        int closedPublication = cleanup.indexOf("engine.closed = true", ownershipGate);

        assertTrue(ownershipClose >= 0);
        assertTrue(ownershipGate > ownershipClose);
        assertTrue(runtimeRelease > ownershipGate);
        assertTrue(targetRelease > runtimeRelease);
        assertTrue(closedPublication > ownershipGate);
    }

    @Test
    public void detachedRuntimesCloseBeforeTheBaseRuntimeAndTarget() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineShutdownSequence.java")).replace("\r\n", "\n");
        int releaseStart = source.indexOf("private Throwable releaseRuntime(");
        int releaseEnd = source.indexOf("private Throwable closeDetachedGenerationRuntimes(", releaseStart);
        String release = source.substring(releaseStart, releaseEnd);
        int detachedRelease = release.indexOf("closeDetachedGenerationRuntimes(null)");
        int detachedGate = release.indexOf("if (detachedFailure != null)", detachedRelease);
        int baseRelease = release.indexOf("closeRuntime(engine.runtime, null)", detachedGate);
        int targetRelease = source.indexOf("engine.publishedTarget::close", releaseEnd);

        assertTrue(detachedRelease >= 0);
        assertTrue(detachedGate > detachedRelease);
        assertTrue(baseRelease > detachedGate);
        assertTrue(targetRelease > releaseEnd);
    }
}
