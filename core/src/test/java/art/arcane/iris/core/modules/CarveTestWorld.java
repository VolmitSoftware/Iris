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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.material.IrisBlockData;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.block.BlockTurn;
import com.volmit.block.BlockRegistry;
import com.volmit.object.PackObjects;
import com.volmit.placement.Placements;
import com.volmit.block.PaletteSampler;
import com.volmit.block.PaletteSpec;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.geometry.BlockFace;
import com.volmit.host.ModuleLogs;
import com.volmit.item.ItemRegistry;
import com.volmit.noise.NoiseFactory;
import com.volmit.random.WorldRandom;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.world.PackBiome;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import com.volmit.object.BlockEntityData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A world for the tests of the carve module: the settings its dimension, regions and biomes declare, the biomes its pack has, Iris's
 * own noise over an empty pack, a few blocks by name and the world's seeds, all in memory, so the module is tested through IrisApi alone.
 * Biomes and regions have the ids {@code test:<key>}; a declaration made for {@code <key>} has the source {@code test:<kind>/<key>}.
 */
public final class CarveTestWorld implements PackWorld {
    public static final String NAMESPACE = "test";
    public static final int MIN_HEIGHT = -64;
    private static final Set<String> AIRS = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final Set<String> FLUIDS = Set.of("minecraft:water", "minecraft:lava");
    private static final Set<String> DECORATIONS = Set.of("minecraft:short_grass", "minecraft:poppy", "minecraft:glow_lichen");
    private static final Set<String> FALLING = Set.of("minecraft:sand", "minecraft:gravel");

    private final Map<Class<?>, Object> states = new HashMap<>();
    private final List<Entry<?>> declared = new ArrayList<>();
    private final Map<ResourceId, PackBiome> biomes = new HashMap<>();
    private final IrisData data = packData();
    private final Resources resources = new Resources();
    private long carveSeed = 1337L;

    public CarveTestWorld declare(Level level, String key, IrisFeature value) {
        String kind = level.name().toLowerCase();
        declared.add(new Entry<>(level, new ResourceId(NAMESPACE, kind + "/" + key), value));
        return this;
    }

    public CarveTestWorld dimension(IrisFeature value) {
        return declare(Level.DIMENSION, "overworld", value);
    }

    public CarveTestWorld biome(String key, int rarity) {
        ResourceId id = new ResourceId(NAMESPACE, key);
        biomes.put(id, new PackBiome(id, key, ResourceId.parse("minecraft:plains"), rarity, List.of()));
        return this;
    }

    public CarveTestWorld carveSeed(long seed) {
        this.carveSeed = seed;
        return this;
    }

    /** The pack data under the noise: nothing in it, as the tests of Iris's value types have it. */
    public IrisData data() {
        return data;
    }

    public static ResourceId id(String key) {
        return new ResourceId(NAMESPACE, key);
    }

    @Override
    public long seed() {
        return carveSeed;
    }

    @Override
    public PackResources resources() {
        return resources;
    }

    @Override
    public PackSettings settings() {
        return new PackSettings() {
            @Override
            public PackResources resources() {
                return resources;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
                return SettingsStack.of(declared.stream().filter(entry -> featureType.isInstance(entry.value())).map(entry -> (Entry<F>) entry).toList());
            }
        };
    }

    @Override
    public int minWorldY() {
        return MIN_HEIGHT;
    }

