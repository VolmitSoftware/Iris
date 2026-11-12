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

package art.arcane.iris.modules.terrain;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.builtin.tooling.SnippetUses;
import art.arcane.volmlib.util.math.RNG;
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
 * The studio's Palettes view (v3e's palette explorer): each biome's layers, sea layers, cave ceiling layers, wall and
 * slab, from above, the blocks each places at a depth as the world places them. Picks go through the world's own
 * {@link BiomeLayers} with the random each stage uses, so asking first seeds a layer's noise as generation would.
 */
public final class BiomePalettes implements PatternView {
    private static final ResourceId ID = ResourceId.parse("iris:terrain/palettes");
    private static final char KIND_SEPARATOR = '#';
    private static final char INDEX_SEPARATOR = '/';
    private static final String MINECRAFT_PREFIX = "minecraft:";
    // Walls and slabs are picked one block at a time with noise that changes with height.
    private static final int SINGLE_BLOCK_DEPTHS = 1;
    private static final int SINGLE_BLOCK_SIGNATURE = 0;

    @Override
    public ResourceId id() {
        return ID;
    }

    @Override
    public String label() {
        return "Palettes";
    }

    @Override
    public String description() {
        return "Each biome's layers, wall and slab from above, the blocks they place at each depth";
    }

    @Override
    public List<PatternEntry> entries(PackWorld world) {
        Optional<RunningWorld> running = world.running();
        if (running.isEmpty()) {
            return List.of();
        }
        SnippetUses snippetUses = new SnippetUses();
        List<PatternEntry> entries = new ArrayList<>(biomeEntries(world, running.get().settings(), snippetUses));
        entries.addAll(snippetUses.entries(world.resources().snippets(PaletteLayer.class), BiomePalettes::blocksOf));
        return entries;
    }

    private static List<PatternEntry> biomeEntries(PackWorld world, FeatureSettings settings, SnippetUses snippetUses) {
        BiomeLayers layers = BiomeLayers.of(world);
        List<PackBiome> biomes = biomesByName(world.resources());
        Map<String, Long> biomesByShownName = biomes.stream().collect(Collectors.groupingBy(PackBiome::name, Collectors.counting()));
        List<PatternEntry> entries = new ArrayList<>();
        for (PackBiome biome : biomes) {
            String group = biomesByShownName.get(biome.name()) > 1 ? biome.name() + " (" + biome.id().path() + ")" : biome.name();
            LayeredBiome layered = layers.biome(biome.id(), settings);
            for (Kind kind : Kind.values()) {
                List<PaletteLayer> palettes = kind.palettesOf(layers, layered, settings, biome.id());
                for (int index = 0; index < palettes.size(); index++) {
                    Optional<PackSnippet.Source> snippet = world.resources().snippetOf(palettes.get(index));
                    PatternEntry entry = entryOf(biome, layered, kind, index, palettes.get(index), group, snippet);
                    entries.add(entry);
                    snippet.ifPresent(source -> snippetUses.use(source, entry));
                }
            }
        }
        return entries;
    }

    @Override
    public Optional<BlockPattern> pattern(PackWorld world, String key, int depth, PatternArea area) {
        if (SnippetUses.isSnippetKey(key)) {
            return SnippetUses.sourceOf(key).flatMap(source -> snippetPattern(world, source, depth, area));
        }
        return biomePattern(world, key, depth, area);
    }

    // A snippet's palette is shown as the first biome that uses it places it; one nothing uses has no place in a
    // biome's stack, so it is picked block by block, as a wall is.
    private Optional<BlockPattern> snippetPattern(PackWorld world, PackSnippet.Source source, int depth, PatternArea area) {
        Optional<RunningWorld> running = world.running();
        if (running.isEmpty()) {
            return Optional.empty();
        }
        SnippetUses snippetUses = new SnippetUses();
        biomeEntries(world, running.get().settings(), snippetUses);
        Optional<PatternEntry> firstUse = snippetUses.firstUseOf(source);
        if (firstUse.isPresent()) {
            return biomePattern(world, firstUse.get().key(), depth, area);
        }
        return world.resources().snippets(PaletteLayer.class).stream()
                .filter(snippet -> snippet.path().equals(source.path()))
                .findFirst()
                .map(snippet -> unusedSnippetPattern(world, snippet, source.entry(), depth, area, running.get().layout().fluidHeight()));
    }

