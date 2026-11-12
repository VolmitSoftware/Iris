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
import art.arcane.iris.engine.framework.Engine;
import com.volmit.host.FeatureCatalog;
import com.volmit.host.ServiceRegistration;
import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemDrop;
import com.volmit.placement.PlacementRecord;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.AmbientEffects;
import com.volmit.service.BlockDrops;
import com.volmit.service.CaveBiomes;
import com.volmit.service.EntitySpawns;
import com.volmit.service.LocateResult;
import com.volmit.service.Loot;
import com.volmit.service.ObjectsAt;
import com.volmit.service.ProtectedFloor;
import com.volmit.service.SaplingGrowth;
import com.volmit.service.StructureLocator;
import com.volmit.service.VanillaFeatures;
import com.volmit.geometry.BlockPosition;
import com.volmit.world.PackWorld;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * What Iris core asks the pack's modules through their services ({@link com.volmit.service.Service}) instead of reading
 * their features itself. Each method combines the providers the way their service says. Asking a pack whose modules
 * fail to load throws its {@link PackModulesException}.
 */
public final class ModuleServices {
    private ModuleServices() {
    }

    /** One provider of a service and the world as its module sees it, whose log is the module's own. */
    private record Provider<S>(S service, PackWorld world) {
    }

    /** Every provider of {@code service}, in the catalog's order, each with {@code world} scoped to its module. */
    private static <S> List<Provider<S>> providers(IrisData data, Class<S> service, PackWorld world) {
        List<Provider<S>> providers = new ArrayList<>();
        for (ServiceRegistration registration : catalog(data).registrations(service)) {
            providers.add(new Provider<>(service.cast(registration.provider()), registration.scope(world)));
        }
        return providers;
    }

    private static <S> List<Provider<S>> providers(Engine engine, Class<S> service) {
        return providers(engine.getData(), service, engine.getGeneration().world());
    }

    private static <S> Optional<Provider<S>> firstProvider(Engine engine, Class<S> service) {
        List<ServiceRegistration> registrations = catalog(engine.getData()).registrations(service);
        if (registrations.isEmpty()) {
            return Optional.empty();
        }
        ServiceRegistration first = registrations.get(0);
        return Optional.of(new Provider<>(service.cast(first.provider()), first.scope(engine.getGeneration().world())));
    }

    /** What the module that decides cave biomes says for the engine's world, kept for the world, its log the module's own. */
    public static CaveBiomeAnswers caveBiomes(Engine engine) {
        return engine.getGeneration().state(CaveBiomeAnswers.class, owner -> firstProvider(owner, CaveBiomes.class)
                .map(provider -> new CaveBiomeAnswers(owner, provider.service(), provider.world()))
                .orElseGet(() -> new CaveBiomeAnswers(owner, null, owner.getGeneration().world())));
    }

    /**
     * Whether the module that shapes the terrain keeps the lowest layer of the engine's world for itself, so the engine
     * discards the blocks others write there. Asked once for the world and kept; no provider keeps nothing.
     */
    public static boolean protectsFloor(Engine engine) {
        return engine.getGeneration().state(FloorProtection.class, owner -> new FloorProtection(
                providers(owner, ProtectedFloor.class).stream()
                        .anyMatch(provider -> provider.service().protectsFloor(provider.world())))).protectsFloor();
    }

    private record FloorProtection(boolean protectsFloor) {
    }

    /** What the modules say about the platform's own placed features in the engine's world, kept for the world. */
    public static VanillaFeatureAnswers vanillaFeatures(Engine engine) {
        return engine.getGeneration().state(VanillaFeatureAnswers.class, owner -> new VanillaFeatureAnswers(
                providers(owner, VanillaFeatures.class).stream()
                        .map(provider -> new VanillaFeatureAnswers.Provider(provider.service(), provider.world()))
                        .toList()));
    }

