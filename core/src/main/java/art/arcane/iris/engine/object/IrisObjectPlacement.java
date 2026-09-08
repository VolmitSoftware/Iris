/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

import art.arcane.iris.core.compat.CompatAction;
import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.CompatRegistry;
import art.arcane.iris.core.compat.CompatStatus;
import art.arcane.iris.core.compat.ContentGate;
import art.arcane.iris.core.compat.PackCompatReport;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.data.cache.LazyBoundedCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.noise.CNG;
import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.TreeType;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

@Snippet("object-placer")
@EqualsAndHashCode()
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an iris object placer. It places objects.")
@Data
public class IrisObjectPlacement {
    private static final int SURFACE_WARP_CACHE_SIZE = 8;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient LazyBoundedCache<SurfaceWarpCacheKey, CNG> surfaceWarpCache =
            new LazyBoundedCache<>(SURFACE_WARP_CACHE_SIZE);
    @RegistryListResource(IrisObject.class)
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("List of objects to place")
    private KList<String> place = new KList<>();
    @Desc("Rotate this objects placement")
    private IrisObjectRotation rotation = new IrisObjectRotation();
    @Desc("Limit the max height or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The maximum layer level of a snow filter overtop of this placement. Set to 0 to disable. Max of 1.")
    private double snow = 0;
    @Desc("Whether or not this object can be targeted by a dolphin.")
    private boolean isDolphinTarget = false;
    @Desc("The slope at which this object can be placed. Range from 0 to 10 by default. Calculated from a 3-block radius from the center of the object placement.")
    private IrisSlopeClip slopeCondition = new IrisSlopeClip();
    @Desc("Set to true to add the rotation of the direction of the slope of the terrain (wherever the slope is going down) to the y-axis rotation of the object." +
            "Rounded to 90 degrees. Adds the *min* rotation of the y axis as well (to still allow you to rotate objects nicely). Discards *max* and *interval* on *yaxis*")
    private boolean rotateTowardsSlope = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance for this to place in a chunk. If you need multiple per chunk, set this to 1 and use density.")
    private double chance = 1;
    @MinNumber(1)
    @Desc("If the chance check passes, place this many in a single chunk")
    private int density = 1;
    @Desc("If the chance check passes, and you specify this, it picks a number in the range based on noise, and 'density' is ignored.")
    private IrisStyledRange densityStyle = null;
    @Desc("When stilting is enabled, this object will define various properties related to it.")
    private IrisStiltSettings stiltSettings;
    @Desc("When a VACUUM place mode is used, this defines the deformation radius and falloff.")
    private IrisVacuumSettings vacuumSettings;
    @MaxNumber(64)
    @MinNumber(0)
    @Desc("When bore is enabled, expand max-y of the cuboid it removes")
    private int boreExtendMaxY = 0;
    @ArrayType(min = 1, type = IrisObjectMarker.class)
    @Desc("Add markers to blocks in this object")
    private KList<IrisObjectMarker> markers = new KList<>();
    @MaxNumber(64)
    @MinNumber(-1)
    @Desc("When bore is enabled, lower min-y of the cuboid it removes")
    private int boreExtendMinY = 0;
    @Desc("If set to true, objects will place on the terrain height, ignoring the water surface.")
    private boolean underwater = false;
    @Desc("If set to true, objects will place in carvings (such as underground) or under an overhang.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;
    @MinNumber(0)
    @MaxNumber(16)
    @Desc("Extra buffer, in blocks, around this object's base footprint that must also be solid, un-carved ground. 0 checks only the footprint itself.")
    private int surfaceSupportBuffer = 2;
    @MinNumber(1)
    @MaxNumber(16)
    @Desc("Minimum thickness, in blocks, of un-carved ground required under every column of this object's base footprint.")
    private int surfaceSupportDepth = 2;
    @Desc("Set to false to let this placement sit over carved surface openings such as cave entrances.")
    private boolean requireSurfaceSupport = true;
    @Desc("When carving placement is enabled, select which carved-space anchor this placement targets.")
    private IrisCaveAnchorMode caveAnchorMode = IrisCaveAnchorMode.PROFILE_DEFAULT;
    @Desc("If this is defined, this object wont place on the terrain heightmap, but instead on this virtual heightmap")
    private IrisNoiseGenerator heightmap;
    @Desc("If set to true, Iris will try to fill the insides of 'rooms' and 'pockets' where air should fit based off of raytrace checks. This prevents a village house placing in an area where a tree already exists, and instead replaces the parts of the tree where the interior of the structure is. \n\nThis operation does not affect warmed-up generation speed however it does slow down loading objects.")
    private boolean smartBore = false;
    @Desc("If set to true, Blocks placed underwater that could be waterlogged are waterlogged.")
    private boolean waterloggable = false;
    @Desc("If set to true, objects will place on the fluid height level Such as boats.")
    private boolean onwater = false;
    @Desc("If set to true, this object will only place parts of itself where blocks already exist. Warning: Melding is very performance intensive!")
    private boolean meld = false;
    @Desc("If set to true, this object will get placed from the bottom of the world up")
    private boolean fromBottom;
    @Desc("If set to true, this object will place from the ground up instead of height checks when not y locked to the surface. This is not compatable with X and Z axis rotations (it may look off)")
    private boolean bottom = false;
    @Desc("If set to true, air will be placed before the schematic places.")
    private boolean bore = false;
    @Desc("Use a generator to warp the field of coordinates. Using simplex for example would make a square placement warp like a flag")
    private IrisGeneratorStyle warp = new IrisGeneratorStyle(NoiseStyle.FLAT);
    @Desc("The placement mode")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;
    @ArrayType(min = 1, type = IrisObjectReplace.class)
    @Desc("Find and replace blocks")
    private KList<IrisObjectReplace> edit = new KList<>();
    @Desc("Translate this object's placement")
    private IrisObjectTranslate translate = new IrisObjectTranslate();
    @Desc("Explicit object scale. When omitted, the dimension's allObjectScaleFactor applies. Any authored scale, including size 1, overrides that factor.")
    private IrisObjectScale scale;
    @ArrayType(min = 1, type = IrisObjectLoot.class)
    @Desc("The loot tables to apply to these objects")
    private KList<IrisObjectLoot> loot = new KList<>();
    @ArrayType(min = 1, type = IrisObjectVanillaLoot.class)
    @Desc("The vanilla loot tables to apply to these objects")
    private KList<IrisObjectVanillaLoot> vanillaLoot = new KList<>();
    @Desc("Whether the given loot tables override any and all other loot tables available in the dimension, region or biome.")
    private boolean overrideGlobalLoot = false;
    @Desc("This object / these objects override the following trees when they grow...")
    @ArrayType(min = 1, type = IrisTree.class)
    private KList<IrisTree> trees = new KList<>();
    @RegistryListResource(IrisObject.class)
    @ArrayType(type = String.class)
    @Desc("List of objects to this object is allowed to collied with")
    private KList<String> allowedCollisions = new KList<>();
    @RegistryListResource(IrisObject.class)
    @ArrayType(type = String.class)
    @Desc("List of objects to this object is forbidden to collied with")
    private KList<String> forbiddenCollisions = new KList<>();
    @Desc("Ignore any placement restrictions for this object")
    @SerializedName(value = "forcePlace", alternate = {"force"})
    private boolean forcePlace = false;
    private transient AtomicCache<TableCache> cache = new AtomicCache<>();
    private transient AtomicCache<CompatStatus> compatCache = new AtomicCache<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient volatile KList<String> compatPlaceCache;

    /**
     * Version-content gate verdict for this placement on the running server. The placement's own generated blocks
     * (every {@code edit[].replace} palette option) exclude the whole placement when one of them is unavailable; each
     * object in {@code place} is then dropped when its .iob palette carries a key that neither resolves nor is
     * rewritten by a matching {@code edit} rule with chance 1. Match sides ({@code edit[].find}, {@code markers[].mark})
     * never count. Cached per placement.
     */
    public CompatStatus evaluateCompat(IrisData data) {
        return compatCache.aquire(() -> computeCompat(data));
    }

    /** True when nothing in this placement can generate on the running server. */
    public boolean isCompatExcluded(IrisData data) {
        return evaluateCompat(data).excluded();
    }

    /** The {@code place} pool with objects that cannot generate on the running server removed. */
    public KList<String> compatPlace(IrisData data) {
        evaluateCompat(data);
        KList<String> survivors = compatPlaceCache;
        return survivors == null ? place : survivors;
    }

    private CompatStatus computeCompat(IrisData data) {
        if (data == null) {
            compatPlaceCache = place;
            return CompatStatus.OK;
        }

        ContentGate gate = data.getContentGate();
        PackCompatReport report = data.getCompatReport();

        if (gate == null || !gate.ready()) {
            // Registry not bound or not ready: UNKNOWN is never MISSING, so nothing is gated.
            report.markIncomplete("registry not ready while evaluating object placements");
            compatPlaceCache = place;
            return CompatStatus.OK;
        }

        String subject = compatSubject();
        KList<CompatFinding> reasons = new KList<>();
        CompatFinding blocked = IrisObjectCompat.evaluateEditPalettes(edit, data, gate, "placement", subject, "", reasons);
        IrisObjectCompat.record(report, reasons);

        if (blocked != null) {
            compatPlaceCache = new KList<>();
            return CompatStatus.excludedBy(reasons);
        }

        KList<String> survivors = new KList<>(place.size());
        CompatFinding firstDrop = null;

        for (int i = 0; i < place.size(); i++) {
            String objectKey = place.get(i);
            KList<CompatFinding> objectReasons = new KList<>();
            KList<String> missing = IrisObjectCompat.unplaceableKeys(objectKey, edit, data, gate, "object", objectKey,
                    subject + " place[" + i + "]", objectReasons);
            IrisObjectCompat.record(report, objectReasons);
            reasons.addAll(objectReasons);

            if (missing.isEmpty()) {
                survivors.add(objectKey);
                continue;
            }

            // One finding per missing key, so the operator sees every fallback the object needs at once.
            for (String key : missing) {
                CompatFinding dropped = new CompatFinding(CompatRegistry.BLOCK, key, CompatAction.DROPPED,
                        "object", objectKey, subject + " place[" + i + "]");
                report.record(dropped);
                reasons.add(dropped);

                if (firstDrop == null) {
                    firstDrop = dropped;
                }
            }
        }

        if (firstDrop != null && survivors.isEmpty()) {
            CompatFinding excluded = new CompatFinding(firstDrop.registry(), firstDrop.key(), CompatAction.EXCLUDED,
                    "placement", subject, "no objects remain");
            report.record(excluded);
            reasons.add(excluded);
            compatPlaceCache = new KList<>();
            return CompatStatus.excludedBy(reasons);
        }

        compatPlaceCache = survivors.size() == place.size() ? place : survivors;
        return new CompatStatus(false, reasons);
    }

    /** The subject a finding names: the single object, or the whole place list when there is more than one. */
    private String compatSubject() {
        if (place.isEmpty()) {
            return "";
        }
        return place.size() == 1 ? place.getFirst() : place.toString(", ");
    }

    public IrisObjectPlacement toPlacement(String... place) {
        IrisObjectPlacement p = new IrisObjectPlacement();
        p.setPlace(new KList<>(place));
        p.setMode(mode);
        p.setEdit(edit);
        p.setTranslate(translate);
        p.setScale(scale);
        p.setWarp(warp);
        p.setBore(bore);
        p.setMeld(meld);
        p.setWaterloggable(waterloggable);
        p.setOnwater(onwater);
        p.setFromBottom(fromBottom);
        p.setBottom(bottom);
        p.setSmartBore(smartBore);
        p.setCarvingSupport(carvingSupport);
        p.setSurfaceSupportBuffer(surfaceSupportBuffer);
        p.setSurfaceSupportDepth(surfaceSupportDepth);
        p.setRequireSurfaceSupport(requireSurfaceSupport);
        p.setCaveAnchorMode(caveAnchorMode);
        p.setUnderwater(underwater);
        p.setHeightmap(heightmap);
        p.setBoreExtendMaxY(boreExtendMaxY);
        p.setBoreExtendMinY(boreExtendMinY);
        p.setStiltSettings(stiltSettings);
        p.setVacuumSettings(vacuumSettings);
        p.setDensity(density);
        p.setDensityStyle(densityStyle);
        p.setChance(chance);
        p.setSnow(snow);
        p.setClamp(clamp);
        p.setRotation(rotation);
        p.setSlopeCondition(slopeCondition);
        p.setRotateTowardsSlope(rotateTowardsSlope);
        p.setDolphinTarget(isDolphinTarget);
        p.setMarkers(markers);
        p.setLoot(loot);
        p.setVanillaLoot(vanillaLoot);
        p.setOverrideGlobalLoot(overrideGlobalLoot);
        p.setTrees(trees);
        p.setAllowedCollisions(allowedCollisions);
        p.setForbiddenCollisions(forbiddenCollisions);
        p.setForcePlace(forcePlace);
        return p;
    }

    public CNG getSurfaceWarp(RNG rng, IrisData data) {
        return getSurfaceWarp(rng, data, data.getEngine());
    }

    public CNG getSurfaceWarp(RNG rng, IrisData data, @Nullable Engine engine) {
        long seed = engine == null ? rng.getSeed() : engine.getSeedManager().getComponent() + 1024L;
        SurfaceWarpCacheKey key = new SurfaceWarpCacheKey(data, engine, seed);
        return surfaceWarpCache.computeIfAbsent(key,
                ignored -> getWarp().create(new RNG(seed), data, engine));
    }

    public double warp(RNG rng, double x, double y, double z, IrisData data) {
        return getSurfaceWarp(rng, data).fitDouble(-(getWarp().getMultiplier() / 2D), (getWarp().getMultiplier() / 2D), x, y, z);
    }

    public IrisObject getObject(DataProvider g, RNG random) {
        IrisData data = g.getData();
        KList<String> pool = compatPlace(data);

        if (pool.isEmpty()) {
            return null;
        }

        return data.getObjectLoader().load(pool.get(random.nextInt(pool.size())));
    }

    public IrisObject scaleObject(RNG random, IrisObject object, IrisDimension dimension) {
        return scale == null
                ? IrisObjectScale.getFixed(object, dimension == null ? 1D : dimension.getAllObjectScaleFactor())
                : scale.get(random, object);
    }

    public double getMaximumScale(IrisDimension dimension) {
        return scale == null
                ? dimension == null ? 1D : dimension.getAllObjectScaleFactor()
                : scale.getMaxScale();
    }

    public boolean matches(IrisTreeSize size, TreeType type) {
        for (IrisTree i : getTrees()) {
            if (i.matches(size, type)) {
                return true;
            }
        }

        return false;
    }

    public int getDensity() {
        if (densityStyle == null) {
            return density;
        }
        return densityStyle.getMid();
    }

    public int getDensity(RNG rng, double x, double z, IrisData data) {
        if (densityStyle == null) {
            return density;
        }

        return (int) Math.round(densityStyle.get(rng, x, z, data));
    }

    private TableCache getCache(IrisData manager) {
        return cache.aquire(() -> {
            TableCache cache = new TableCache();

            cache.merge(getCache(manager, getVanillaLoot(), IrisObjectPlacement::getVanillaTable));
            cache.merge(getCache(manager, getLoot(), manager.getLootLoader()::load));

            return cache;
        });
    }

    private TableCache getCache(IrisData manager, KList<? extends IObjectLoot> list, Function<String, IrisLootTable> loader) {
        TableCache tc = new TableCache();

        for (IObjectLoot loot : list) {
            if (loot == null || loot.getWeight() <= 0)
                continue;
            IrisLootTable table = loader.apply(loot.getName());
            if (table == null) {
                IrisLogging.warn("Couldn't find loot table " + loot.getName());
                continue;
            }
            if (table.isCompatExcluded()) {
                continue;
            }

            if (loot.getFilter().isEmpty()) //Table applies to all containers
            {
                tc.global.put(table, loot.getWeight());
            } else if (!loot.isExact()) //Table is meant to be by type
            {
                for (PlatformBlockState filterState : loot.getFilter(manager)) {
                    BlockData filterData = (BlockData) filterState.nativeHandle();
                    if (!tc.basic.containsKey(filterData.getMaterial())) {
                        tc.basic.put(filterData.getMaterial(), new WeightedTables());
                    }

                    tc.basic.get(filterData.getMaterial()).put(table, loot.getWeight());
                }
            } else //Filter is exact
            {
                for (PlatformBlockState filterState : loot.getFilter(manager)) {
                    BlockData filterData = (BlockData) filterState.nativeHandle();
                    if (!tc.exact.containsKey(filterData.getMaterial())) {
                        tc.exact.put(filterData.getMaterial(), new KMap<>());
                    }

                    if (!tc.exact.get(filterData.getMaterial()).containsKey(filterData)) {
                        tc.exact.get(filterData.getMaterial()).put(filterData, new WeightedTables());
                    }

                    tc.exact.get(filterData.getMaterial()).get(filterData).put(table, loot.getWeight());
                }
            }
        }
        return tc;
    }

    @Nullable
    private static IrisVanillaLootTable getVanillaTable(String name) {
        return Optional.ofNullable(NamespacedKey.fromString(name))
                .map(Bukkit::getLootTable)
                // Hand over the key, not the LootTable: IrisVanillaLootTable holds no Bukkit fields.
                .map(table -> new IrisVanillaLootTable(String.valueOf(table.getKey())))
                .orElse(null);
    }

    /**
     * Gets the loot table that should be used for the block
     *
     * @param state       The block state of the block
     * @param dataManager Iris Data Manager
     * @return The loot table it should use.
     */
    public IrisLootTable getTable(PlatformBlockState state, IrisData dataManager, RNG rng) {
        TableCache cache = getCache(dataManager);
        BlockData data = (BlockData) state.nativeHandle();
        if (BukkitBlockResolution.isStorageChest(data)) {
            IrisLootTable picked = null;
            if (cache.exact.containsKey(data.getMaterial()) && cache.exact.get(data.getMaterial()).containsKey(data)) {
                picked = cache.exact.get(data.getMaterial()).get(data).pullRandom(rng);
            } else if (cache.basic.containsKey(data.getMaterial())) {
                picked = cache.basic.get(data.getMaterial()).pullRandom(rng);
            } else if (!cache.global.isEmpty()) {
                picked = cache.global.pullRandom(rng);
            }

            return picked;
        }

        return null;
    }

    private static final class SurfaceWarpCacheKey {
        private final IrisData data;
        private final Engine engine;
        private final long seed;

        private SurfaceWarpCacheKey(IrisData data, Engine engine, long seed) {
            this.data = data;
            this.engine = engine;
            this.seed = seed;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SurfaceWarpCacheKey key)) {
                return false;
            }
            return data == key.data && engine == key.engine && seed == key.seed;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(data);
            result = 31 * result + System.identityHashCode(engine);
            return 31 * result + Long.hashCode(seed);
        }
    }

    private static class TableCache {
        final transient WeightedTables global = new WeightedTables();
        final transient KMap<Material, WeightedTables> basic = new KMap<>();
        final transient KMap<Material, KMap<BlockData, WeightedTables>> exact = new KMap<>();

        private void merge(TableCache other) {
            global.merge(other.global);
            basic.merge(other.basic, WeightedTables::merge);
            exact.merge(other.exact, (a, b) -> a.merge(b, WeightedTables::merge));
        }
    }

    private static final class WeightedTables {
        private final KList<WeightedTable> tables = new KList<>();

        private void put(IrisLootTable table, int weight) {
            tables.add(new WeightedTable(table, weight));
        }

        private IrisLootTable pullRandom(RNG rng) {
            WeightedTable picked = LootResolver.pickWeighted(tables, WeightedTable::weight, rng);
            return picked == null ? null : picked.table();
        }

        private boolean isEmpty() {
            return tables.isEmpty();
        }

        private WeightedTables merge(WeightedTables other) {
            tables.addAll(other.tables);
            return this;
        }
    }

    private record WeightedTable(IrisLootTable table, int weight) {
    }
}
