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
import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemDrop;
import com.volmit.service.AmbientEffects;
import com.volmit.service.BlockDrops;
import com.volmit.service.CaveBiomes;
import com.volmit.service.EntitySpawns;
import com.volmit.service.LocateResult;
import com.volmit.service.Loot;
import com.volmit.service.ObjectsAt;
import com.volmit.service.SaplingGrowth;
import com.volmit.service.StructureLocator;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What Iris core asks the pack's modules through their services ({@link com.volmit.service.Service}) instead of reading
 * their features itself. Each method combines the providers the way their service says. Asking a pack whose modules
 * fail to load throws its {@link PackModulesException}.
 */
public final class ModuleServices {
    private ModuleServices() {
    }

    /** What the module that decides cave biomes says for the engine's world, kept for the world. */
    public static CaveBiomeAnswers caveBiomes(Engine engine) {
        return engine.getGeneration().state(CaveBiomeAnswers.class, owner ->
                new CaveBiomeAnswers(owner, catalog(owner.getData()).service(CaveBiomes.class).orElse(null)));
    }

    /** The objects every module would place in the chunk at {@code chunkX}, {@code chunkZ}, guessed without generating it. */
    public static Set<String> objectsAt(Engine engine, int chunkX, int chunkZ) {
        PackWorld world = engine.getGeneration().world();
        Set<String> keys = new LinkedHashSet<>();
        for (ObjectsAt provider : catalog(engine.getData()).services(ObjectsAt.class)) {
            keys.addAll(provider.objectsAt(world, chunkX, chunkZ));
        }
        return keys;
    }

    /** The biomes and regions of the engine's layout that every module says place the object {@code objectKey}. */
    public static ObjectsAt.PlacedBy objectPlacers(Engine engine, String objectKey) {
        PackWorld world = engine.getGeneration().world();
        Set<String> biomes = new LinkedHashSet<>();
        Set<String> regions = new LinkedHashSet<>();
        for (ObjectsAt provider : catalog(engine.getData()).services(ObjectsAt.class)) {
            ObjectsAt.PlacedBy placedBy = provider.placedBy(world, objectKey);
            biomes.addAll(placedBy.biomes());
            regions.addAll(placedBy.regions());
        }
        return new ObjectsAt.PlacedBy(biomes, regions);
    }

    /** The loot tables the modules say the container at {@code site} draws from, in module order. */
    public static List<Loot.Source> lootTables(Engine engine, Loot.Site site) {
        PackWorld world = engine.getGeneration().world();
        List<Loot.Source> tables = new ArrayList<>();
        for (Loot provider : catalog(engine.getData()).services(Loot.class)) {
            tables.addAll(provider.tables(world, site));
        }
        return tables;
    }

    /** The items loot table {@code key} gives at {@code site}, from the module that knows the table. */
    public static List<ItemDrop> lootItems(Engine engine, Loot.Site site, String key) {
        PackWorld world = engine.getGeneration().world();
        List<ItemDrop> items = new ArrayList<>();
        for (Loot provider : catalog(engine.getData()).services(Loot.class)) {
            items.addAll(provider.items(world, site, key));
        }
        return items;
    }

    /** What the modules drop for the broken block, put together. */
    public static BlockDrops.Result blockDrops(Engine engine, BlockDrops.Broken broken) {
        PackWorld world = engine.getGeneration().world();
        List<ItemDrop> drops = new ArrayList<>();
        boolean replacesVanillaDrops = false;
        for (BlockDrops provider : catalog(engine.getData()).services(BlockDrops.class)) {
            BlockDrops.Result result = provider.drops(world, broken);
            drops.addAll(result.drops());
            replacesVanillaDrops |= result.replacesVanillaDrops();
        }
        return new BlockDrops.Result(drops, replacesVanillaDrops);
    }

    /** The tree grown by the first module that grows one for {@code sapling}; empty leaves the growth to the platform. */
    public static Optional<SaplingGrowth.Growth> growSapling(Engine engine, SaplingGrowth.Sapling sapling, SaplingGrowth.Surroundings surroundings) {
        PackWorld world = engine.getGeneration().world();
        for (SaplingGrowth provider : catalog(engine.getData()).services(SaplingGrowth.class)) {
            Optional<SaplingGrowth.Growth> growth = provider.grow(world, sapling, surroundings);
            if (growth.isPresent()) {
                return growth;
            }
        }
        return Optional.empty();
    }

    /** The objects every module places as trees. */
    public static SaplingGrowth.TreeObjects treeObjects(Engine engine) {
        PackWorld world = engine.getGeneration().world();
        Set<String> objects = new LinkedHashSet<>();
        Set<String> procedural = new LinkedHashSet<>();
        for (SaplingGrowth provider : catalog(engine.getData()).services(SaplingGrowth.class)) {
            SaplingGrowth.TreeObjects trees = provider.treeObjects(world);
            objects.addAll(trees.objects());
            procedural.addAll(trees.procedural());
        }
        return new SaplingGrowth.TreeObjects(objects, procedural);
    }

