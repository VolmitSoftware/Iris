package art.arcane.iris.core.tooling;

import art.arcane.iris.core.modules.ModuleResourceFixture;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLog;
import com.volmit.ModuleSetup;
import com.volmit.config.Reference;
import com.volmit.host.CommandRegistration;
import com.volmit.resource.PackResources;
import com.volmit.resource.PlatformRegistries;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;
import com.volmit.tooling.ToolPack;
import com.volmit.world.PackWorld;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ToolingCommandsTest {
    private static final ResourceKind<String> NOTES = ResourceKind.of("test:note", "notes", String.class);

    private enum Mode { FAST_PASS, SLOW }

    private final Executor sameThread = Runnable::run;
    private final Map<String, FakePack> packsByName = new LinkedHashMap<>();
    private final RecordingSender sender = new RecordingSender();
    private final List<Object> seen = Collections.synchronizedList(new ArrayList<>());
    private Optional<PlatformRegistries> registries = Optional.empty();
    private ToolingCommands dispatcher;

    @Before
    public void dispatcherOverFakePacks() {
        dispatcher = new ToolingCommands(new PackDirectory() {
            @Override
            public Optional<ServedPack> find(String name) {
                return Optional.ofNullable(packsByName.get(name));
            }

            @Override
            public List<String> names() {
                return List.copyOf(packsByName.keySet());
            }
        }, sameThread, () -> registries);
    }

    private FakePack packWith(String name, Command... roots) {
        PackResources resources = mock(PackResources.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(resources.packId()).thenReturn(new ResourceId("test", name));
        when(resources.ids(NOTES)).thenReturn(List.of(new ResourceId("test", "alpha"), new ResourceId("test", "beta"),
                new ResourceId("other", "gamma")));
        ToolPack toolPack = mock(ToolPack.class);
        when(toolPack.resources()).thenReturn(resources);
        IrisModule module = new IrisModule() {
            @Override
            public ModuleInfo info() {
                return ModuleInfo.of("test:" + name, name, "1");
            }

            @Override
            public void setup(ModuleSetup setup) {
                for (Command root : roots) setup.tooling().registerCommand(root);
            }
        };
        FakePack pack = new FakePack(name, ModuleResourceFixture.catalogOf(module).commands(), toolPack);
        packsByName.put(name, pack);
        return pack;
    }

    private static List<String> words(String line) {
        return line.isBlank() ? List.of() : List.of(line.trim().split("\\s+", -1));
    }

    private void run(String line) {
        dispatcher.execute(sender, words(line));
    }

    private List<String> complete(String line) {
        return dispatcher.complete(sender, List.of(line.split(" ", -1)));
    }

    private void standIn(FakePack pack) {
        sender.world = Optional.of(new SenderWorld(pack, mock(PackWorld.class)));
    }

    private Command recording(String name) {
        return Command.named(name).runs(context -> seen.add(name));
    }

    @Test
    public void parsesRequiredAndOptionalArgumentsInOrder() {
        Argument<String> label = Argument.word("label");
        Argument<Long> count = Argument.integer("count", 1, 10);
        Argument<Mode> mode = Argument.choice("mode", Mode.class).orElse(Mode.SLOW);
        packWith("one", Command.named("give").argument(label).argument(count).argument(mode)
                .runs(context -> seen.add(List.of(context.get(label), context.get(count), context.get(mode)))));
        standIn(packsByName.get("one"));

        run("give apple 3");
        run("give pear 7 fast-pass");

        assertEquals(List.of(List.of("apple", 3L, Mode.SLOW), List.of("pear", 7L, Mode.FAST_PASS)), seen);
        assertTrue(sender.errors.isEmpty());
    }

    @Test
    public void reportsAMissingRequiredArgumentWithTheUsage() {
        packWith("one", Command.named("give").argument(Argument.word("label")).argument(Argument.integer("count", 1, 10))
                .argument(Argument.word("note").orElse("none")).runs(context -> seen.add("ran")));
        standIn(packsByName.get("one"));

        run("give apple");

        assertEquals(List.of("Missing count"), sender.errors);
        assertEquals(List.of("Use: give <label> <count> [note]"), sender.infos);
        assertTrue(seen.isEmpty());
    }

    @Test
    public void reportsTooManyArgumentsAndInvalidValues() {
        packWith("one", Command.named("give").argument(Argument.integer("count", 1, 10))
                .argument(Argument.choice("mode", Mode.class).orElse(Mode.SLOW)).runs(context -> seen.add("ran")));
        standIn(packsByName.get("one"));

        run("give 3 slow extra words");
        run("give 11");
        run("give three");
        run("give 3 sideways");

        assertEquals(List.of("Too many arguments: extra words", "count must be a whole number from 1 to 10",
                "count must be a whole number", "mode must be one of fast-pass, slow"), sender.errors);
        assertTrue(seen.isEmpty());
    }

    @Test
    public void textTakesTheRestOfTheLineJoinedBySingleSpaces() {
        Argument<String> title = Argument.word("title");
        Argument<String> body = Argument.text("body");
        packWith("one", Command.named("note").argument(title).argument(body)
                .runs(context -> seen.add(context.get(title) + "|" + context.get(body))));
        standIn(packsByName.get("one"));

        run("note head  several   spaced words");

        assertEquals(List.of("head|several spaced words"), seen);
    }

    @Test
    public void aWordThatNamesASubCommandSelectsItAndAliasesWork() {
        packWith("one", Command.named("tools").alias("t").child(recording("list").alias("ls")).child(recording("clear")));
        standIn(packsByName.get("one"));

        run("tools list");
        run("t ls");
        run("tools clear");

        assertEquals(List.of("list", "list", "clear"), seen);
    }

    @Test
    public void aCommandWithoutAnActionListsItsSubCommandsAndRejectsUnknownOnes() {
        packWith("one", Command.named("tools").describe("Pack tools")
                .child(Command.named("list").describe("List things").argument(Argument.word("filter").orElse("all")))
                .child(recording("clear")));
        standIn(packsByName.get("one"));

        run("tools");
        run("tools frobnicate");

        assertEquals(List.of("tools: Pack tools", "  tools list [filter] - List things", "  tools clear",
                "tools: Pack tools", "  tools list [filter] - List things", "  tools clear"), sender.infos);
        assertEquals(List.of("Unknown tools sub-command: frobnicate"), sender.errors);
    }

    @Test
    public void aLineThatNoRootOwnsIsAnErrorThatPointsToTheList() {
        packWith("one", recording("tools"));
        standIn(packsByName.get("one"));

        run("elsewhere now");

        assertEquals(List.of("No module command named elsewhere"), sender.errors);
        assertEquals(List.of("List them with /iris module"), sender.infos);
        assertTrue(seen.isEmpty());
    }

    @Test
    public void theBranchAnswersToModuleAndItsAliasInAnyCase() {
        assertTrue(ToolingCommands.isBranchWord("module"));
        assertTrue(ToolingCommands.isBranchWord("M"));
        assertFalse(ToolingCommands.isBranchWord("structure"));
        assertFalse(ToolingCommands.isBranchWord("mod"));
    }

    @Test
    public void noCommandWordListsTheMountedRootsWithTheirCommandsForTheSender() {
        packWith("one",
                Command.named("tools").describe("Pack tools")
                        .child(Command.named("list").describe("List things").argument(Argument.word("filter").orElse("all"))
                                .runs(context -> { }))
                        .child(Command.named("clear").runs(context -> { })),
                Command.named("ping").describe("Answer").openToEveryone().runs(context -> { }),
                Command.named("shape").runs(context -> { }));
        standIn(packsByName.get("one"));

        sender.operator = false;
        run("pack=one");

        assertEquals(List.of("Module commands of pack one, run as /iris module <command>:", "  ping - Answer"), sender.infos);
        assertTrue(sender.errors.isEmpty());
    }

    @Test
    public void theListNamesEveryCommandOfAnOperatorAndSaysSoWhenAPackHasNone() {
        packWith("one",
                Command.named("tools")
                        .child(Command.named("list").describe("List things").argument(Argument.word("filter").orElse("all"))
                                .runs(context -> { }))
                        .child(Command.named("clear").runs(context -> { })),
                Command.named("ping").runs(context -> { }));
        packWith("bare");

        run("pack=one");
        run("pack=bare");

        assertEquals(List.of("Module commands of pack one, run as /iris module <command>:",
                "  ping", "  tools list [filter] - List things", "  tools clear",
                "No module of pack bare has commands for you"), sender.infos);
    }

    @Test
    public void packOptionIsTakenOutOfTheLineWhereverItStands() {
        Argument<String> label = Argument.word("label");
        packWith("alpha", Command.named("give").argument(label).runs(context -> seen.add(context.get(label))));
        packWith("beta", Command.named("other").runs(context -> seen.add("beta")));

        run("pack=alpha give apple");
        run("give pack=alpha pear");
        run("give plum pack=alpha");

        assertEquals(List.of("apple", "pear", "plum"), seen);
    }

    @Test
    public void aNamedPackWinsOverTheSendersWorldAndAnUnknownOneIsAnError() {
        packWith("alpha", Command.named("give").runs(context -> seen.add("alpha")));
        packWith("beta", Command.named("give").runs(context -> seen.add("beta")));
        standIn(packsByName.get("beta"));

        run("give pack=alpha");
        run("give");
        run("give pack=missing");
        run("give pack=alpha pack=beta");

        assertEquals(List.of("alpha", "beta"), seen);
        assertEquals(List.of("No pack named missing", "Name one pack only"), sender.errors);
    }

    @Test
    public void withoutAPackOrAWorldItFallsBackToTheOpenStudioThenAsksForAPack() {
        packWith("alpha", Command.named("give").runs(context -> seen.add("alpha")));

        run("give");
        sender.studio = Optional.of(packsByName.get("alpha"));
        run("give");

        assertEquals(List.of("Name a pack: pack=<name>"), sender.errors);
        assertEquals(List.of("alpha"), seen);
    }

    @Test
    public void theWorldIsOnTheContextOnlyWhenItGeneratesWithTheResolvedPack() {
        PackWorld world = mock(PackWorld.class);
        AtomicReference<Optional<PackWorld>> given = new AtomicReference<>();
        packWith("alpha", Command.named("where").runs(context -> given.set(context.world())));
        packWith("beta", Command.named("where").runs(context -> given.set(context.world())));
        sender.world = Optional.of(new SenderWorld(packsByName.get("alpha"), world));

        run("where");
        assertSame(world, given.get().orElseThrow());

        run("where pack=beta");
        assertTrue(given.get().isEmpty());

        run("where pack=alpha");
        assertSame(world, given.get().orElseThrow());
    }

    @Test
    public void onlyOperatorsRunACommandUnlessItIsOpenToEveryone() {
        packWith("alpha", recording("locked"), Command.named("public").openToEveryone().runs(context -> seen.add("public")));
        standIn(packsByName.get("alpha"));
        sender.operator = false;

        run("locked");
        run("public");
        assertFalse(dispatcher.executeIfOpenToEveryone(sender, words("locked")));
        assertTrue(dispatcher.executeIfOpenToEveryone(sender, words("public")));

        assertEquals(List.of("public", "public"), seen);
        assertEquals(List.of("Only operators may use locked"), sender.errors);
    }

    @Test
    public void aSenderWithoutTheRootPermissionLearnsNothingFromAFailedLine() {
        packWith("alpha", Command.named("public").openToEveryone().argument(Argument.integer("n", 1, 2)).runs(context -> seen.add("ran")));
        standIn(packsByName.get("alpha"));
        sender.operator = false;

        assertFalse(dispatcher.executeIfOpenToEveryone(sender, words("public 9")));
        assertFalse(dispatcher.executeIfOpenToEveryone(sender, words("missing")));
        assertFalse(dispatcher.executeIfOpenToEveryone(sender, words("public pack=nowhere")));
        assertFalse(dispatcher.executeIfOpenToEveryone(sender, words("")));

        assertTrue(sender.errors.isEmpty());
        assertTrue(sender.infos.isEmpty());
    }

    @Test
    public void anActionRunsOnAWorkerThreadAndWhatItThrowsReachesTheSender() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor(task -> new Thread(task, "test-worker"));
        try {
            ToolingCommands threaded = new ToolingCommands(packDirectory(), workers, () -> registries);
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(2);
            packWith("alpha",
                    Command.named("where").runs(context -> {
                        threadName.set(Thread.currentThread().getName());
                        done.countDown();
                    }),
                    Command.named("boom").runs(context -> {
                        done.countDown();
                        throw new IllegalStateException("kaput");
                    }));
            standIn(packsByName.get("alpha"));

            threaded.execute(sender, words("where"));
            threaded.execute(sender, words("boom"));

            assertTrue(done.await(10, TimeUnit.SECONDS));
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            assertEquals("test-worker", threadName.get());
            assertNotEquals(Thread.currentThread().getName(), threadName.get());
            assertEquals(List.of("boom failed: kaput"), sender.errors);
        } finally {
            workers.shutdownNow();
        }
    }

    private PackDirectory packDirectory() {
        return new PackDirectory() {
            @Override
            public Optional<ServedPack> find(String name) {
                return Optional.ofNullable(packsByName.get(name));
            }

            @Override
            public List<String> names() {
                return List.copyOf(packsByName.keySet());
            }
        };
    }

    @Test
    public void aPackWhoseModulesDidNotLoadFailsTheLineWithTheReason() {
        FakePack broken = new FakePack("broken", null, null);
        packsByName.put("broken", broken);

        run("anything pack=broken");

        assertEquals(List.of("The modules of pack broken did not load: Command root tools is registered by module a already"),
                sender.errors);
    }

    @Test
    public void resourceAndRegisteredArgumentsReadThePackAndThePlatform() {
        Argument<ResourceId> note = Argument.resource("note", NOTES);
        Argument<ResourceId> structure = Argument.registered("structure", Reference.STRUCTURE);
        packWith("alpha", Command.named("show").argument(note).argument(structure)
                .runs(context -> seen.add(context.get(note) + "|" + context.get(structure))));
        standIn(packsByName.get("alpha"));
        registries = Optional.of(reference -> reference == Reference.STRUCTURE
                ? Set.of(new ResourceId("minecraft", "village_plains")) : Set.of());

        run("show alpha village_plains");
        run("show other:gamma minecraft:village_plains");
        run("show nowhere village_plains");
        run("show alpha igloo");

        assertEquals(List.of("test:alpha|minecraft:village_plains", "other:gamma|minecraft:village_plains"), seen);
        assertEquals(List.of("No notes resource 'nowhere' in this pack", "No structure 'igloo' is registered"), sender.errors);
    }

    @Test
    public void completesRootsSubCommandsChoicesAndResources() {
        packWith("alpha",
                Command.named("tools").child(recording("list")).child(recording("clear")).child(Command.named("mode")
                        .argument(Argument.choice("mode", Mode.class)).argument(Argument.resource("note", NOTES))
                        .argument(Argument.text("rest")).runs(context -> { })),
                recording("tidy"));
        packWith("beta", recording("betaonly"));
        standIn(packsByName.get("alpha"));

        assertEquals(List.of("tidy", "tools"), complete("t"));
        assertEquals(List.of("list", "clear", "mode"), complete("tools "));
        assertEquals(List.of("clear"), complete("tools c"));
        assertEquals(List.of("fast-pass", "slow"), complete("tools mode "));
        assertEquals(List.of("slow"), complete("tools mode s"));
        assertEquals(List.of("alpha", "beta", "other:gamma"), complete("tools mode slow "));
        assertEquals(List.of("beta"), complete("tools mode slow b"));
        assertEquals(List.of(), complete("tools mode slow beta "));
        assertEquals(List.of(), complete("tools mode sideways "));
        assertEquals(List.of(), complete("tools mode slow beta typing more"));
    }

    @Test
    public void completesPackNamesAndDoesNotOfferWhatTheSenderMayNotRun() {
        packWith("alpha", recording("locked"), Command.named("public").openToEveryone().runs(context -> { }));
        packWith("alpine", recording("other"));
        sender.operator = false;
        standIn(packsByName.get("alpha"));

        assertEquals(List.of("pack=alpha", "pack=alpine"), complete("pack=al"));
        assertEquals(List.of("pack=alpine"), complete("pack=alpi"));
        assertEquals(List.of("public"), complete(""));
        assertEquals(List.of(), complete("locked "));
    }

    @Test
    public void withoutAPackInReachCompletionOffersThePackOption() {
        packWith("alpha", recording("give"));
        packWith("beta", recording("take"));

        assertEquals(List.of("pack=alpha", "pack=beta"), complete(""));
        assertEquals(List.of("give"), complete("pack=alpha "));
    }

    @Test
    public void completionNeverThrows() {
        assertEquals(List.of(), complete("tools "));
        assertEquals(List.of(), dispatcher.complete(sender, List.of()));
        assertEquals(List.of(), complete("tools pack=a pack=b "));
    }

    /** A pack of the test: its catalog's root commands, or the failure of its modules when it has none. */
    private static final class FakePack implements ServedPack {
        private final String name;
        private final List<CommandRegistration> commands;
        private final ToolPack toolPack;

        FakePack(String name, List<CommandRegistration> commands, ToolPack toolPack) {
            this.name = name;
            this.commands = commands;
            this.toolPack = toolPack;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<CommandRegistration> commands() {
            if (commands == null) {
                throw new IllegalStateException("The modules of pack " + name
                        + " did not load: Command root tools is registered by module a already");
            }
            return commands;
        }

        @Override
        public ToolPack toolPack(ModuleLog log) {
            return toolPack;
        }
    }
}
