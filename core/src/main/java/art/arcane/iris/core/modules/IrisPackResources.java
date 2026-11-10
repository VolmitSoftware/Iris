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

import art.arcane.iris.core.loader.IrisData;
import com.volmit.object.PackObjects;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.image.IrisImageMap;
import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.block.BlockRegistry;
import com.volmit.host.ResourceKindRegistration;
import com.volmit.item.ItemRegistry;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.PackExpression;
import com.volmit.noise.PackImageMap;
import com.volmit.object.GeneratedObject;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.world.PackBiome;
import com.volmit.world.PackRegion;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The resources of one loaded snapshot of an Iris pack, which every chunk, mantle and placement stage of that
 * snapshot shares. Each load or hotload of the pack's data makes a new one, with a new {@link #snapshotId()}, and so
 * does every reload of its module resources ({@link IrisData#dump()}, {@link IrisData#invalidateStructureResources()}).
 * <p>
 * Served by kind: the host kinds {@link #OBJECTS} (a pack object, as its geometry), {@link #BIOMES} and
 * {@link #REGIONS}, {@link #EXPRESSIONS} and {@link #IMAGE_MAPS} (each only as its id, which noise styles name), and
 * every {@link ResourceKind} the pack's modules register, read from the kind's folder
 * ({@link ModuleResourceFiles}). All use the id {@code <pack namespace>:<key>} that {@link PackResourceIds}
 * gives the pack's biomes in a biome sample, so one id can name a biome and a region. An id the pack lacks, or of
 * another namespace, is absent; any other kind fails with {@link IllegalArgumentException}. A resource that exists
 * but cannot be loaded fails with {@link IllegalStateException}. Each resource is built once and kept for the
 * snapshot. The module kinds are looked up the first time one is asked for, which loads the pack's modules.
 * <p>
 * Blocks resolve through the platform and the pack's {@code blocks} folder ({@link IrisBlockRegistry}), and noise
 * styles through Iris's generators, expressions and image maps ({@link IrisNoiseFactory}), both kept per snapshot.
 */
public final class IrisPackResources implements PackResources {
    private static final String SERVED_TYPES = "the host kinds (objects, biomes, regions, expressions, image maps) and the pack's module resource kinds";

    private final IrisData data;
    private final PackResourceIds ids;
    private final UUID snapshotId = UUID.randomUUID();
    private final SettingsStacks settingsStacks = new SettingsStacks();
    private final BlockRegistry blocks;
    private final PackObjects objects;
    private final ItemRegistry items = new IrisItemRegistry();
    private final NoiseFactory noise;
    private final Map<ResourceId, Kind<?>> irisKinds;
    private final Supplier<List<ResourceKind<?>>> moduleKindList;
    private volatile Map<ResourceId, Kind<?>> moduleKinds;
    private volatile List<String> moduleFolders;
    private boolean announcedToModules;

    public IrisPackResources(IrisData data) {
        this(data, () -> data.getModules().pack().catalog().resourceKinds().stream()
                .<ResourceKind<?>>map(ResourceKindRegistration::kind)
                .toList());
    }

    /** @param moduleKinds the resource kinds of the pack's modules, asked for once, when a module kind is first needed */
    IrisPackResources(IrisData data, Supplier<List<ResourceKind<?>>> moduleKinds) {
        this.data = data;
        this.ids = new PackResourceIds(data.getDataFolder());
        this.moduleKindList = moduleKinds;
        this.blocks = new IrisBlockRegistry(data, this);
        this.objects = new IrisPackObjects(data, this);
        this.noise = new IrisNoiseFactory(data, this);
        this.irisKinds = Map.of(
                OBJECTS.id(), Kind.<IrisObject, GeneratedObject>ofLoader(ids, data::getObjectLoader, (id, object) -> object.getGeometry()),
                BIOMES.id(), Kind.<IrisBiome, PackBiome>ofLoader(ids, data::getBiomeLoader, this::biomeOf),
                REGIONS.id(), Kind.<IrisRegion, PackRegion>ofLoader(ids, data::getRegionLoader, this::regionOf),
                EXPRESSIONS.id(), Kind.<IrisExpression, PackExpression>ofLoader(ids, data::getExpressionLoader, (id, expression) -> new PackExpression(id)),
                IMAGE_MAPS.id(), Kind.<IrisImageMap, PackImageMap>ofLoader(ids, data::getImageMapLoader, (id, image) -> new PackImageMap(id)));
    }

    @Override
    public ResourceId packId() {
        return ids.packId();
    }

    @Override
    public UUID snapshotId() {
        return snapshotId;
    }

    /** The settings stacks of this snapshot's columns, which every world generating with it shares. */
    SettingsStacks settingsStacks() {
        return settingsStacks;
    }

    /**
     * Runs {@code announce}, which tells the pack's modules that this snapshot loaded, unless it ran successfully for
     * this snapshot before: modules hear of each snapshot once, however many worlds start with it.
     */
    synchronized void loadOnce(Runnable announce) {
        if (announcedToModules) {
            return;
        }
        announce.run();
        announcedToModules = true;
    }

    @Override
    public BlockRegistry blocks() {
        return blocks;
    }

    @Override
    public PackObjects objects() {
        return objects;
    }

    @Override
    public ItemRegistry items() {
        return items;
    }

    @Override
    public NoiseFactory noise() {
        return noise;
    }

    @Override
    @SuppressWarnings("unchecked") // A kind's id names the one kind registered under it, which serves the kind's type.
    public <T> Optional<T> find(ResourceKind<T> kind, ResourceId id) {
        return ((Kind<T>) kindOf(kind)).find(id);
    }

    @Override
    public List<ResourceId> ids(ResourceKind<?> kind) {
        return kindOf(kind).ids();
    }

    @Override
    public <S> List<S> services(Class<S> service) {
        return PackServices.of(data).providers(service);
    }

    /**
     * The ids of the resources of the kind {@code kindId} names, for Iris's own tools that list a module's resources
     * without the module's class for them, which the plugin's classes cannot reach when the kind ships in a module jar.
     */
    public List<ResourceId> ids(ResourceId kindId) {
        return kindOf(kindId).ids();
    }

    /** The id of the resource the pack names by {@code packKey}, such as {@code overworld:castle/keep} for {@code castle/keep}. */
    @Override
    public ResourceId idOf(String packKey) {
        return ids.of(packKey);
    }

    /** The folders the pack's module resource kinds read, which loads the pack's modules. */
    public List<String> moduleFolderNames() {
        List<String> folders = moduleFolders;
        if (folders == null) {
            folders = moduleKindList.get().stream().map(ResourceKind::folder).sorted().toList();
            moduleFolders = folders;
        }
        return folders;
    }

    private Kind<?> kindOf(ResourceKind<?> kind) {
        return kindOf(kind.id());
    }

    private Kind<?> kindOf(ResourceId kindId) {
        Kind<?> iris = irisKinds.get(kindId);
        if (iris != null) {
            return iris;
        }
        Kind<?> module = moduleKinds().get(kindId);
        if (module == null) {
            throw new IllegalArgumentException("Iris serves pack resources of " + SERVED_TYPES + ", not " + kindId);
        }
        return module;
    }

    private Map<ResourceId, Kind<?>> moduleKinds() {
        Map<ResourceId, Kind<?>> kinds = moduleKinds;
        if (kinds != null) {
            return kinds;
        }
        synchronized (this) {
            if (moduleKinds == null) {
                Map<ResourceId, Kind<?>> found = new HashMap<>();
                moduleKindList.get().forEach(kind -> found.put(kind.id(), moduleKind(kind)));
                moduleKinds = Map.copyOf(found);
            }
            return moduleKinds;
        }
    }

    private <T> Kind<T> moduleKind(ResourceKind<T> kind) {
        ModuleResourceFiles<T> files = new ModuleResourceFiles<>(data, kind);
        return new Kind<>(ids, files::keys, (id, key) -> files.read(key));
    }

    /** Iris's own biome that {@code id}, as a {@code BiomeSample} gives it, names; for host code that reads what the biome declares. */
    Optional<IrisBiome> irisBiome(ResourceId id) {
        String key = irisKinds.get(BIOMES.id()).keyOf(id);
        return key == null ? Optional.empty() : Optional.ofNullable(data.getBiomeLoader().load(key));
    }

    /**
     * The key Iris's own code names {@code id} by, a resource of {@code kind}: the pack key that has the id, else the
     * id's path when it is of the pack's namespace, else the id as written ({@code minecraft:village}).
     */
    public String keyOf(ResourceKind<?> kind, ResourceId id) {
        String key = kindOf(kind).keyOf(id);
        return key == null ? keyOf(id) : key;
    }

    /** The key Iris's own code names {@code id} by when no index of keys is at hand: its path in the pack's namespace, else the id as written. */
    public String keyOf(ResourceId id) {
        return id.namespace().equals(ids.namespace()) ? id.path() : id.toString();
    }

    private PackBiome biomeOf(ResourceId id, IrisBiome biome) {
        return new PackBiome(id, biome.getName(), ResourceId.parse(biome.getDerivativeKey()), ResourceId.parse(biome.getVanillaDerivativeKey()),
                biome.getRarity(), idsOf(biome.getChildren()));
    }

    private PackRegion regionOf(ResourceId id, IrisRegion region) {
        return new PackRegion(id, region.getName(), region.getRarity(), idsOf(region.getLandBiomes()), idsOf(region.getSeaBiomes()),
                idsOf(region.getShoreBiomes()), idsOf(region.getCaveBiomes()));
    }

    private List<ResourceId> idsOf(Collection<String> keys) {
        return keys.stream().map(ids::of).toList();
    }

    /** One kind of pack resource: the keys the pack has, how a key's resource is read, an index from ids to keys and what was read so far. */
    private static final class Kind<T> {
        private final PackResourceIds ids;
        private final Supplier<Collection<String>> keys;
        private final BiFunction<ResourceId, String, T> read;
        private final Map<ResourceId, T> built = new ConcurrentHashMap<>();
        private volatile Map<ResourceId, String> keysById;

        /** {@code read} gives the resource of a key, or null when it exists but fails to load. */
        Kind(PackResourceIds ids, Supplier<Collection<String>> keys, BiFunction<ResourceId, String, T> read) {
            this.ids = ids;
            this.keys = keys;
            this.read = read;
        }

        /** A kind Iris's own loader serves, each loaded resource converted into what IrisApi hands out. */
        static <R extends IrisRegistrant, T> Kind<T> ofLoader(PackResourceIds ids, Supplier<ResourceLoader<R>> loader,
                                                               BiFunction<ResourceId, R, T> conversion) {
            return new Kind<>(ids, () -> Arrays.asList(loader.get().getPossibleKeys()), (id, key) -> {
                R resource = loader.get().load(key);
                return resource == null ? null : conversion.apply(id, resource);
            });
        }

        /** The pack key that has {@code id}, or null. */
        String keyOf(ResourceId id) {
            return keysById().get(id);
        }

        Optional<T> find(ResourceId id) {
            T cached = built.get(id);
            if (cached != null) {
                return Optional.of(cached);
            }
            String key = keysById().get(id);
            if (key == null) {
                return Optional.empty();
            }
            T made = read.apply(id, key);
            if (made == null) {
                throw new IllegalStateException("Pack resource " + id + " exists but failed to load");
            }
            T raced = built.putIfAbsent(id, made);
            return Optional.of(raced != null ? raced : made);
        }

        List<ResourceId> ids() {
            return keysById().keySet().stream().sorted().toList();
        }

        /** The pack's keys by the ids they get, built on first use; a key whose id another key already has is left out. */
        private Map<ResourceId, String> keysById() {
            Map<ResourceId, String> index = keysById;
            if (index != null) {
                return index;
            }
            Map<ResourceId, String> indexed = new HashMap<>();
            for (String key : keys.get()) {
                indexed.putIfAbsent(ids.of(key), key);
            }
            keysById = Map.copyOf(indexed);
            return keysById;
        }
    }
}
