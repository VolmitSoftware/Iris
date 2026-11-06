package com.volmit.tooling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One command: a name, sub-commands, and at most one action over typed arguments. Immutable; each method returns a
 * changed copy. A module registers its root command with {@link ToolingRegistry#registerCommand}, and the host mounts it
 * below its own command root, such as {@code /iris structure list}. A word that names a sub-command always selects it,
 * so give a command that has children either no arguments or arguments no child is named like.
 */
public final class Command {
    private static final Pattern NAME = Pattern.compile("[a-z0-9-]+");

    private final String name;
    private final List<String> aliases;
    private final String description;
    private final List<Command> children;
    private final List<Argument<?>> arguments;
    private final CommandAction action;
    private final boolean openToEveryone;

    private Command(String name, List<String> aliases, String description, List<Command> children,
                     List<Argument<?>> arguments, CommandAction action, boolean openToEveryone) {
        this.name = name;
        this.aliases = List.copyOf(aliases);
        this.description = description;
        this.children = List.copyOf(children);
        this.arguments = List.copyOf(arguments);
        this.action = action;
        this.openToEveryone = openToEveryone;
    }

    /** A command named {@code name}: lower case letters, digits and '-'. */
    public static Command named(String name) {
        requireWord(name, "Command name");
        return new Command(name, List.of(), "", List.of(), List.of(), null, false);
    }

    /** This command, also answering to {@code aliases}, which are written like a name and differ from it and from each other. */
    public Command alias(String... aliases) {
        var all = new ArrayList<>(this.aliases);
        for (var alias : aliases) {
            requireWord(alias, "Alias of command " + name);
            if (alias.equals(name) || all.contains(alias)) {
                throw new IllegalArgumentException("Command " + name + " answers to " + alias + " already");
            }
            all.add(alias);
        }
        return new Command(name, all, description, children, arguments, action, openToEveryone);
    }

    /** This command with the one-line help text the host shows. */
    public Command describe(String description) {
        return new Command(name, aliases, requireText(description, "Description of command " + name), children, arguments,
                action, openToEveryone);
    }

    /** This command with {@code child} as a sub-command; a child that shares a name or alias with another child fails. */
    public Command child(Command child) {
        var added = new ArrayList<>(children);
        for (var existing : children) {
            for (var word : child.words()) {
                if (existing.answersTo(word)) {
                    throw new IllegalArgumentException("Command " + name + " has a sub-command answering to " + word
                            + " already (" + existing.name + "), so " + child.name + " cannot use it");
                }
            }
        }
        added.add(child);
        return new Command(name, aliases, description, added, arguments, action, openToEveryone);
    }

    /** This command taking {@code argument} next; an optional argument may not come before a required one, nothing may
     * follow an argument that takes the rest of the line, and two arguments cannot share a name.
     */
    public Command argument(Argument<?> argument) {
        var added = new ArrayList<>(arguments);
        if (!added.isEmpty() && added.getLast().takesRestOfLine()) {
            throw new IllegalArgumentException("Argument " + argument.name() + " of command " + name + " follows "
                    + added.getLast().name() + ", which takes the rest of the line");
        }
        if (!added.isEmpty() && added.getLast().isOptional() && !argument.isOptional()) {
            throw new IllegalArgumentException("Required argument " + argument.name() + " of command " + name
                    + " follows the optional argument " + added.getLast().name());
        }
        if (added.stream().anyMatch(existing -> existing.name().equals(argument.name()))) {
            throw new IllegalArgumentException("Command " + name + " has an argument named " + argument.name() + " already");
        }
        added.add(argument);
        return new Command(name, aliases, description, children, added, action, openToEveryone);
    }

    /** This command running {@code action}; a command without an action only groups its children. */
    public Command runs(CommandAction action) {
        return new Command(name, aliases, description, children, arguments, Objects.requireNonNull(action, "action"),
                openToEveryone);
    }

    /** This command usable by every sender; by default only operators may run it. */
    public Command openToEveryone() {
        return new Command(name, aliases, description, children, arguments, action, true);
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    /** The help text, empty when the module gave none. */
    public String description() {
        return description;
    }

    public List<Command> children() {
        return children;
    }

    public List<Argument<?>> arguments() {
        return arguments;
    }

    /** What the command runs; empty for a command that only groups its children. */
    public Optional<CommandAction> action() {
        return Optional.ofNullable(action);
    }

    /** Whether every sender may run this command, not only operators. */
    public boolean isOpenToEveryone() {
        return openToEveryone;
    }

    /** Whether {@code word} is this command's name or one of its aliases. */
    public boolean answersTo(String word) {
        return name.equals(word) || aliases.contains(word);
    }

    /** The name and the aliases. */
    public List<String> words() {
        var words = new ArrayList<String>();
        words.add(name);
        words.addAll(aliases);
        return words;
    }

    @Override
    public String toString() {
        return "command " + name;
    }

    private static void requireWord(String word, String what) {
        if (word == null || !NAME.matcher(word).matches()) {
            throw new IllegalArgumentException(what + " '" + word + "' must be lower case letters, digits and '-'");
        }
    }

    private static String requireText(String text, String what) {
        return Objects.requireNonNull(text, what);
    }
}