    // A file that could not be read is listed too, and places nothing.
    private static BlockPattern unusedSnippetPattern(PackWorld world, PackSnippet<PaletteLayer> snippet, int entry, int depth, PatternArea area,
                                                     int fluidHeight) {
        if (entry >= snippet.entries().size()) {
            return patternOf(area, (x, z, atDepth) -> null, depth);
        }
        ResolvedLayer layer = BiomeLayers.of(world).resolve(snippet.entries().get(entry));
        return patternOf(area, Kind.singleBlocks(layer, new RNG(world.seed(IrisSeedPurposes.TERRAIN)), fluidHeight), depth);
    }

    private static BlockPattern patternOf(PatternArea area, ColumnPick pick, int depth) {
        BlockState[] blocks = new BlockState[area.columns() * area.columns()];
        for (int row = 0; row < area.columns(); row++) {
            for (int column = 0; column < area.columns(); column++) {
                blocks[row * area.columns() + column] = pick.at(area.blockX(column), area.blockZ(row), depth);
            }
        }
        return BlockPattern.of(area.columns(), blocks);
    }

    private Optional<BlockPattern> biomePattern(PackWorld world, String key, int depth, PatternArea area) {
        Optional<RunningWorld> running = world.running();
        Optional<PaletteRef> ref = PaletteRef.parse(key, world.resources());
        if (running.isEmpty() || ref.isEmpty()) {
            return Optional.empty();
        }
        BiomeLayers layers = BiomeLayers.of(world);
        FeatureSettings settings = running.get().settings();
        PaletteRef palette = ref.get();
        LayeredBiome biome = layers.biome(palette.biome(), settings);
        List<PaletteLayer> palettes = palette.kind().palettesOf(layers, biome, settings, palette.biome());
        if (palette.index() >= palettes.size() || depth < 0) {
            return Optional.empty();
        }
        ColumnPick pick = palette.kind().picker(world, layers, biome, palettes.get(palette.index()), palette.index(),
                running.get().layout().fluidHeight());
        return Optional.of(patternOf(area, pick, depth));
    }