    /** What the first module with something to spawn at {@code site} plans; empty when none does. */
    public static Optional<PlannedSpawn> planSpawn(Engine engine, EntitySpawns.Site site) {
        PackWorld world = engine.getGeneration().world();
        for (EntitySpawns provider : catalog(engine.getData()).services(EntitySpawns.class)) {
            Optional<EntitySpawns.Plan> plan = provider.plan(world, site);
            if (plan.isPresent()) {
                return Optional.of(new PlannedSpawn(provider, world, plan.get()));
            }
        }
        return Optional.empty();
    }

    /** The entity {@code key} as the first module that knows it would spawn it. */
    public static Optional<EntitySpec> entity(Engine engine, String key) {
        PackWorld world = engine.getGeneration().world();
        for (EntitySpawns provider : catalog(engine.getData()).services(EntitySpawns.class)) {
            Optional<EntitySpec> entity = provider.entity(world, key);
            if (entity.isPresent()) {
                return entity;
            }
        }
        return Optional.empty();
    }

    /** What the first module that knows the marker {@code tag} says it does. */
    public static Optional<EntitySpawns.MarkerRule> markerRule(Engine engine, String tag) {
        PackWorld world = engine.getGeneration().world();
        for (EntitySpawns provider : catalog(engine.getData()).services(EntitySpawns.class)) {
            Optional<EntitySpawns.MarkerRule> rule = provider.marker(world, tag);
            if (rule.isPresent()) {
                return rule;
            }
        }
        return Optional.empty();
    }

    /** The ambient effects every module says are due for a player standing at {@code at}, in module order. */
    public static List<AmbientEffects.Effect> ambientEffects(Engine engine, BlockPosition at) {
        PackWorld world = engine.getGeneration().world();
        List<AmbientEffects.Effect> effects = new ArrayList<>();
        for (AmbientEffects provider : catalog(engine.getData()).services(AmbientEffects.class)) {
            effects.addAll(provider.due(world, at));
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
            keys.addAll(provider.structures(data.getPackResources()));
        }
        return keys;
    }

    /** The keys of the structures the world's modules place and can locate. */
    public static Set<String> locatableStructures(Engine engine) {
        PackWorld world = engine.getGeneration().world();
        Set<String> keys = new LinkedHashSet<>();
        for (StructureLocator provider : catalog(engine.getData()).services(StructureLocator.class)) {
            keys.addAll(provider.locatable(world));
        }
        return keys;
    }

    /** Whether a module of the world can locate structure {@code key}, matched without regard to case. */
    public static boolean canLocateStructure(Engine engine, String key) {
        return structureLocatorOf(engine, key).isPresent();
    }

    /**
     * Searches for the start of structure {@code key} nearest to {@code fromX}, {@code fromZ} with the first module that
     * can locate it, in the world's own coordinates; not found when none can.
     */
    public static StructureSearch locateStructure(Engine engine, String key, int fromX, int fromZ, int maxRadiusChunks) {
        PackWorld world = engine.getGeneration().world();
        LocateResult result = structureLocatorOf(engine, key)
                .map(provider -> provider.locate(world, key, fromX, fromZ, maxRadiusChunks))
                .orElse(LocateResult.NOT_FOUND);
        return StructureSearch.of(result, engine);
    }

    /** How a structure search ended. A found start is at {@code x}, {@code y}, {@code z}: world coordinates, Y the world's own. */
    public record StructureSearch(Outcome outcome, int x, int y, int z) {
        public enum Outcome { FOUND, NOT_FOUND, SEARCH_LIMIT_REACHED }

        private static final StructureSearch NOT_FOUND = new StructureSearch(Outcome.NOT_FOUND, 0, 0, 0);
        private static final StructureSearch SEARCH_LIMIT_REACHED = new StructureSearch(Outcome.SEARCH_LIMIT_REACHED, 0, 0, 0);

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

    /** The first provider, in the catalog's order, whose locatable structures hold {@code key}. */
    private static Optional<StructureLocator> structureLocatorOf(Engine engine, String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        PackWorld world = engine.getGeneration().world();
        String wanted = normalized(key);
        for (StructureLocator provider : catalog(engine.getData()).services(StructureLocator.class)) {
            if (provider.locatable(world).stream().map(ModuleServices::normalized).anyMatch(wanted::equals)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    private static String normalized(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    private static FeatureCatalog catalog(IrisData data) {
        return data.getModules().pack().catalog();
    }
}
