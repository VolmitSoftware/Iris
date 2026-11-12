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

package art.arcane.iris.builtin.tooling;

import com.volmit.resource.PackSnippet;
import com.volmit.tooling.PatternEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The snippet files a pattern view lists after its biomes' entries (v3e's snippet folders): each of a file's entries
 * once, with the biome entries that use it, so a shared palette is found in one place and a file nothing uses yet
 * still shows.
 */
public final class SnippetUses {
    private static final String GROUP = "Snippets";
    private static final String KEY_PREFIX = "snippet:";
    private static final char ENTRY_SEPARATOR = '#';
    private static final int MOST_USES_LISTED = 8;
    private static final int UNUSED_DEPTHS = 1;

    private final Map<PackSnippet.Source, List<PatternEntry>> usesBySource = new LinkedHashMap<>();

    /** Whether {@code key} names an entry of a snippet file, not a biome's. */
    public static boolean isSnippetKey(String key) {
        return key.startsWith(KEY_PREFIX);
    }

    /** The entry of a snippet file that {@code key} names; empty for a key that names none. */
    public static Optional<PackSnippet.Source> sourceOf(String key) {
        int entryStart = key.lastIndexOf(ENTRY_SEPARATOR);
        if (!isSnippetKey(key) || entryStart <= KEY_PREFIX.length()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PackSnippet.Source(key.substring(KEY_PREFIX.length(), entryStart), Integer.parseInt(key.substring(entryStart + 1))));
        } catch (IllegalArgumentException notAnEntry) {
            return Optional.empty();
        }
    }

    /** The line an entry read from a snippet file says so with. */
    public static String factOf(PackSnippet.Source source) {
        return "Snippet " + source.path();
    }

    /** Notes that the entry {@code user} places what {@code source} holds. */
    public void use(PackSnippet.Source source, PatternEntry user) {
        usesBySource.computeIfAbsent(source, unused -> new ArrayList<>()).add(user);
    }

    /** The first entry that uses {@code source}, which it is shown as; empty when nothing uses it. */
    public Optional<PatternEntry> firstUseOf(PackSnippet.Source source) {
        return Optional.ofNullable(usesBySource.get(source)).map(List::getFirst);
    }

    /**
     * One entry for each thing the snippet files hold, in the order given; a file that could not be read is listed once.
     *
     * @param blocksOf the line naming the blocks a snippet's entry places
     */
    public <T> List<PatternEntry> entries(List<PackSnippet<T>> snippets, Function<T, String> blocksOf) {
        List<PatternEntry> entries = new ArrayList<>();
        for (PackSnippet<T> snippet : snippets) {
            if (snippet.entries().isEmpty()) {
                entries.add(entryOf(new PackSnippet.Source(snippet.path(), 0), snippet.path(), List.of("Could not be read; the server log says why")));
                continue;
            }
            for (int held = 0; held < snippet.entries().size(); held++) {
                PackSnippet.Source source = new PackSnippet.Source(snippet.path(), held);
                String name = snippet.entries().size() == 1 ? snippet.path() : snippet.path() + " · " + (held + 1);
                List<String> facts = new ArrayList<>(List.of(blocksOf.apply(snippet.entries().get(held))));
                facts.addAll(usesOf(source));
                entries.add(entryOf(source, name, facts));
            }
        }
        return entries;
    }

    private PatternEntry entryOf(PackSnippet.Source source, String name, List<String> facts) {
        int depths = firstUseOf(source).map(PatternEntry::depths).orElse(UNUSED_DEPTHS);
        return new PatternEntry(KEY_PREFIX + source.path() + ENTRY_SEPARATOR + source.entry(), name, Optional.of(GROUP), depths, facts);
    }

    private List<String> usesOf(PackSnippet.Source source) {
        List<PatternEntry> uses = usesBySource.getOrDefault(source, List.of());
        if (uses.isEmpty()) {
            return List.of("Not used by any biome");
        }
        List<String> lines = new ArrayList<>();
        lines.add(uses.size() == 1 ? "Used once, shown as placed there:" : "Used " + uses.size() + " times, shown as the first places it:");
        uses.stream().limit(MOST_USES_LISTED).map(SnippetUses::nameOf).forEach(lines::add);
        if (uses.size() > MOST_USES_LISTED) {
            lines.add("and " + (uses.size() - MOST_USES_LISTED) + " more");
        }
        return lines;
    }

    private static String nameOf(PatternEntry user) {
        return user.group().map(group -> group + " · " + user.name()).orElse(user.name());
    }
}