    private static List<PackBiome> biomesByName(PackResources resources) {
        return resources.ids(PackResources.BIOMES).stream()
                .map(id -> resources.find(PackResources.BIOMES, id))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(PackBiome::name).thenComparing(biome -> biome.id().toString()))
                .toList();
    }

    private static PatternEntry entryOf(PackBiome biome, LayeredBiome layered, Kind kind, int index, PaletteLayer palette, String group,
                                        Optional<PackSnippet.Source> snippet) {
        String key = biome.id() + String.valueOf(KIND_SEPARATOR) + kind.keyName + INDEX_SEPARATOR + index;
        String name = kind.numbered ? kind.label + " " + (index + 1) : kind.label;
        List<String> facts = kind.factsOf(palette, layered);
        snippet.map(SnippetUses::factOf).ifPresent(facts::add);
        return new PatternEntry(key, name, Optional.of(group).filter(shown -> !shown.isBlank()), kind.depthsOf(palette), facts);
    }

    private static String blocksOf(PaletteLayer palette) {
        Map<String, Integer> weights = palette.palette().stream()
                .collect(Collectors.groupingBy(spec -> shortName(spec.block()), LinkedHashMap::new, Collectors.summingInt(BlockSpec::weight)));
        return weights.entrySet().stream()
                .map(entry -> entry.getValue() > 1 ? entry.getKey() + " ×" + entry.getValue() : entry.getKey())
                .collect(Collectors.joining(", "));
    }

    private static String shortName(String block) {
        return block.startsWith(MINECRAFT_PREFIX) ? block.substring(MINECRAFT_PREFIX.length()) : block;
    }

    private static String thicknessOf(PaletteLayer palette) {
        if (palette.minHeight() == palette.maxHeight()) {
            return palette.maxHeight() + (palette.maxHeight() == 1 ? " block thick" : " blocks thick");
        }
        return palette.minHeight() + " to " + palette.maxHeight() + " blocks thick";
    }

    /** The block a palette places at a column, {@code depth} blocks below its top, or null for none. */
    @FunctionalInterface
    private interface ColumnPick {
        BlockState at(int x, int z, int depth);
    }

    private enum Kind {
        LAYER("layer", "Layer", true),
        SEA_LAYER("sea-layer", "Sea layer", true),
        CEILING_LAYER("ceiling-layer", "Cave ceiling layer", true),
        WALL("wall", "Wall", false),
        SLAB("slab", "Slab", false);

        private final String keyName;
        private final String label;
        private final boolean numbered;

        Kind(String keyName, String label, boolean numbered) {
            this.keyName = keyName;
            this.label = label;
            this.numbered = numbered;
        }

        static Optional<Kind> byKeyName(String keyName) {
            for (Kind kind : values()) {
                if (kind.keyName.equals(keyName)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }

        // A biome without a wall or slab block has those palettes, just empty, so they aren't listed.
        List<PaletteLayer> palettesOf(BiomeLayers layers, LayeredBiome biome, FeatureSettings settings, ResourceId id) {
            return switch (this) {
                case LAYER -> layers.surfaceLayers(biome);
                case SEA_LAYER -> layers.seaLayers(biome);
                case CEILING_LAYER -> layers.ceilingLayers(biome);
                case WALL -> placing(biome.wall() == null ? null : biome.wall().layer());
                case SLAB -> placing(settings.declaredBy(id, SlabSettings.class).map(SlabSettings::layer).orElse(null));
            };
        }

        private static List<PaletteLayer> placing(PaletteLayer layer) {
            return layer == null || layer.palette().isEmpty() ? List.of() : List.of(layer);
        }

        int depthsOf(PaletteLayer palette) {
            return numbered ? Math.max(1, palette.maxHeight()) : SINGLE_BLOCK_DEPTHS;
        }

        List<String> factsOf(PaletteLayer palette, LayeredBiome biome) {
            List<String> facts = new ArrayList<>();
            facts.add(blocksOf(palette));
            facts.add(numbered ? thicknessOf(palette) : "Picked at the fluid height; changes with height");
            facts.add(palette.style().style().toLowerCase(Locale.ROOT).replace('_', ' ') + " noise, zoom " + palette.zoom());
            if (!palette.slopeCondition().isDefault()) {
                facts.add("Only on slopes from " + palette.slopeCondition().minimumSlope() + " to " + palette.slopeCondition().maximumSlope()
                        + "; shown everywhere here");
            }
            if (this == LAYER && biome.layers() == null) {
                facts.add("The biome declares no layers, so it gets this default one");
            }
            if (this == LAYER && biome.lock() != null && biome.lock().locked()) {
                facts.add("The biome locks its layers, which repeats them by height");
            }
            return facts;
        }

        // Each stage picks with its own random: the terrain's for surface and sea layers, the caves' for ceilings and
        // walls, post processing's for slabs. The studio asks with the same one, so it seeds noise as generation does.
        ColumnPick picker(PackWorld world, BiomeLayers layers, LayeredBiome biome, PaletteLayer palette, int index, int fluidHeight) {
            return switch (this) {
                case LAYER -> {
                    RNG terrain = new RNG(world.seed(IrisSeedPurposes.TERRAIN));
                    yield (x, z, depth) -> layers.surfaceLayerBlock(biome, index, depth, x, z, terrain);
                }
                case SEA_LAYER -> {
                    RNG terrain = new RNG(world.seed(IrisSeedPurposes.TERRAIN));
                    yield (x, z, depth) -> layers.seaLayerBlock(biome, index, depth, x, z, terrain);
                }
                case CEILING_LAYER -> {
                    RNG carve = new RNG(world.seed(IrisSeedPurposes.CARVE));
                    yield (x, z, depth) -> layers.ceilingLayerBlock(biome, index, depth, x, z, carve);
                }
                case WALL -> singleBlocks(layers.resolve(palette), new RNG(world.seed(IrisSeedPurposes.CARVE)), fluidHeight);
                case SLAB -> singleBlocks(layers.resolve(palette), new RNG(world.seed(IrisSeedPurposes.POST)), fluidHeight);
            };
        }

        static ColumnPick singleBlocks(ResolvedLayer layer, RNG random, int height) {
            return (x, z, depth) -> depth == 0 ? layer.get(random, SINGLE_BLOCK_SIGNATURE, x, height, z) : null;
        }
    }

    private record PaletteRef(ResourceId biome, Kind kind, int index) {
        static Optional<PaletteRef> parse(String key, PackResources resources) {
            int kindStart = key.lastIndexOf(KIND_SEPARATOR);
            int indexStart = key.lastIndexOf(INDEX_SEPARATOR);
            if (kindStart < 1 || indexStart < kindStart) {
                return Optional.empty();
            }
            Optional<Kind> kind = Kind.byKeyName(key.substring(kindStart + 1, indexStart));
            Optional<Integer> index = wholeNumber(key.substring(indexStart + 1));
            Optional<ResourceId> biome = resources.ids(PackResources.BIOMES).stream()
                    .filter(id -> id.toString().equals(key.substring(0, kindStart)))
                    .findFirst();
            if (kind.isEmpty() || index.isEmpty() || biome.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new PaletteRef(biome.get(), kind.get(), index.get()));
        }

        private static Optional<Integer> wholeNumber(String text) {
            try {
                return Optional.of(Integer.parseInt(text)).filter(number -> number >= 0);
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
        }
    }
}
