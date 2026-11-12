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

package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.builtin.tooling.SnippetUses;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.PackSnippet;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.BlockPattern;
import com.volmit.tooling.PatternArea;
import com.volmit.tooling.PatternEntry;
import com.volmit.tooling.PatternView;
import com.volmit.world.PackBiome;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The studio's Decorators view (v3e's palette explorer showed them beside the layers): each biome's decorators from
 * above, the columns a decorator's own noise lets it place on and the block it picks there. A decorator is shown alone:
 * in the world the biome's other decorators, the surface block and the slope decide between them.
 */
public final class DecoratorPatterns implements PatternView {
    private static final ResourceId ID = ResourceId.parse("iris:decorant/decorators");
    private static final char INDEX_SEPARATOR = '#';
    private static final char ENTRY_SEED_SEPARATOR = '#';
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final int ONE_BLOCK = 1;
    private static final Map<DecorationPart, String> PLACES = Map.of(
            DecorationPart.SHORE_LINE, "On the shore line",
            DecorationPart.SEA_SURFACE, "On the sea's surface",
            DecorationPart.SEA_FLOOR, "On the sea floor",
            DecorationPart.CEILING, "On cave ceilings");

    @Override
    public ResourceId id() {
        return ID;
    }

    @Override
    public String label() {
        return "Decorators";
    }

    @Override
    public String description() {
        return "Each biome's decorators from above, where they place and the blocks they pick";
    }

    @Override
    public List<PatternEntry> entries(PackWorld world) {
        Optional<RunningWorld> running = world.running();
        if (running.isEmpty()) {
            return List.of();
        }
        SnippetUses snippetUses = new SnippetUses();
        List<PatternEntry> entries = new ArrayList<>(biomeEntries(world.resources(), running.get().settings(), snippetUses));
        entries.addAll(snippetUses.entries(world.resources().snippets(Decorator.class), decorator -> blocksOf(decorator.palette())));
        return entries;
    }

    @Override
    public Optional<BlockPattern> pattern(PackWorld world, String key, int depth, PatternArea area) {
        Optional<RunningWorld> running = world.running();
        if (running.isEmpty() || depth < 0) {
            return Optional.empty();
        }
        FeatureSettings settings = running.get().settings();
        Optional<ResolvedDecorator> decorator = SnippetUses.isSnippetKey(key)
                ? SnippetUses.sourceOf(key).flatMap(source -> snippetDecorator(world, settings, source))
                : biomeDecorator(world, settings, key);
        if (decorator.isEmpty() && isUnreadableSnippet(world.resources(), key)) {
            return Optional.of(BlockPattern.of(area.columns(), new BlockState[area.columns() * area.columns()]));
        }
        return decorator.map(placed -> patternOf(placed, depth, area));
    }

    // A file that could not be read is listed too, and places nothing.
    private static boolean isUnreadableSnippet(PackResources resources, String key) {
        return SnippetUses.sourceOf(key).stream().anyMatch(source -> resources.snippets(Decorator.class).stream()
                .anyMatch(snippet -> snippet.path().equals(source.path()) && snippet.entries().isEmpty()));
    }

    private static List<PatternEntry> biomeEntries(PackResources resources, FeatureSettings settings, SnippetUses snippetUses) {
        List<PackBiome> biomes = biomesByName(resources);
        Map<String, Long> biomesByShownName = biomes.stream().collect(Collectors.groupingBy(PackBiome::name, Collectors.counting()));
        List<PatternEntry> entries = new ArrayList<>();
        for (PackBiome biome : biomes) {
            String group = biomesByShownName.get(biome.name()) > 1 ? biome.name() + " (" + biome.id().path() + ")" : biome.name();
            List<Decorator> decorators = settings.declaredBy(biome.id(), DecorantSettings.class).map(DecorantSettings::decorators).orElse(List.of());
            for (int index = 0; index < decorators.size(); index++) {
                Decorator decorator = decorators.get(index);
                if (decorator == null || decorator.palette().isEmpty()) {
                    continue;
                }
                Optional<PackSnippet.Source> snippet = resources.snippetOf(decorator);
                PatternEntry entry = entryOf(biome, index, decorator, group, snippet);
                entries.add(entry);
                snippet.ifPresent(source -> snippetUses.use(source, entry));
            }
        }
        return entries;
    }