    /** The objects every module would place in the chunk at {@code chunkX}, {@code chunkZ}, guessed without generating it. */
    public static Set<String> objectsAt(Engine engine, int chunkX, int chunkZ) {
        IrisPackResources resources = engine.getData().getPackResources();
        Set<String> keys = new LinkedHashSet<>();
        for (Provider<ObjectsAt> provider : providers(engine, ObjectsAt.class)) {
            provider.service().objectsAt(provider.world(), chunkX, chunkZ).forEach(id -> keys.add(resources.keyOf(PackResources.OBJECTS, id)));
        }
        return keys;
    }

    /**
     * The object, and the structure it is a piece of, that a module plans at the block, as pack keys; empty when none
     * plans one. Y is the height above the dimension's bottom.
     */
    public static Optional<PlannedPlacement> plannedPlacementAt(Engine engine, int x, int y, int z) {
        IrisPackResources resources = engine.getData().getPackResources();
        for (Provider<ObjectsAt> provider : providers(engine, ObjectsAt.class)) {
            Optional<PlacementRecord> planned = provider.service().placementAt(provider.world(), x, y, z);
            if (planned.isPresent()) {
                return planned.map(record -> new PlannedPlacement(
                        resources.keyOf(PackResources.OBJECTS, record.object()),
                        record.structure().map(resources::keyOf),
                        record.owner().map(owner -> PlacementMarkers.ownerKeyOf(owner, resources))));
            }
        }
        return Optional.empty();
    }

    /**
     * A placement a module plans at a block: the object's key, for a structure piece the structure's, and the biome,
     * region or dimension that placed it.
     */
    public record PlannedPlacement(String object, Optional<String> structure, Optional<PlacementMarkers.OwnerKey> owner) {
    }

    /** The keys of the biomes and regions of the engine's layout that every module says place the object {@code objectKey}. */
    public static ObjectPlacers objectPlacers(Engine engine, String objectKey) {
        IrisPackResources resources = engine.getData().getPackResources();
        ResourceId object = resources.idOf(objectKey);
        Set<String> biomes = new LinkedHashSet<>();
        Set<String> regions = new LinkedHashSet<>();
        for (Provider<ObjectsAt> provider : providers(engine, ObjectsAt.class)) {
            ObjectsAt.PlacedBy placedBy = provider.service().placedBy(provider.world(), object);
            placedBy.biomes().forEach(id -> biomes.add(resources.keyOf(PackResources.BIOMES, id)));
            placedBy.regions().forEach(id -> regions.add(resources.keyOf(PackResources.REGIONS, id)));
        }
        return new ObjectPlacers(biomes, regions);
    }

    /** The keys of the biomes and regions that place an object. */
    public record ObjectPlacers(Set<String> biomes, Set<String> regions) {
        public boolean isEmpty() {
            return biomes.isEmpty() && regions.isEmpty();
        }
    }

    /** The loot tables the modules say the container at {@code site} draws from, in module order. */
    public static List<Loot.Source> lootTables(Engine engine, Loot.Site site) {
        List<Loot.Source> tables = new ArrayList<>();
        for (Provider<Loot> provider : providers(engine, Loot.class)) {
            tables.addAll(provider.service().tables(provider.world(), site));
        }
        return tables;
    }

    /** The seed to shuffle the slots of the container at {@code site} with: the first the modules give; empty when none does. */
    public static OptionalLong lootShuffleSeed(Engine engine, Loot.Site site) {
        for (Provider<Loot> provider : providers(engine, Loot.class)) {
            OptionalLong seed = provider.service().shuffleSeed(provider.world(), site);
            if (seed.isPresent()) {
                return seed;
            }
        }
        return OptionalLong.empty();
    }

    /** The items loot table {@code table} gives at {@code site}, from the module that knows the table. */
    public static List<ItemDrop> lootItems(Engine engine, Loot.Site site, ResourceId table) {
        List<ItemDrop> items = new ArrayList<>();
        for (Provider<Loot> provider : providers(engine, Loot.class)) {
            items.addAll(provider.service().items(provider.world(), site, table));
        }
        return items;
    }

