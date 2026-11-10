package art.arcane.iris.modded;

import art.arcane.iris.engine.object.material.BlockDataMergeSupport;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedLifecycleFailureContractTest {
    private static final String SOURCE_ROOT_PROPERTY = "iris.moddedCommonSources";

    @Test
    public void existingDimensionRepointBindsBeforeHandlePublication() throws IOException {
        String managerSource = source("ModdedDimensionManager.java");
        String create = method(managerSource, "public static Handle create(");
        int existingStart = requiredIndex(create, "if (existing != null");
        int presentStart = requiredIndex(create, "if (serverAccess.hasLevel(server, key))", existingStart + 1);
        int injectionStart = requiredIndex(create, "try {", presentStart);

        assertRepointBeforePublication(create.substring(existingStart, presentStart));
        assertRepointBeforePublication(create.substring(presentStart, injectionStart));

        String generatorSource = source("IrisModdedChunkGenerator.java");
        String repointAndBind = method(generatorSource, "void repointAndBind(");
        assertBefore(repointAndBind, "ModdedWorldEngines.prepareReplacement(",
                "ModdedWorldEngines.installReplacement(");
        assertBefore(repointAndBind, "ModdedWorldEngines.installReplacement(", "this.activePack = pack;");
        String failure = catchBlock(repointAndBind);
        assertTrue(failure.contains("ModdedWorldEngines.closeUnregistered(replacement);"));
        assertTrue(failure.contains("addSuppressed("));

        String worldEnginesSource = source("ModdedWorldEngines.java");
        String installReplacement = method(worldEnginesSource, "static void installReplacement(");
        assertTrue(installReplacement.contains("ENGINES.compute("));
        assertBefore(installReplacement, "close(current);", "return activeReplacement;");
        assertFalse(installReplacement.contains("catch ("));
    }

    @Test
    public void injectionBindsBeforeRegistrationAndRollsBackEveryFailure() throws IOException {
        String source = source("ModdedDimensionManager.java");
        String injection = method(source, "private static Handle inject(");

        assertBefore(injection, "generator.bindLevel(level);", "serverAccess.putLevelIfAbsent(server, key, level);");
        assertBefore(injection, "serverAccess.putLevelIfAbsent(server, key, level);",
                "server.getPlayerList().addWorldborderListener(level);");
        assertFalse(injection.contains("serverAccess.putLevel(server, key, previous);"));
        String failure = catchBlock(injection);
        assertBefore(failure, "rollbackInjection(", "throw ");

        String rollback = method(source, "void rollbackInjection(");
        assertBefore(rollback, "serverAccess.hasLevel(server, key)", "serverAccess.removeLevel(server, key);");
        assertTrue(rollback.contains("generator.unbindEngine("));
        assertTrue(rollback.contains("level.close();"));
        assertTrue(rollback.contains("addSuppressed("));
    }

    @Test
    public void persistentReinjectionQuarantinesBrokenEntriesAndContinues() throws IOException {
        String source = source("ModdedStartup.java");
        String reinjection = method(source, "private static void reinjectPersistentDimensions(");
        String failure = catchBlock(reinjection);

        assertTrue(failure.contains("ModdedIrisLog.error("));
        assertFalse(failure.contains("e.toString()"));
        assertFalse(failure.contains("throw new IllegalStateException("));
        assertTrue(reinjection.contains("injected++;"));
    }

    @Test
    public void persistentReinjectionWaitsForPlayerServicesAndServerStarted() throws IOException {
        String bootstrapSource = source("ModdedEngineBootstrap.java");
        String start = method(bootstrapSource, "public static void start(");
        String serverAboutToStart = method(bootstrapSource, "public static void serverAboutToStart(");
        String serverStarted = method(bootstrapSource, "public static void serverStarted(");

        assertFalse(start.contains("ModdedStartup.runOnce(server);"));
        assertFalse(serverAboutToStart.contains("ModdedStartup.runOnce(server);"));
        assertEquals(1, occurrences(serverStarted, "ModdedStartup.runOnce(server);"));
        assertBefore(serverStarted, "bindWorldGenerators(server);", "ModdedStartup.runOnce(server);");
        assertBefore(serverStarted, "ModdedStartup.runOnce(server);", "reconcileSpawn(server);");

        String startupSource = source("ModdedStartup.java");
        String runOnce = method(startupSource, "public static void runOnce(");
        assertBefore(runOnce, "server.getPlayerList() == null", "STARTED.compareAndSet(false, true)");
    }

    @Test
    public void dynamicLevelMutationsInvalidateLoaderTickCaches() throws IOException {
        String levelsSource = source("ModdedServerLevels.java");
        String put = method(levelsSource, "public ServerLevel putLevel(");
        String putIfAbsent = method(levelsSource, "public ServerLevel putLevelIfAbsent(");
        String remove = method(levelsSource, "public ServerLevel removeLevel(");

        assertBefore(put, "server.levels.put(key, level);", "levelCacheInvalidator.accept(server);");
        assertTrue(put.contains("if (previous != level)"));
        assertBefore(putIfAbsent, "server.levels.putIfAbsent(key, level);", "levelCacheInvalidator.accept(server);");
        assertTrue(putIfAbsent.contains("if (previous == null)"));
        assertBefore(remove, "server.levels.remove(key);", "levelCacheInvalidator.accept(server);");
        assertTrue(remove.contains("if (removed != null)"));

        String loaderSource = source("ModdedLoader.java");
        assertTrue(loaderSource.contains("void invalidateLevelCache(MinecraftServer server);"));
        String bootstrapSource = source("ModdedEngineBootstrap.java");
        assertTrue(bootstrapSource.contains("new ModdedServerLevels(boundLoader::invalidateLevelCache)"));
    }

    @Test
    public void packDimensionLoadingNeverSuppressesTheFailureAsNull() throws IOException {
        String source = source("ModdedDimensionManager.java");
        String loading = method(source, "private static IrisDimension loadPackDimension(");

        assertFalse(loading.contains("return null;"));
        assertFalse(loading.contains("e.toString()"));
        int catchIndex = loading.indexOf("catch (");
        if (catchIndex >= 0) {
            String failure = blockAt(loading, requiredIndex(loading, "{", catchIndex));
            assertTrue(failure.contains("throw "));
            assertTrue(failure.contains(", e);") || failure.contains("throw e;"));
        }
    }

    @Test
    public void productionPackResolutionNeverUsesTheParityProbeAsData() throws IOException {
        String source = source("ModdedWorldEngines.java");
        String resolvePack = method(source, "static File resolvePack(");

        assertFalse(source.contains("System.getProperty(\"iris.parity\")"));
        assertFalse(source.contains("parityPack"));
        assertFalse(source.contains("falling back to parity pack"));
        assertTrue(resolvePack.contains("throw new IllegalStateException("));
    }

    @Test
    public void engineBootstrapResetsStartupExactlyOnceDuringStop() throws IOException {
        String source = source("ModdedEngineBootstrap.java");
        String stopped = method(source, "private static void closeStoppedServer(");

        assertEquals(1, occurrences(source, "ModdedStartup::reset"));
        assertEquals(1, occurrences(stopped, "ModdedStartup::reset"));
    }

    @Test
    public void engineBootstrapAttemptsEveryShutdownStageAndAggregatesFailures() throws IOException {
        String source = source("ModdedEngineBootstrap.java");
        String stop = method(source, "public static void stop(");
        String stopped = method(source, "private static void closeStoppedServer(");

        assertTrue(stop.contains("\"services\""));
        assertFalse(stop.contains("\"world engines\""));
        assertBefore(stopped, "\"world engines\"", "\"dimension manager\"");
        assertBefore(stopped, "\"server state\"", "reportStopFailure(\"stopped\", failure);");
        assertBefore(stop, "\"services\"", "reportStopFailure(\"stopping\", failure);");
        assertFalse(stop.contains("throw"));
        assertFalse(stopped.contains("throw"));
        String report = method(source, "private static void reportStopFailure(");
        assertTrue(report.contains("ModdedIrisLog.error(\"Iris modded shutdown ({}) completed with failures\", phase, failure);"));

        String runStage = method(source, "private static Throwable runStopStage(");
        assertTrue(runStage.contains("catch (Throwable stageFailure)"));
        assertTrue(runStage.contains("failure.addSuppressed(stageFailure);"));
    }

    @Test
    public void worldShutdownRemovesOnlySuccessfullyClosedMappings() throws IOException {
        String source = source("ModdedWorldEngines.java");
        String shutdown = method(source, "public static void shutdown()");

        assertTrue(shutdown.contains("new ArrayList<>(ENGINES.entrySet())"));
        assertFalse(shutdown.contains("ENGINES.clear()"));
        // Bound generators must go through unbindEngine (latch unloading, then strict evict) so a
        // late chunk-system drain cannot rebuild an engine nothing closes again.
        assertTrue(shutdown.contains("generator.unbindEngine(level);"));
        assertBefore(shutdown, "close(engine);", "ENGINES.remove(level, engine)");
        assertTrue(shutdown.contains("ENGINES.containsKey(level)"));
        assertTrue(shutdown.contains("failure.addSuppressed(e);"));
        assertTrue(shutdown.contains("failed mappings were retained"));
    }

    @Test
    public void engineEvictionRetainsTheMappingUntilCloseSucceeds() throws IOException {
        String source = source("ModdedWorldEngines.java");
        String eviction = method(source, "static void evictOrThrow(");

        assertTrue(eviction.contains("ENGINES.computeIfPresent("));
        assertFalse(eviction.contains("ENGINES.remove("));
        assertBefore(eviction, "close(current);", "return null;");
    }

    @Test
    public void generatorUnbindRetainsOwnershipWhenEvictionFails() throws IOException {
        String source = source("IrisModdedChunkGenerator.java");
        String unbind = method(source, "synchronized void unbindEngine(ServerLevel level)");

        assertFalse(unbind.contains("finally"));
        assertBefore(unbind, "unloading = true;", "ModdedWorldEngines.evictOrThrow(level);");
        assertBefore(unbind, "ModdedWorldEngines.evictOrThrow(level);", "clearEngineBinding();");
        String binding = method(source, "private Engine bindEngine(ServerLevel level)");
        assertTrue(binding.contains("requireBindingAllowed();"));
        assertTrue(binding.contains("requireCompletedShutdown(cached);"));
        String bindLevel = method(source, "synchronized void bindLevel(ServerLevel level)");
        assertBefore(bindLevel, "requireCompletedShutdown(engine);", "unloading = false;");
        String boundEngine = method(source, "public Engine engineIfBound()");
        assertTrue(boundEngine.contains("unloading || current == null || current.isClosing() || current.isClosed()"));
    }

    @Test
    public void loaderUnloadLogsCloseFailureAndDynamicRemovalUsesStrictEviction() throws IOException {
        String bootstrapSource = source("ModdedEngineBootstrap.java");
        String unload = method(bootstrapSource, "public static void levelUnloaded(ServerLevel level)");
        String failure = catchBlock(unload);
        assertTrue(failure.contains("ModdedIrisLog.error("));
        assertFalse(failure.contains("throw "));

        String managerSource = source("ModdedDimensionManager.java");
        String remove = method(managerSource, "public static boolean remove(MinecraftServer server, String dimensionId, boolean wipeStorage)");
        assertTrue(remove.contains("ModdedWorldEngines.evictOrThrow(level);"));
        assertFalse(remove.contains("ModdedWorldEngines.evict(level);"));
        assertTrue(remove.contains("unloadEventStarted = true;"));
        assertTrue(remove.contains("rollbackRemoval(server, serverAccess, key, level, generator, unloadEventStarted, e);"));

        String rollback = method(managerSource, "private static void rollbackRemoval(");
        assertTrue(rollback.contains("serverAccess.hasLevel(server, key)"));
        assertTrue(rollback.contains("generator.bindLevel(level);"));
        assertTrue(rollback.contains("failure.addSuppressed(rollbackFailure);"));
        assertTrue(rollback.contains("ModdedIrisLog.error("));
    }

    @Test
    public void engineBootstrapRollsBackEveryPublishedBindingBeforeRethrowing() throws IOException {
        String source = source("ModdedEngineBootstrap.java");
        String bind = method(source, "public static ModdedPlatform bind(");
        String failure = catchBlock(bind);

        assertBefore(bind, "createdServices.enableAll();", "runtime = new BoundRuntime(");
        assertTrue(bind.contains("rollback.add(() -> GuiHost.suppressDesktop("));
        assertTrue(bind.contains("rollback.add(() -> restorePlatform("));
        assertTrue(bind.contains("rollback.add(() -> ModdedDimensionManager.restoreAccess("));
        assertTrue(bind.contains("rollback.add(() -> BlockStateRotation.restorePlatformRotator("));
        assertTrue(bind.contains("rollback.add(() -> BlockDataMergeSupport.restorePlatformMerger("));
        assertTrue(bind.contains("rollback.add(() -> TileData.restorePlatformReader("));
        assertTrue(bind.contains("rollback.add(() -> TileData.restorePlatformFactory("));
        assertTrue(bind.contains("rollback.add(() -> GuiHost.set("));
        assertTrue(bind.contains("bindService(PreservationRegistry.class"));
        assertTrue(bind.contains("bindService(EngineEffectsProvider.class"));
        assertTrue(bind.contains("bindService(EnginePlatformHooks.class"));
        assertTrue(bind.contains("bindService(EngineWorldManagerProvider.class"));
        assertTrue(bind.contains("rollback.add(customContentDiscovery::rollback);"));
        assertBefore(failure, "createdServices.rollback(failure);", "rollback.restore(failure);");
        assertBefore(failure, "rollback.restore(failure);", "throw ");
    }

    private static void assertRepointBeforePublication(String branch) {
        assertBefore(branch, "repointAndBind(", "new Handle(");
        assertBefore(branch, "repointAndBind(", "HANDLES.put(");
        assertBefore(branch, "repointAndBind(", "return ");
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = requiredIndex(source, first);
        int secondIndex = requiredIndex(source, second);
        assertTrue(first + " must occur before " + second, firstIndex < secondIndex);
    }

    private static int requiredIndex(String source, String needle) {
        return requiredIndex(source, needle, 0);
    }

    private static int requiredIndex(String source, String needle, int start) {
        int index = source.indexOf(needle, start);
        assertTrue("Missing source contract token: " + needle, index >= 0);
        return index;
    }

    private static String source(String fileName) throws IOException {
        String sourceRoot = System.getProperty(SOURCE_ROOT_PROPERTY);
        assertTrue("Missing system property " + SOURCE_ROOT_PROPERTY,
                sourceRoot != null && !sourceRoot.isBlank());
        Path sourcePath = Path.of(sourceRoot, "art", "arcane", "iris", "modded", fileName);
        return Files.readString(sourcePath);
    }

    private static String method(String source, String signature) {
        int start = requiredIndex(source, signature);
        int openBrace = openingBrace(source, start);
        return source.substring(start, matchingBrace(source, openBrace) + 1);
    }

    /**
     * Returns the block of the shallowest catch in the method, i.e. the one attached to the
     * method's outermost try. Textual first-match would return a nested catch instead, and a
     * nested catch handles a different failure than the one these contracts pin.
     */
    private static String catchBlock(String source) {
        int bodyStart = openingBrace(source, 0);
        int depth = 0;
        int bestDepth = Integer.MAX_VALUE;
        int catchIndex = -1;
        int i = bodyStart;
        while (i < source.length()) {
            int skipped = skipNonCode(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    break;
                }
            } else if (current == 'c' && depth < bestDepth && source.startsWith("catch (", i)) {
                bestDepth = depth;
                catchIndex = i;
            }
            i++;
        }
        assertTrue("Missing source contract token: catch (", catchIndex >= 0);
        return blockAt(source, openingBrace(source, catchIndex));
    }

    private static String blockAt(String source, int openBrace) {
        return source.substring(openBrace, matchingBrace(source, openBrace) + 1);
    }

    private static int openingBrace(String source, int start) {
        int i = start;
        while (i < source.length()) {
            int skipped = skipNonCode(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }
            if (source.charAt(i) == '{') {
                return i;
            }
            i++;
        }
        throw new IllegalArgumentException("No source block after offset " + start);
    }

    private static int matchingBrace(String source, int openBrace) {
        int depth = 0;
        int i = openBrace;
        while (i < source.length()) {
            int skipped = skipNonCode(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        throw new IllegalArgumentException("Unclosed source block");
    }

    /**
     * Advances past a comment, string, char or text block starting at the index, otherwise returns
     * the index unchanged. Comments must be skipped: an apostrophe in prose ("generator's") would
     * otherwise open a char literal and swallow the rest of the file.
     */
    private static int skipNonCode(String source, int index) {
        char current = source.charAt(index);
        if (current == '/' && index + 1 < source.length()) {
            char next = source.charAt(index + 1);
            if (next == '/') {
                int end = source.indexOf('\n', index);
                return end < 0 ? source.length() : end + 1;
            }
            if (next == '*') {
                int end = source.indexOf("*/", index + 2);
                return end < 0 ? source.length() : end + 2;
            }
            return index;
        }
        if (current == '"' && source.startsWith("\"\"\"", index)) {
            int end = source.indexOf("\"\"\"", index + 3);
            return end < 0 ? source.length() : end + 3;
        }
        if (current == '"' || current == '\'') {
            return endOfLiteral(source, index, current);
        }
        return index;
    }

    private static int endOfLiteral(String source, int index, char terminator) {
        for (int i = index + 1; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '\\') {
                i++;
                continue;
            }
            if (current == terminator) {
                return i + 1;
            }
        }
        return source.length();
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while (true) {
            int index = source.indexOf(needle, offset);
            if (index < 0) {
                return count;
            }
            count++;
            offset = index + needle.length();
        }
    }
}
