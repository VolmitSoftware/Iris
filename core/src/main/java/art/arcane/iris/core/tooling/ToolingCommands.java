/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.tooling;

import art.arcane.iris.core.modules.IrisValidationContext;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import com.volmit.host.CommandRegistration;
import com.volmit.resource.PlatformRegistries;
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;
import com.volmit.tooling.PlatformStructures;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The one dispatcher of the commands modules register ({@link com.volmit.tooling.ToolingRegistry#registerCommand}),
 * which both adapters mount as the {@code /iris module <root> <command...>} branch (alias {@code m}); it takes the words
 * after the branch word. It resolves the pack first, because the commands
 * belong to one pack's module set: {@code pack=<name>} anywhere in the line, else the pack of the sender's world, else the
 * pack of the sender's open studio. Then it walks the module's command tree, parses the arguments and runs the action on a
 * worker thread, so no command runs on the server's main thread or a region thread.
 * <p>
 * Words are separated by whitespace; there is no quoting, and an argument that takes the rest of the line gets the
 * remaining words joined by single spaces.
 */
public final class ToolingCommands {
    public static final String BRANCH = "module";
    public static final String BRANCH_ALIAS = "m";
    private static final String NAME_A_PACK = "Name a pack: " + CommandLine.PACK_OPTION + "<name>";
    private static final String WORKER_NAME_PREFIX = "Iris-Tooling-";

    private final PackDirectory packs;
    private final Executor workers;
    private final Supplier<Optional<PlatformRegistries>> platform;
    private final Supplier<Optional<PlatformStructures>> platformStructures;

    public ToolingCommands(PackDirectory packs, Executor workers, Supplier<Optional<PlatformRegistries>> platform) {
        this(packs, workers, platform, Optional::empty);
    }

    public ToolingCommands(PackDirectory packs, Executor workers, Supplier<Optional<PlatformRegistries>> platform,
                           Supplier<Optional<PlatformStructures>> platformStructures) {
        this.packs = packs;
        this.workers = workers;
        this.platform = platform;
        this.platformStructures = platformStructures;
    }

    /** The dispatcher of the bound platform, with daemon worker threads created as commands need them. */
    public static ToolingCommands forBoundPlatform() {
        AtomicInteger workerNumber = new AtomicInteger();
        ExecutorService workers = Executors.newCachedThreadPool(task -> {
            Thread worker = new Thread(task, WORKER_NAME_PREFIX + workerNumber.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        });
        return new ToolingCommands(PackDirectory.ofBoundPlatform(), workers, IrisValidationContext::boundPlatformRegistries,
                () -> Optional.ofNullable(IrisServices.getOrNull(PlatformStructures.class)));
    }

    /** Whether {@code word} opens the module branch of the {@code /iris} root, {@code module} or its alias {@code m}. */
    public static boolean isBranchWord(String word) {
        return BRANCH.equalsIgnoreCase(word) || BRANCH_ALIAS.equalsIgnoreCase(word);
    }

    /**
     * Runs the command the words after the branch word name: the pack, the tree and the arguments are resolved on the
     * calling thread and the action runs on a worker. No command word lists the commands of the pack's modules, and a
     * problem with the line reaches the sender as an error.
     */
    public void execute(ToolingSender sender, List<String> words) {
        run(sender, words, false);
    }

    /**
     * Like {@link #execute}, for a sender the platform denies the root command: runs only a command opened to everyone,
     * and tells the sender nothing otherwise.
     *
     * @return whether it ran such a command
     */
    public boolean executeIfOpenToEveryone(ToolingSender sender, List<String> words) {
        return run(sender, words, true);
    }

    /** Like {@link #execute}, with the resolving done on a worker too, for callers that must not block. */
    public void executeOffThread(ToolingSender sender, List<String> words) {
        workers.execute(() -> execute(sender, words));
    }

    /** The words that can follow {@code words}, whose last word is the one being typed. */
    public List<String> complete(ToolingSender sender, List<String> words) {
        if (words.isEmpty()) {
            return List.of();
        }
        String partial = words.getLast();
        if (partial.startsWith(CommandLine.PACK_OPTION)) {
            return packs.names().stream().map(name -> CommandLine.PACK_OPTION + name).filter(option -> option.startsWith(partial)).toList();
        }
        try {
            CommandLine typed = CommandLine.of(words.subList(0, words.size() - 1));
            ResolvedPack pack = resolvePack(sender, typed.packName());
            List<CommandRegistration> roots = rootsOf(pack.served());
            if (typed.words().isEmpty()) {
                return roots.stream()
                        .filter(root -> mayRun(sender, root.command()))
                        .map(root -> root.command().name())
                        .filter(name -> name.startsWith(partial))
                        .toList();
            }
            Optional<LocatedCommand> located = LocatedCommand.locate(roots, typed.words());
            if (located.isEmpty() || !mayRun(sender, located.get().command())) {
                return List.of();
            }
            return completions(sender, located.get(), partial, contextOf(sender, pack, located.get()));
        } catch (CommandFailure | RuntimeException cannotComplete) {
            return List.of();
        }
    }

    private boolean run(ToolingSender sender, List<String> tokens, boolean onlyIfOpenToEveryone) {
        try {
            CommandLine line = CommandLine.of(tokens);
            ResolvedPack pack = resolvePack(sender, line.packName());
            List<CommandRegistration> roots = rootsOf(pack.served());
            if (line.words().isEmpty() && onlyIfOpenToEveryone) {
                return false;
            }
            if (line.words().isEmpty()) {
                describeMountedRoots(sender, pack.served(), roots);
                return true;
            }
            Optional<LocatedCommand> located = LocatedCommand.locate(roots, line.words());
            if (located.isEmpty() && onlyIfOpenToEveryone) {
                return false;
            }
            if (located.isEmpty()) {
                throw new CommandFailure("No module command named " + line.words().getFirst(), "List them with /iris module");
            }
            if (onlyIfOpenToEveryone && !located.get().command().isOpenToEveryone()) {
                return false;
            }
            launch(sender, pack, located.get());
            return true;
        } catch (CommandFailure failure) {
            if (onlyIfOpenToEveryone) {
                return false;
            }
            sender.error(failure.getMessage());
            failure.usage().ifPresent(sender::info);
            return true;
        } catch (RuntimeException failure) {
            IrisLogging.reportError(failure);
            sender.error("Command failed: " + reasonOf(failure));
            return true;
        }
    }

    private void launch(ToolingSender sender, ResolvedPack pack, LocatedCommand located) throws CommandFailure {
        if (!mayRun(sender, located.command())) {
            throw new CommandFailure("Only operators may use " + located.pathText());
        }
        if (located.command().action().isEmpty()) {
            describeGroup(sender, located);
            return;
        }
        CommandRun context = contextOf(sender, pack, located);
        parseArguments(located, context);
        workers.execute(() -> runAction(located, context));
    }

    private static void runAction(LocatedCommand located, CommandRun context) {
        try {
            located.command().action().orElseThrow().run(context);
        } catch (Throwable failure) {
            context.sender().error(located.pathText() + " failed: " + reasonOf(failure));
            located.root().module().log().error("Command " + located.pathText() + " failed", failure);
        }
    }

    private static String reasonOf(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static boolean mayRun(ToolingSender sender, Command command) {
        return sender.isOperator() || command.isOpenToEveryone();
    }

    private ResolvedPack resolvePack(ToolingSender sender, Optional<String> packName) throws CommandFailure {
        if (packName.isPresent()) {
            ServedPack named = packs.find(packName.get()).orElseThrow(() -> new CommandFailure("No pack named " + packName.get()));
            Optional<PackWorld> world = sender.world().filter(standing -> standing.pack().equals(named)).map(SenderWorld::world);
            return new ResolvedPack(named, world);
        }
        Optional<SenderWorld> standing = sender.world();
        if (standing.isPresent()) {
            return new ResolvedPack(standing.get().pack(), Optional.of(standing.get().world()));
        }
        ServedPack ofStudio = sender.openStudioPack().orElseThrow(() -> new CommandFailure(NAME_A_PACK));
        return new ResolvedPack(ofStudio, Optional.empty());
    }

    private static List<CommandRegistration> rootsOf(ServedPack pack) throws CommandFailure {
        try {
            return pack.commands();
        } catch (IllegalStateException modulesFailed) {
            throw new CommandFailure(modulesFailed.getMessage());
        }
    }

    private CommandRun contextOf(ToolingSender sender, ResolvedPack pack, LocatedCommand located) {
        return new CommandRun(sender, pack.served().toolPack(located.root().module().log()), pack.world(), platform.get(),
                platformStructures.get());
    }

    private static void parseArguments(LocatedCommand located, CommandRun context) throws CommandFailure {
        List<String> typed = located.arguments();
        int next = 0;
        for (Argument<?> argument : located.command().arguments()) {
            if (next >= typed.size()) {
                if (argument.isOptional()) continue;
                throw new CommandFailure("Missing " + argument.name(), "Use: " + located.usage());
            }
            String text = argument.takesRestOfLine() ? String.join(" ", typed.subList(next, typed.size())) : typed.get(next);
            next = argument.takesRestOfLine() ? typed.size() : next + 1;
            parseInto(context, argument, text, located);
        }
        if (next < typed.size()) {
            throw new CommandFailure("Too many arguments: " + String.join(" ", typed.subList(next, typed.size())),
                    "Use: " + located.usage());
        }
    }

    private static <T> void parseInto(CommandRun context, Argument<T> argument, String text, LocatedCommand located) throws CommandFailure {
        try {
            context.put(argument, argument.parse(text, context));
        } catch (IllegalArgumentException invalid) {
            throw new CommandFailure(invalid.getMessage(), "Use: " + located.usage());
        }
    }

    private static List<String> completions(ToolingSender sender, LocatedCommand located, String partial, CommandRun context) {
        List<String> suggestions = new ArrayList<>();
        List<String> typed = located.arguments();
        if (typed.isEmpty()) {
            located.command().children().stream()
                    .filter(child -> mayRun(sender, child))
                    .map(Command::name)
                    .filter(name -> name.startsWith(partial))
                    .forEach(suggestions::add);
        }
        List<Argument<?>> arguments = located.command().arguments();
        for (int index = 0; index < typed.size() && index < arguments.size(); index++) {
            Argument<?> argument = arguments.get(index);
            if (argument.takesRestOfLine()) {
                return List.of();
            }
            if (!parsesInto(context, argument, typed.get(index))) {
                return List.of();
            }
        }
        if (typed.size() < arguments.size()) {
            suggestions.addAll(arguments.get(typed.size()).complete(partial, context));
        }
        return suggestions.stream().distinct().toList();
    }

    private static <T> boolean parsesInto(CommandRun context, Argument<T> argument, String text) {
        try {
            context.put(argument, argument.parse(text, context));
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static void describeMountedRoots(ToolingSender sender, ServedPack pack, List<CommandRegistration> roots) {
        List<String> lines = new ArrayList<>();
        for (CommandRegistration root : roots) {
            collectUsageLines(sender, List.of(root.command().name()), root.command(), lines);
        }
        if (lines.isEmpty()) {
            sender.info("No module of pack " + pack.name() + " has commands for you");
            return;
        }
        sender.info("Module commands of pack " + pack.name() + ", run as /iris module <command>:");
        lines.forEach(line -> sender.info("  " + line));
    }

    private static void collectUsageLines(ToolingSender sender, List<String> path, Command command, List<String> lines) {
        if (!mayRun(sender, command)) {
            return;
        }
        if (command.action().isPresent()) {
            lines.add(LocatedCommand.usageOf(path, command) + (command.description().isEmpty() ? "" : " - " + command.description()));
        }
        for (Command child : command.children()) {
            List<String> childPath = new ArrayList<>(path);
            childPath.add(child.name());
            collectUsageLines(sender, childPath, child, lines);
        }
    }

    private static void describeGroup(ToolingSender sender, LocatedCommand located) {
        if (!located.arguments().isEmpty()) {
            sender.error("Unknown " + located.pathText() + " sub-command: " + located.arguments().getFirst());
        }
        String description = located.command().description();
        sender.info(located.pathText() + (description.isEmpty() ? "" : ": " + description));
        for (Command child : located.command().children()) {
            if (!mayRun(sender, child)) continue;
            List<String> childPath = new ArrayList<>(located.path());
            childPath.add(child.name());
            sender.info("  " + LocatedCommand.usageOf(childPath, child) + (child.description().isEmpty() ? "" : " - " + child.description()));
        }
    }

    private record ResolvedPack(ServedPack served, Optional<PackWorld> world) {}
}
