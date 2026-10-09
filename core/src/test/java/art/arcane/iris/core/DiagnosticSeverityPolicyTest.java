package art.arcane.iris.core;

import art.arcane.iris.engine.object.compatibility.IrisCompat;
import art.arcane.iris.engine.object.image.IrisImage;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every core diagnostic keeps its severity through to the server log, so the level a call site picks is the
 * level an operator scanning for WARN and SEVERE sees. This pins the two ends of that decision: conditions an
 * operator must act on stay at WARN or ERROR, and conditions that repeat per block, per sample or per chunk -
 * or that report designed backpressure, a probe on an optional dependency, or a success - do not.
 * <p>
 * The pin is on the source rather than on a live engine because most of these call sites need a running world
 * to reach, and what is being fixed is the choice of level, not the condition that triggers it.
 */
public class DiagnosticSeverityPolicyTest {
    @Test
    public void perBlockAndPerSampleConditionsDoNotReachTheServerLogAtWarnLevel() {
        assertLoggedWith("util/project/hunk/Hunk.java", "OUT OF BOUNDS ", "debug");
        assertLoggedWith("engine/mantle/MantleWriter.java", "No set? ", "debug");
        assertLoggedWith("engine/mantle/MantleWriter.java", "Mantle Writer Accessed chunk out of bounds", "debug");
        assertLoggedWith("engine/decorator/ResolvedDecorator.java", "Empty Block Data for ", "warnOnce");
        assertLoggedWith("engine/object/compatibility/IrisCompat.java", "Can't find block data for ", "warnOnce");
        assertLoggedWith("engine/data/cache/AtomicCache.java", "Atomic cache supplier failed: %s: %s", "warnOnce");
        assertLoggedWith("engine/IrisComplex.java", "Failed to sample interpolated biome bounds", "warnOnce");
        assertLoggedWith("engine/framework/placer/WorldObjectPlacer.java", "Tried to place custom block at", "warnOnce");
    }

    /**
     * Backpressure pausing generation is the design working. It is reported every five seconds for the whole
     * duration of a large pregen, so at WARN it would be the only thing a log scan finds.
     */
    @Test
    public void designedBackpressureIsReportedWithoutRaisingAWarning() {
        assertLoggedWith("core/pregenerator/PregenMantleBackpressure.java", "Pregen mantle backpressure: ", "info");
        assertLoggedWith("core/pregenerator/PregenMantleBackpressure.java", "Pregen heap pressure: pausing generation", "info");
        assertLoggedWith("core/pregenerator/MantleHeapPressure.java", "Iris heap remained at", "info");
        assertLoggedWith("core/pregenerator/methods/AsyncPregenMethod.java", "is still pending after", "debug");
    }

    @Test
    public void generationTracingStaysAtDebugLevel() {
        assertLoggedWith("engine/mantle/components/ObjectPlacing.java", "Regen object layer start:", "debug");
        assertLoggedWith("engine/mantle/components/ObjectPlacing.java", "Goldendebug object attempt:", "debug");
        assertLoggedWith("engine/mantle/components/GoldenDebugObjectPlacer.java", "Goldendebug query:", "debug");
        assertLoggedWith("engine/mantle/components/StructurePlacing.java", "[StructTrace] ORIGIN", "debug");
    }

    @Test
    public void coreProductionDoesNotWriteDirectlyToProcessStreams() throws IOException {
        assertNoProcessStreamWrites(Path.of("src/main/java"));
        assertNoProcessStreamWrites(defaultModuleSources());
    }

