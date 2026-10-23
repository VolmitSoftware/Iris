package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.ModuleLog;
import com.volmit.ModuleSetup;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.Pipeline;
import com.volmit.stage.Stage;
import com.volmit.support.TestContexts;
import com.volmit.support.TestModuleJar;
import com.volmit.support.TestSetups;
import com.volmit.support.TestWorld;
import com.volmit.world.PackWorld;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModuleLogTest {
    private static final String ALPHA = "test:alpha";
    private static final String BETA = "test:beta";
    private static final int ONCE_KEY_CAP = 512;

    /** A host logger that remembers what it was asked to write. */
    private static final class RecordingLogger implements System.Logger {
        record Line(Level level, String message, Throwable thrown) {}

        final List<Line> lines = new ArrayList<>();
        boolean debugEnabled = true;

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public boolean isLoggable(Level level) {
            return level != Level.DEBUG || debugEnabled;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
            lines.add(new Line(level, message, thrown));
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            lines.add(new Line(level, format, null));
        }

        List<String> messages() {
            return lines.stream().map(Line::message).toList();
        }
    }

    private static IrisModule module(String id) {
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(id, "Log " + id, "1"); }
            public void setup(ModuleSetup setup) {}
        };
    }

    @Test
    void labelsEveryLineWithTheModuleIdAndKeepsTheLevel() {
        var logger = new RecordingLogger();
        var log = ModuleLogs.labelled(ALPHA, logger);

        log.debug("d");
        log.info("i");
        log.warn("w");
        log.error("e", null);

        assertEquals(List.of("[test:alpha] d", "[test:alpha] i", "[test:alpha] w", "[test:alpha] e"), logger.messages());
        assertEquals(List.of(Level.DEBUG, Level.INFO, Level.WARNING, Level.ERROR),
                logger.lines.stream().map(RecordingLogger.Line::level).toList());
    }

    @Test
    void aMessageIsPlainTextNotAFormat() {
        var logger = new RecordingLogger();

        ModuleLogs.labelled(ALPHA, logger).warn("100% {0} %s");

        assertEquals(List.of("[test:alpha] 100% {0} %s"), logger.messages());
    }

    @Test
    void debugIsWrittenOnlyWhenTheHostLogsDebug() {
        var logger = new RecordingLogger();
        logger.debugEnabled = false;
        var log = ModuleLogs.labelled(ALPHA, logger);

        log.debug("hidden");
        log.info("shown");
        logger.debugEnabled = true;
        log.debug("now shown");

        assertEquals(List.of("[test:alpha] shown", "[test:alpha] now shown"), logger.messages());
    }

    @Test
    void errorHandsTheCauseToTheHostToReport() {
        var logger = new RecordingLogger();
        var cause = new IllegalStateException("boom");

        ModuleLogs.labelled(ALPHA, logger).error("Failed to place", cause);

        assertEquals(1, logger.lines.size());
        assertEquals(Level.ERROR, logger.lines.get(0).level());
        assertEquals("[test:alpha] Failed to place", logger.lines.get(0).message());
        assertSame(cause, logger.lines.get(0).thrown());
    }

    @Test
    void warnOnceWarnsTheFirstTimeAndDebugsAfterwards() {
        var logger = new RecordingLogger();
        var log = ModuleLogs.labelled(ALPHA, logger);

        log.warnOnce("empty", "first");
        log.warnOnce("empty", "second");
        log.warnOnce("other", "third");

        assertEquals(List.of(Level.WARNING, Level.DEBUG, Level.WARNING),
                logger.lines.stream().map(RecordingLogger.Line::level).toList());
        assertEquals(List.of("[test:alpha] first", "[test:alpha] second", "[test:alpha] third"), logger.messages());
    }

    @Test
    void warnOnceKeysBelongToTheModule() {
        var logger = new RecordingLogger();
        var alpha = ModuleLogs.labelled(ALPHA, logger);
        var beta = ModuleLogs.labelled(BETA, logger);

        alpha.warnOnce("shared", "alpha says");
        beta.warnOnce("shared", "beta says");

        assertEquals(List.of(Level.WARNING, Level.WARNING), logger.lines.stream().map(RecordingLogger.Line::level).toList());
    }

    @Test
    void warnOnceStopsClaimingKeysPastTheCap() {
        var logger = new RecordingLogger();
        var log = ModuleLogs.labelled(ALPHA, logger);
        for (int key = 0; key < ONCE_KEY_CAP; key++) {
            log.warnOnce("key" + key, "claimed");
        }
        logger.lines.clear();

        log.warnOnce("one too many", "dropped to debug");

        assertEquals(List.of(Level.DEBUG), logger.lines.stream().map(RecordingLogger.Line::level).toList());
    }

    @Test
    void discardingLogWritesNothingAndNeverThrows() {
        var log = ModuleLogs.discarding();

        assertDoesNotThrow(() -> {
            log.debug("d");
            log.info("i");
            log.warn("w");
            log.warnOnce("k", "m");
            log.error("e", new RuntimeException());
        });
    }

    @Test
    void builtInModulesLogThroughTheHostLogger() {
        var logger = new RecordingLogger();
        var host = ModuleHost.of(List.of(module(ALPHA))).loggingTo(logger);

        host.builtIns().get(0).log().warn("hello");

        assertEquals(List.of("[test:alpha] hello"), logger.messages());
    }

    @Test
    void packModulesLogThroughTheLoadersLogger(@TempDir Path pack, @TempDir Path copies) {
        var logger = new RecordingLogger();
        TestModuleJar.module("pack.gamma.GammaModule", "gamma:core", "", "")
                .writeInto(pack, "gamma.jar");

        try (var modules = PackModules.load(pack, List.of(), copies, logger)) {
            logger.lines.clear();
            modules.modules().get(0).log().warn("from a jar");
        }

        assertEquals(List.of("[gamma:core] from a jar"), logger.messages());
    }

    @Test
    void setupGivesTheModuleItsOwnLog() {
        var logger = new RecordingLogger();
        var logs = new ArrayList<ModuleLog>();
        var settingUp = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(BETA, "Sets up", "1"); }
            public void setup(ModuleSetup setup) { logs.add(setup.log()); }
        };

        TestSetups.install(new StageGraphBuilder(), DiscoveredModule.builtIn(settingUp).loggingTo(logger));
        logs.get(0).info("set up");

        assertEquals(List.of("[test:beta] set up"), logger.messages());
    }

    @Test
    void aStageBodySeesItsOwnModulesLogOnTheContextAndOnTheWorld() {
        var logger = new RecordingLogger();
        var alphaStage = Stage.chunk("test:alpha/first");
        var betaStage = Stage.chunk("test:beta/second");
        var builder = new StageGraphBuilder();
        TestSetups.install(builder, DiscoveredModule.builtIn(stagedModule(ALPHA, alphaStage, null)).loggingTo(logger));
        TestSetups.install(builder, DiscoveredModule.builtIn(stagedModule(BETA, betaStage, alphaStage)).loggingTo(logger));
        var runner = new ChunkRunner(builder.resolve(Pipeline.CHUNK));

        try (var world = new TestWorld()) {
            runner.generate(TestContexts.generation(Map.of(), null, null, world), TestContexts.SEED, StageTimings.NONE);
        }

        assertEquals(List.of("[test:alpha] context", "[test:alpha] world", "[test:beta] context", "[test:beta] world"),
                logger.messages());
    }

    private static IrisModule stagedModule(String id, ChunkStage<Void> stage, ChunkStage<Void> after) {
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(id, "Stage " + id, "1"); }
            public void setup(ModuleSetup setup) {
                var definition = setup.stages().define(stage);
                if (after != null) {
                    definition.softAfter(after);
                }
                definition.run(context -> {
                    context.log().info("context");
                    context.world().log().info("world");
                });
            }
        };
    }

    @Test
    void lifecycleHooksAndServicesGetAWorldWhoseLogIsTheModules() {
        var logger = new RecordingLogger();
        var seen = new ArrayList<ModuleLog>();
        var module = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(ALPHA, "Hooks", "1"); }
            public void setup(ModuleSetup setup) {
                setup.registry().registerLifecycle(new ModuleLifecycle() {
                    public void worldStarted(PackWorld world) { seen.add(world.log()); }
                });
            }
        };
        var discovered = DiscoveredModule.builtIn(module).loggingTo(logger);
        var catalog = LoadedModules.load(List.of(discovered), Map.of(), Set.of(), Set.of()).catalog();

        try (var world = new TestWorld()) {
            new LifecycleRunner(catalog).worldStarted(world);
            new ServiceRegistration(Runnable.class, (Runnable) () -> {}, discovered).scope(world).log().warn("from a service");
        }
        seen.get(0).info("from a hook");

        assertEquals(List.of("[test:alpha] from a service", "[test:alpha] from a hook"), logger.messages());
    }
}