    /** What the modules drop for the broken block, put together. */
    public static BlockDrops.Result blockDrops(Engine engine, BlockDrops.Broken broken) {
        List<ItemDrop> drops = new ArrayList<>();
        boolean replacesVanillaDrops = false;
        for (Provider<BlockDrops> provider : providers(engine, BlockDrops.class)) {
            BlockDrops.Result result = provider.service().drops(provider.world(), broken);
            drops.addAll(result.drops());
            replacesVanillaDrops |= result.replacesVanillaDrops();
        }
        return new BlockDrops.Result(drops, replacesVanillaDrops);
    }

    /** The tree grown by the first module that grows one for {@code sapling}; empty leaves the growth to the platform. */
    public static Optional<SaplingGrowth.Growth> growSapling(Engine engine, SaplingGrowth.Sapling sapling, SaplingGrowth.Surroundings surroundings) {
        for (Provider<SaplingGrowth> provider : providers(engine, SaplingGrowth.class)) {
            Optional<SaplingGrowth.Growth> growth = provider.service().grow(provider.world(), sapling, surroundings);
            if (growth.isPresent()) {
                return growth;
            }
        }
        return Optional.empty();
    }

    /** The objects every module places as trees. */
    public static SaplingGrowth.TreeObjects treeObjects(Engine engine) {
        Set<ResourceId> objects = new LinkedHashSet<>();
        for (Provider<SaplingGrowth> provider : providers(engine, SaplingGrowth.class)) {
            objects.addAll(provider.service().treeObjects(provider.world()).objects());
        }
        return new SaplingGrowth.TreeObjects(objects);
    }

    /** What the first module with something to spawn at {@code site} plans; empty when none does. */
    public static Optional<PlannedSpawn> planSpawn(Engine engine, EntitySpawns.Site site) {
        for (Provider<EntitySpawns> provider : providers(engine, EntitySpawns.class)) {
            Optional<EntitySpawns.Plan> plan = provider.service().plan(provider.world(), site);
            if (plan.isPresent()) {
                return Optional.of(new PlannedSpawn(provider.service(), provider.world(), plan.get()));
            }
        }
        return Optional.empty();
    }

    /** The entity the pack names {@code key} as the first module that knows it would spawn it. */
    public static Optional<EntitySpec> entity(Engine engine, String key) {
        ResourceId id = engine.getData().getPackResources().idOf(key);
        for (Provider<EntitySpawns> provider : providers(engine, EntitySpawns.class)) {
            Optional<EntitySpec> entity = provider.service().entity(provider.world(), id);
            if (entity.isPresent()) {
                return entity;
            }
        }
        return Optional.empty();
    }

    /** What the first module that knows the marker {@code tag} says it does. */
    public static Optional<EntitySpawns.MarkerRule> markerRule(Engine engine, String tag) {
        ResourceId id = engine.getData().getPackResources().idOf(tag);
        for (Provider<EntitySpawns> provider : providers(engine, EntitySpawns.class)) {
            Optional<EntitySpawns.MarkerRule> rule = provider.service().marker(provider.world(), id);
            if (rule.isPresent()) {
                return rule;
            }
        }
        return Optional.empty();
    }

    /** The ambient effects every module says are due for a player standing at {@code at}, in module order. */
    public static List<AmbientEffects.Effect> ambientEffects(Engine engine, BlockPosition at) {
        List<AmbientEffects.Effect> effects = new ArrayList<>();
        for (Provider<AmbientEffects> provider : providers(engine, AmbientEffects.class)) {
            effects.addAll(provider.service().due(provider.world(), at));
        }
        return effects;
    }

    /** A plan and the module that made it, which hears how many of its entities appeared. */
    public record PlannedSpawn(EntitySpawns provider, PackWorld world, EntitySpawns.Plan plan) {
        public void spawned(int count) {
            provider.spawned(world, plan, count);
        }
    }