    private static void assertNoProcessStreamWrites(Path sourceRoot) throws IOException {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> bypasses = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("System.out")
                                    || source.contains("System.err")
                                    || source.contains(".printStackTrace();");
                        } catch (IOException unreadable) {
                            throw new IllegalStateException(unreadable);
                        }
                    })
                    .toList();
            assertTrue(bypasses.toString(), bypasses.isEmpty());
        }
    }

    @Test
    public void probesOnOptionalDependenciesAreNotWarnings() throws IOException {
        assertLoggedWith("core/link/MultiverseCoreLink.java", "is not reachable; Iris world settings", "info");
        assertLoggedWith("core/link/MultiverseCoreLink.java", "Multiverse will record the live name", "info");
        assertLoggedWith("core/gui/GuiHost.java", "Unable to install the Iris desktop quit guard", "info");
        String slimJar = read("util/common/misc/SlimJar.java");
        assertTrue(slimJar.contains("debug(plugin, \"Failed to inject the library loader"));
        assertTrue(slimJar.contains("if (DEBUG)"));
        assertTrue(slimJar.contains("plugin.getLogger().info(\"[DEBUG] \" + message)"));
    }

    /**
     * A refused destructive command is the guard working, and the admin who typed it is told directly. The
     * console record of it is not a warning about the server.
     */
    @Test
    public void aRefusedMultiverseCommandIsRecordedWithoutRaisingAWarning() throws IOException {
        String guard = read("core/link/MultiverseGuardListener.java");

        assertTrue("the console record of a refusal is kept", guard.contains("IrisLogging.info(\"%s %s\", reason, remedy)"));
        assertFalse("the refusal is not a warning about the server", guard.contains("IrisLogging.warn(\"%s %s\""));
    }

    @Test
    public void decorationAndBlankLinesAreNotEmittedAtAnySeverity() throws IOException {
        String safeguard = read("core/safeguard/IrisSafeguard.java");
        assertFalse("a blank record renders as an empty [WARN] line", safeguard.contains("IrisLogging.warn(\"\")"));
        assertFalse("a blank record renders as an empty [SEVERE] line", safeguard.contains("IrisLogging.error(\"\")"));
        assertFalse("a blank record renders as an empty [INFO] line", safeguard.contains("IrisLogging.info(\"\")"));
        assertFalse("a separator rule is not a diagnostic", safeguard.contains("--==<"));

        String configurator = read("core/ServerConfigurator.java");
        assertFalse("a separator rule is not a diagnostic", configurator.contains("IrisLogging.error(\"===="));
        assertFalse("a separator rule is not a diagnostic", configurator.contains("IrisLogging.error(\"----"));
    }

    /**
     * A written debug image is a success, and the method that wrote it had no callers at all.
     */
    @Test
    public void theUnusedDebugImageWriterIsGone() throws IOException {
        assertFalse(read("engine/object/image/IrisImage.java").contains("writeDebug"));
    }

    /**
     * The other end of the policy. These are the conditions the promotion exists for.
     */
    @Test
    public void operatorActionableConditionsKeepTheirSeverity() {
        assertLoggedWith("core/lifecycle/VanishedWorldStorage.java", "Iris world storage is gone at", "error");
        assertLoggedWith("core/IrisWorlds.java", "has unusable world storage and is excluded", "error");
        assertLoggedWith("engine/EngineDataStore.java", "Failed to setup Engine Data", "error");
        assertLoggedWith("engine/IrisEngineMantle.java", "Failed to read chunk section, skipping it.", "error");
        assertLoggedWith("engine/IrisEngineMantle.java", "Failed to read chunk, creating a new chunk instead.", "error");
        assertLoggedWith("util/common/reflect/WrappedField.java", "Failed to created WrappedField", "error");
        assertLoggedWith("core/datapack/DatapackIngestService.java", "Repairing modified or corrupt Iris-managed datapack", "warn");
        assertLoggedWith("core/pregenerator/cache/PregenCacheImpl.java", "Pregen cache storage is gone at", "warn");
        assertLoggedWith("core/pregenerator/PregenMantleBackpressure.java", "Pregen mantle backpressure exceeded ", "warn");
        assertLoggedWith("core/pregenerator/PregenMantleBackpressure.java", "Pregen heap pressure wait exceeded ", "warn");
        assertLoggedWith("core/pregenerator/IrisPregenerator.java", "failed chunk(s); failures are not cached", "warn");
        assertLoggedWith("core/service/MultiverseSVC.java", "Multiverse world events are not bindable", "warn");
    }

    /**
     * A handful of lifecycle lines an operator reads logs/latest.log to find. NOTICE is the level adapters
     * route to the server's own logger rather than to the console sender.
     */
    @Test
    public void lifecycleLinesAreRaisedAtNoticeLevel() {
        assertLoggedWith("engine/IrisEngine.java", "Engine init: ", "notice");
        assertLoggedWith("core/link/MultiverseCoreLink.java", "Adopted %d live Iris world", "notice");
        assertLoggedWith("core/pregenerator/IrisPregenerator.java", "Pregen finished: ", "notice");
    }

    private static void assertLoggedWith(String relativePath, String fragment, String expectedMethod) {
        String source;
        try {
            source = read(relativePath);
        } catch (IOException unreadable) {
            throw new AssertionError("Cannot read " + relativePath, unreadable);
        }

        int occurrences = 0;
        for (int at = source.indexOf(fragment); at >= 0; at = source.indexOf(fragment, at + 1)) {
            occurrences++;
            assertEquals(relativePath + " @ \"" + fragment + "\" occurrence " + occurrences,
                    expectedMethod, loggingMethodBefore(source, at, relativePath, fragment));
        }
        assertTrue(relativePath + " no longer contains \"" + fragment + "\"", occurrences > 0);
    }

    private static String loggingMethodBefore(String source, int at, String relativePath, String fragment) {
        int call = source.lastIndexOf("IrisLogging.", at);
        assertTrue(relativePath + " @ \"" + fragment + "\" is not logged through IrisLogging", call >= 0);
        int start = call + "IrisLogging.".length();
        int end = start;
        while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) {
            end++;
        }
        return source.substring(start, end);
    }

    private static String read(String relativePath) throws IOException {
        Path source = Path.of("src/main/java/art/arcane/iris").resolve(relativePath);
        if (!Files.isRegularFile(source)) {
            source = defaultModuleSources().resolve("art/arcane/iris").resolve(relativePath);
        }
        return Files.readString(source).replace("\r\n", "\n");
    }

    private static Path defaultModuleSources() {
        return Path.of(System.getProperty("iris.defaultModuleSources", "../../IrisModule/src/main/java"));
    }
}
