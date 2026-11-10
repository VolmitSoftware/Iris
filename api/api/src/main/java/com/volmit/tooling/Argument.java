package com.volmit.tooling;

import com.volmit.config.Reference;
import com.volmit.resource.PlatformRegistries;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * One typed argument of a {@link Command}: its name, how text becomes a value, what the host completes, and an optional
 * default. Immutable; each method returns a changed copy. Modules declare arguments and read their values with
 * {@link CommandContext#get}; the host calls {@link #parse} and {@link #complete}.
 */
public final class Argument<T> {
    private static final String CHOICE_SEPARATOR = "-";
    private static final String PLATFORM_NAMESPACE = "minecraft";

    private final String name;
    private final String description;
    private final Parser<T> parser;
    private final Completer completer;
    private final boolean takesRestOfLine;
    private final T fallback;
    private final Suggestions suggestions;

    private Argument(String name, String description, Parser<T> parser, Completer completer, boolean takesRestOfLine,
                     T fallback, Suggestions suggestions) {
        this.name = name;
        this.description = description;
        this.parser = parser;
        this.completer = completer;
        this.takesRestOfLine = takesRestOfLine;
        this.fallback = fallback;
        this.suggestions = suggestions;
    }

    /** One word. */
    public static Argument<String> word(String name) {
        return of(name, false, (text, context) -> text, (partial, context) -> List.of());
    }

    /** The rest of the line. Only as the last argument. */
    public static Argument<String> text(String name) {
        return of(name, true, (text, context) -> text, (partial, context) -> List.of());
    }

    /** A whole number from {@code min} to {@code max}. */
    public static Argument<Long> integer(String name, long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("Argument " + name + " allows no number: " + min + " is above " + max);
        }
        return of(name, false, (text, context) -> {
            var value = parseWholeNumber(name, text);
            if (value < min || value > max) {
                throw new IllegalArgumentException(name + " must be a whole number from " + min + " to " + max);
            }
            return value;
        }, (partial, context) -> List.of());
    }

    /** A number from {@code min} to {@code max}. */
    public static Argument<Double> decimal(String name, double min, double max) {
        if (!(min <= max)) {
            throw new IllegalArgumentException("Argument " + name + " allows no number: " + min + " is above " + max);
        }
        return of(name, false, (text, context) -> {
            var value = parseNumber(name, text);
            if (!(value >= min && value <= max)) {
                throw new IllegalArgumentException(name + " must be a number from " + min + " to " + max);
            }
            return value;
        }, (partial, context) -> List.of());
    }

    /** {@code true} or {@code false}. */
    public static Argument<Boolean> flag(String name) {
        return of(name, false, (text, context) -> {
            if (text.equalsIgnoreCase("true")) return true;
            if (text.equalsIgnoreCase("false")) return false;
            throw new IllegalArgumentException(name + " must be true or false");
        }, (partial, context) -> List.of("true", "false"));
    }

    /** One constant of {@code type}, written in lower case with '-'. */
    public static <E extends Enum<E>> Argument<E> choice(String name, Class<E> type) {
        var words = Arrays.stream(type.getEnumConstants()).map(Argument::choiceWord).toList();
        return of(name, false, (text, context) -> {
            for (var constant : type.getEnumConstants()) {
                if (choiceWord(constant).equals(text.toLowerCase(Locale.ROOT))) return constant;
            }
            throw new IllegalArgumentException(name + " must be one of " + String.join(", ", words));
        }, (partial, context) -> words);
    }

    /** The id of a resource of {@code kind} in the resolved pack; completes {@link com.volmit.resource.PackResources#ids}.
     * Written as the key the pack's files name ({@code villages/plains}) or as the full id.
     */
    public static Argument<ResourceId> resource(String name, ResourceKind<?> kind) {
        return of(name, false, (text, context) -> {
            var resources = context.pack().resources();
            var id = text.indexOf(':') < 0 ? resources.idOf(text) : ResourceId.parse(text);
            if (!resources.ids(kind).contains(id)) {
                throw new IllegalArgumentException("No " + kind.folder() + " resource '" + text + "' in this pack");
            }
            return id;
        }, (partial, context) -> {
            var resources = context.pack().resources();
            var packNamespace = resources.packId().namespace();
            return resources.ids(kind).stream()
                    .map(id -> id.namespace().equals(packNamespace) ? id.path() : id.toString())
                    .toList();
        });
    }

    /** One word that {@code reader} turns into the value, for a value only the command's module can check, such as a resource
     * of a kind its module's neighbour registers. {@code reader} fails with an IllegalArgumentException that says why; the
     * argument completes nothing until {@link #suggesting} gives it candidates.
     */
    public static <T> Argument<T> reading(String name, Reader<T> reader) {
        Objects.requireNonNull(reader, "reader");
        return of(name, false, reader::read, (partial, context) -> List.of());
    }

    /** An id registered in the platform's {@code registry}, such as {@link Reference#STRUCTURE}; completes
     * {@link PlatformRegistries#ids}. Written as the platform writes it, {@code minecraft:village_plains}, or without the
     * {@code minecraft:} namespace.
     */
    public static Argument<ResourceId> registered(String name, Reference registry) {
        Objects.requireNonNull(registry, "registry");
        return of(name, false, (text, context) -> {
            var id = ResourceId.ofPlatformKey(text);
            if (!registeredIds(context, registry).contains(id)) {
                throw new IllegalArgumentException("No " + registryName(registry) + " '" + text + "' is registered");
            }
            return id;
        }, (partial, context) -> registeredIds(context, registry).stream().flatMap(Argument::platformWritings).toList());
    }

    private static Stream<String> platformWritings(ResourceId id) {
        return id.namespace().equals(PLATFORM_NAMESPACE) ? Stream.of(id.toString(), id.path()) : Stream.of(id.toString());
    }

    /** This argument, optional, with {@code fallback} when the sender leaves it out. */
    public Argument<T> orElse(T fallback) {
        return new Argument<>(name, description, parser, completer, takesRestOfLine,
                Objects.requireNonNull(fallback, "fallback"), suggestions);
    }

    /** This argument with the help text the host shows. */
    public Argument<T> describe(String description) {
        return new Argument<>(name, Objects.requireNonNull(description, "description"), parser, completer, takesRestOfLine,
                fallback, suggestions);
    }

    /** This argument completing what {@code suggestions} returns as well, such as the bays of an open studio session. */
    public Argument<T> suggesting(Suggestions suggestions) {
        return new Argument<>(name, description, parser, completer, takesRestOfLine, fallback,
                Objects.requireNonNull(suggestions, "suggestions"));
    }

    public String name() {
        return name;
    }

    /** The help text, empty when the module gave none. */
    public String description() {
        return description;
    }

    /** Whether the argument takes every word that is left, so the host passes it unsplit. */
    public boolean takesRestOfLine() {
        return takesRestOfLine;
    }

    /** Whether the sender may leave the argument out. */
    public boolean isOptional() {
        return fallback != null;
    }

    /** The value used when the sender leaves an optional argument out; null for a required one. */
    public T fallback() {
        return fallback;
    }

    /**
     * The value {@code text} stands for. For the host.
     *
     * @throws IllegalArgumentException with a message for the sender when {@code text} is no such value
     */
    public T parse(String text, CommandContext context) {
        return parser.parse(text, context);
    }

    /** The completions for {@code partial}, the word typed so far: this argument's own and the module's, sorted and without
     * repeats. For the host.
     */
    public List<String> complete(String partial, CommandContext context) {
        var lowered = partial.toLowerCase(Locale.ROOT);
        var own = completer.complete(partial, context).stream();
        var added = suggestions == null ? Stream.<String>empty() : suggestions.suggest(context).stream();
        return Stream.concat(own, added)
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lowered))
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public String toString() {
        return "argument " + name;
    }

    private static <T> Argument<T> of(String name, boolean takesRestOfLine, Parser<T> parser, Completer completer) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Argument name '" + name + "' must be one word");
        }
        return new Argument<>(name, "", parser, completer, takesRestOfLine, null, null);
    }

    private static String choiceWord(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace("_", CHOICE_SEPARATOR);
    }

    private static long parseWholeNumber(String name, String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(name + " must be a whole number");
        }
    }

    private static double parseNumber(String name, String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    private static Set<ResourceId> registeredIds(CommandContext context, Reference registry) {
        return context.platform()
                .orElseThrow(() -> new IllegalArgumentException("This server has no registries to read " + registryName(registry) + " ids from"))
                .ids(registry);
    }

    private static String registryName(Reference registry) {
        return registry.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Turns the word a sender typed into the argument's value. */
    @FunctionalInterface
    public interface Reader<T> {
        T read(String text, CommandContext context);
    }

    private interface Parser<T> {
        T parse(String text, CommandContext context);
    }

    private interface Completer {
        List<String> complete(String partial, CommandContext context);
    }
}