    /** Every structure key the pack's modules know, placed or not. */
    public static Set<String> structureKeys(IrisData data) {
        Set<String> keys = new LinkedHashSet<>();
        for (StructureLocator provider : catalog(data).services(StructureLocator.class)) {
            keys.addAll(structureKeysOf(data, provider.structures(data.getPackResources())));
        }
        return keys;
    }

    /** The keys of the structures the world's modules place and can locate. */
    public static Set<String> locatableStructures(Engine engine) {
        Set<String> keys = new LinkedHashSet<>();
        for (Provider<StructureLocator> provider : providers(engine, StructureLocator.class)) {
            keys.addAll(structureKeysOf(engine.getData(), provider.service().locatable(provider.world())));
        }
        return keys;
    }

    /** Whether a module of the world can locate structure {@code key}, matched without regard to case. */
    public static boolean canLocateStructure(Engine engine, String key) {
        return structureLocatorOf(engine, key, StructureLocator::locatable).isPresent();
    }

    /**
     * Every key under which the world's modules find what they place, locatable or not: their own structures and the
     * platform structures they start or stand in for.
     */
    public static Set<String> placedStructures(Engine engine) {
        Set<String> keys = new LinkedHashSet<>();
        for (Provider<StructureLocator> provider : providers(engine, StructureLocator.class)) {
            keys.addAll(structureKeysOf(engine.getData(), provider.service().placed(provider.world())));
        }
        return keys;
    }

    /** Whether a module of the world places structure {@code key}, located or not, matched without regard to case. */
    public static boolean placesStructure(Engine engine, String key) {
        return structureLocatorOf(engine, key, StructureLocator::placed).isPresent();
    }

    /**
     * Searches for the start of structure {@code key} nearest to {@code fromX}, {@code fromZ} with the first module that
     * places it, in the world's own coordinates; not found when none does.
     */
    public static StructureSearch locateStructure(Engine engine, String key, int fromX, int fromZ, int maxRadiusChunks) {
        LocateResult result = structureLocatorOf(engine, key, StructureLocator::placed)
                .map(provider -> provider.service().locate(provider.world(), structureIdOf(engine.getData(), key), fromX, fromZ,
                        maxRadiusChunks))
                .orElse(LocateResult.NOT_FOUND);
        return StructureSearch.of(result, engine);
    }

    /** The platform structures the world's modules start themselves, as lower-case registry keys. */
    public static Set<String> startedStructures(Engine engine) {
        return platformStructures(engine, StructureLocator::startedStructures);
    }

    /** Whether a module of the world starts the platform structure {@code key} itself. */
    public static boolean startsStructure(Engine engine, String key) {
        return key != null && startedStructures(engine).contains(normalized(key));
    }

    /** Whether a module of the world can find the starts it makes of the platform structure {@code key}. */
    public static boolean canLocateStart(Engine engine, String key) {
        return key != null && platformStructures(engine, StructureLocator::locatableStarts).contains(normalized(key));
    }

    /**
     * Searches ring by ring, up to {@code maxRings} rings, for the start the first module that can find them makes of the
     * platform structure {@code key} nearest to {@code fromX}, {@code fromZ}, trying only chunks {@code candidates}
     * accepts, in the world's own coordinates. A {@link StructureSearchLimitException} from {@code candidates} ends the
     * search as {@link StructureSearch.Outcome#SEARCH_LIMIT_REACHED}.
     */
    public static StructureSearch locateStart(Engine engine, String key, int fromX, int fromZ, int maxRings,
                                              CandidateChunks candidates) {
        Optional<ResourceId> structure = registryId(key);
        if (structure.isEmpty()) {
            return StructureSearch.NOT_FOUND;
        }
        for (Provider<StructureLocator> provider : providers(engine, StructureLocator.class)) {
            if (!provider.service().locatableStarts(provider.world()).contains(structure.get())) {
                continue;
            }
            try {
                return StructureSearch.of(provider.service().locateStart(provider.world(), structure.get(), fromX, fromZ,
                        maxRings, candidates::accepts), engine);
            } catch (StructureSearchLimitException limit) {
                return StructureSearch.SEARCH_LIMIT_REACHED;
            }
        }
        return StructureSearch.NOT_FOUND;
    }