    @Override
    public synchronized <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create) {
        Object known = states.get(type);
        if (known == null) {
            known = create.apply(this);
            states.put(type, known);
        }
        return type.cast(known);
    }

    @Override
    public ModuleLog log() {
        return ModuleLogs.discarding();
    }

    @Override
    public long seed(ResourceId purpose) {
        return IrisSeedPurposes.CARVE.equals(purpose) ? carveSeed : WorldRandom.purposeSeed(carveSeed, purpose);
    }

    @Override
    public Optional<RunningWorld> running() {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return Optional.empty();
    }

    @Override
    public Placements placements() {
        throw new UnsupportedOperationException("No placements here");
    }

    @SuppressWarnings("unchecked")
    private static IrisData packData() {
        IrisData pack = mock(IrisData.class);
        ResourceLoader<IrisBlockData> blocks = mock(ResourceLoader.class);
        when(pack.getBlockLoader()).thenReturn(blocks);
        when(pack.getDataFolder()).thenReturn(new java.io.File(NAMESPACE));
        return pack;
    }

    private final class Resources implements PackResources {
        private final UUID snapshot = UUID.randomUUID();
        private final NoiseFactory noise = new IrisNoiseFactory(data, new IrisPackResources(data, java.util.List::of));

        @Override
        public ResourceId packId() {
            return new ResourceId(NAMESPACE, "pack");
        }

        @Override
        public UUID snapshotId() {
            return snapshot;
        }

        @Override
        public BlockRegistry blocks() {
            return new Blocks();
        }

        @Override
        public ItemRegistry items() {
            throw new UnsupportedOperationException("No items here");
        }

        @Override
        public PackObjects objects() {
            throw new UnsupportedOperationException("No objects here");
        }

        @Override
        public NoiseFactory noise() {
            return noise;
        }

        @Override
        public <T> Optional<T> find(ResourceKind<T> kind, ResourceId id) {
            if (!kind.equals(BIOMES)) {
                return Optional.empty();
            }
            return Optional.ofNullable(kind.type().cast(biomes.get(id)));
        }

        @Override
        public List<ResourceId> ids(ResourceKind<?> kind) {
            return kind.equals(BIOMES) ? biomes.keySet().stream().sorted().toList() : List.of();
        }
    }

    /** Blocks by name: air, water and lava, a few decorations, falling blocks and ores (named {@code *_ore}); everything else is a solid cube. */
    public static final class Blocks implements BlockRegistry {
        @Override
        public Optional<BlockState> find(String key) {
            return Optional.of(named(key));
        }

        @Override
        public BlockState resolve(BlockSpec spec) {
            return named(spec.block());
        }

        @Override
        public BlockState oreFor(BlockState rock, BlockState ore) {
            return ore;
        }

        private static BlockState named(String key) {
            return BlockState.parse(key.contains(":") ? key : "minecraft:" + key);
        }

        @Override
        public Optional<BlockEntityData> blockEntity(BlockSpec spec) {
            return Optional.empty();
        }

        @Override
        public PaletteSampler palette(PaletteSpec spec, long seed) {
            throw new UnsupportedOperationException("No palettes here");
        }

        @Override
        public boolean hasTrait(BlockState state, BlockTrait trait) {
            String material = state.material().toString();
            boolean air = AIRS.contains(material);
            boolean fluid = FLUIDS.contains(material);
            boolean decoration = DECORATIONS.contains(material);
            return switch (trait) {
                case AIR -> air;
                case FLUID -> fluid;
                case SOLID -> !air && !fluid && !decoration;
                case ORE -> material.endsWith("_ore");
                case DECORATION -> decoration;
                case GRAVITY -> FALLING.contains(material);
                default -> false;
            };
        }

        @Override
        public boolean inTag(BlockState state, ResourceId tag) {
            return false;
        }

        @Override
        public boolean canAttach(BlockState state, BlockState support, BlockFace face) {
            return hasTrait(support, BlockTrait.SOLID);
        }

        @Override
        public boolean canPlaceOn(BlockState state, BlockState below) {
            return hasTrait(below, BlockTrait.SOLID);
        }

        @Override
        public BlockState rotate(BlockState state, BlockTurn turn) {
            return state;
        }

        @Override
        public BlockState merge(BlockState base, BlockState update) {
            return update;
        }

        @Override
        public BlockState withPropertyIfAccepted(BlockState state, String name, String value) {
            return state.withProperty(name, value);
        }

        @Override
        public boolean matches(BlockState state, BlockState pattern) {
            return state.equals(pattern);
        }
    }
}