    private static List<PackBiome> biomesByName(PackResources resources) {
        return resources.ids(PackResources.BIOMES).stream()
                .map(id -> resources.find(PackResources.BIOMES, id))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(PackBiome::name).thenComparing(biome -> biome.id().toString()))
                .toList();
    }

    private static PatternEntry entryOf(PackBiome biome, int index, Decorator decorator, String group, Optional<PackSnippet.Source> snippet) {
        String key = biome.id().toString() + INDEX_SEPARATOR + index;
        List<String> facts = factsOf(decorator);
        snippet.map(SnippetUses::factOf).ifPresent(facts::add);
        return new PatternEntry(key, "Decorator " + (index + 1), Optional.of(group).filter(shown -> !shown.isBlank()), depthsOf(decorator), facts);
    }

    // A stack scaled to its cave has no height of its own to step through.
    private static int depthsOf(Decorator decorator) {
        return decorator.isStacking() && !decorator.scaleStack() ? decorator.stackMax() : ONE_BLOCK;
    }

    private static List<String> factsOf(Decorator decorator) {
        List<String> facts = new ArrayList<>();
        facts.add(blocksOf(decorator.palette()));
        if (!decorator.topPalette().isEmpty()) {
            facts.add("Topped with " + blocksOf(decorator.topPalette()));
        }
        facts.add("Chance " + decorator.chance() + ", " + decorator.style().style().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " noise, zoom " + decorator.style().zoom());
        if (decorator.isStacking()) {
            facts.add(stackOf(decorator));
        }
        if (PLACES.containsKey(decorator.partOf())) {
            facts.add(PLACES.get(decorator.partOf()));
        }
        if (!decorator.slopeCondition().isDefault()) {
            facts.add("Only on slopes from " + decorator.slopeCondition().minimumSlope() + " to " + decorator.slopeCondition().maximumSlope()
                    + "; shown everywhere here");
        }
        facts.add("Shown alone, without the biome's other decorators");
        return facts;
    }

    private static String stackOf(Decorator decorator) {
        String unit = decorator.scaleStack() ? "% of the cave's height" : " blocks";
        if (decorator.stackMin() == decorator.stackMax()) {
            return "Stacks " + decorator.stackMax() + unit;
        }
        return "Stacks " + decorator.stackMin() + " to " + decorator.stackMax() + unit;
    }

    private static String blocksOf(List<BlockSpec> palette) {
        Map<String, Integer> weights = palette.stream()
                .collect(Collectors.groupingBy(spec -> shortName(spec.block()), LinkedHashMap::new, Collectors.summingInt(BlockSpec::weight)));
        return weights.entrySet().stream()
                .map(entry -> entry.getValue() > 1 ? entry.getKey() + " ×" + entry.getValue() : entry.getKey())
                .collect(Collectors.joining(", "));
    }

    private static String shortName(String block) {
        return block.startsWith(MINECRAFT_PREFIX) ? block.substring(MINECRAFT_PREFIX.length()) : block;
    }

    private static Optional<ResolvedDecorator> biomeDecorator(PackWorld world, FeatureSettings settings, String key) {
        int indexStart = key.lastIndexOf(INDEX_SEPARATOR);
        if (indexStart < 1) {
            return Optional.empty();
        }
        Optional<Integer> index = wholeNumber(key.substring(indexStart + 1));
        Optional<ResourceId> biome = world.resources().ids(PackResources.BIOMES).stream()
                .filter(id -> id.toString().equals(key.substring(0, indexStart)))
                .findFirst();
        if (index.isEmpty() || biome.isEmpty()) {
            return Optional.empty();
        }
        Optional<DecorantSettings> declared = settings.declaredBy(biome.get(), DecorantSettings.class);
        return declared.map(decorators -> BiomeDecorators.of(world)
                .decoratorAt(new DecoratedBiome(BiomeDecorators.biomeKey(biome.get()), decorators, null), index.get()));
    }

    // A snippet's decorator is shown as the first biome that uses it places it; one nothing uses gets noise of its own.
    private static Optional<ResolvedDecorator> snippetDecorator(PackWorld world, FeatureSettings settings, PackSnippet.Source source) {
        SnippetUses snippetUses = new SnippetUses();
        biomeEntries(world.resources(), settings, snippetUses);
        Optional<PatternEntry> firstUse = snippetUses.firstUseOf(source);
        if (firstUse.isPresent()) {
            return biomeDecorator(world, settings, firstUse.get().key());
        }
        return world.resources().snippets(Decorator.class).stream()
                .filter(snippet -> snippet.path().equals(source.path()))
                .map(PackSnippet::entries)
                .filter(held -> source.entry() < held.size())
                .findFirst()
                .map(held -> BiomeDecorators.of(world).unlisted(source.path() + ENTRY_SEED_SEPARATOR + source.entry(), held.get(source.entry())));
    }

    private static Optional<Integer> wholeNumber(String text) {
        try {
            return Optional.of(Integer.parseInt(text)).filter(number -> number >= 0);
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static BlockPattern patternOf(ResolvedDecorator decorator, int depth, PatternArea area) {
        BlockState[] blocks = new BlockState[area.columns() * area.columns()];
        for (int row = 0; row < area.columns(); row++) {
            for (int column = 0; column < area.columns(); column++) {
                blocks[row * area.columns() + column] = blockAt(decorator, area.blockX(column), area.blockZ(row), depth);
            }
        }
        return BlockPattern.of(area.columns(), blocks);
    }

    // The top of a stack takes the top palette, as generation gives it, and the rest the palette.
    private static BlockState blockAt(ResolvedDecorator decorator, int x, int z, int depth) {
        if (!decorator.passesChanceGate(x, z) || depth >= heightAt(decorator, x, z)) {
            return null;
        }
        return depth == 0 && decorator.isStacking() ? decorator.pickTopBlock(x, z) : decorator.pickBlock(x, z);
    }

    private static int heightAt(ResolvedDecorator decorator, int x, int z) {
        return decorator.isStacking() && !decorator.isScaleStack() ? decorator.stackHeight(x, z) : ONE_BLOCK;
    }
}