    /** The chunks a search for a module's start of a platform structure may answer with. */
    @FunctionalInterface
    public interface CandidateChunks {
        boolean accepts(int chunkX, int chunkZ);
    }

    /** How a structure search ended. A found start is at {@code x}, {@code y}, {@code z}: world coordinates, Y the world's own. */
    public record StructureSearch(Outcome outcome, int x, int y, int z) {
        public enum Outcome { FOUND, NOT_FOUND, SEARCH_LIMIT_REACHED }

        public static final StructureSearch NOT_FOUND = new StructureSearch(Outcome.NOT_FOUND, 0, 0, 0);
        public static final StructureSearch SEARCH_LIMIT_REACHED = new StructureSearch(Outcome.SEARCH_LIMIT_REACHED, 0, 0, 0);

        public boolean found() {
            return outcome == Outcome.FOUND;
        }

        /** {@code result} with its height above the dimension's bottom turned into the world's Y. */
        static StructureSearch of(LocateResult result, Engine engine) {
            return switch (result) {
                case LocateResult.Found found -> new StructureSearch(Outcome.FOUND, found.position().x(),
                        found.position().y() + engine.getMinHeight(), found.position().z());
                case LocateResult.NotFound ignored -> NOT_FOUND;
                case LocateResult.SearchLimitReached ignored -> SEARCH_LIMIT_REACHED;
            };
        }
    }

    /** The first provider, in the catalog's order, whose structures of the kind {@code structures} lists hold {@code key}. */
    private static Optional<Provider<StructureLocator>> structureLocatorOf(Engine engine, String key,
                                                                          BiFunction<StructureLocator, PackWorld, Set<ResourceId>> structures) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        ResourceId wanted = structureIdOf(engine.getData(), key);
        for (Provider<StructureLocator> provider : providers(engine, StructureLocator.class)) {
            if (structures.apply(provider.service(), provider.world()).contains(wanted)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    /**
     * The id of the structure Iris's commands name {@code key}: a key with a namespace is that id
     * ({@code minecraft:village}), any other a structure of the pack. Ids are lower case, so keys match without regard
     * to case.
     */
    private static ResourceId structureIdOf(IrisData data, String key) {
        String trimmed = key.trim();
        return trimmed.indexOf(':') >= 0 ? ResourceId.parse(normalized(trimmed)) : data.getPackResources().idOf(trimmed);
    }

    /** The keys Iris's commands show for {@code structures}: a pack structure by its path, any other by its id. */
    private static Set<String> structureKeysOf(IrisData data, Set<ResourceId> structures) {
        IrisPackResources resources = data.getPackResources();
        Set<String> keys = new LinkedHashSet<>();
        for (ResourceId structure : structures) {
            keys.add(resources.keyOf(structure));
        }
        return keys;
    }

    /** The platform structures every provider lists with {@code structures}, as lower-case registry keys. */
    private static Set<String> platformStructures(Engine engine, BiFunction<StructureLocator, PackWorld, Set<ResourceId>> structures) {
        Set<String> keys = new LinkedHashSet<>();
        for (Provider<StructureLocator> provider : providers(engine, StructureLocator.class)) {
            structures.apply(provider.service(), provider.world()).forEach(id -> keys.add(id.toString()));
        }
        return keys;
    }

    private static Optional<ResourceId> registryId(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ResourceId.parse(normalized(key.trim())));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    private static String normalized(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    private static FeatureCatalog catalog(IrisData data) {
        return data.getModules().pack().catalog();
    }
}
