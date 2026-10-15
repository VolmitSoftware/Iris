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

package art.arcane.iris.engine.object.world.dimension;

import art.arcane.iris.engine.object.image.IrisImageMap;
import art.arcane.iris.engine.object.image.IrisImageMapApplication;
import art.arcane.iris.engine.object.image.IrisImageMapBinding;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.IrisShapedGeneratorStyle;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.engine.object.procedural.tree.IrisTreeSettings;
import art.arcane.iris.engine.object.runtime.StudioMode;
import art.arcane.iris.engine.object.value.IrisRange;
import art.arcane.iris.engine.object.world.IrisEnvironment;
import art.arcane.iris.engine.object.world.IrisWorldBoundary;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.core.IrisDatapackCompiler.DimensionHeight;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.core.nms.datapack.IDataFixer;
import art.arcane.iris.core.nms.datapack.IDataFixer.Dimension;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListFunction;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.functions.ComponentFlagFunction;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.noise.CNG;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("Represents a dimension")
@Data
@EqualsAndHashCode(callSuper = false, doNotUseGetters = true)
@ToString(doNotUseGetters = true)
public class IrisDimension extends IrisRegistrant {
    private static final Pattern RESOURCE_KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private final transient AtomicCache<Position2> parallaxSize = new AtomicCache<>();
    private final transient AtomicCache<CNG> rockLayerGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> fluidLayerGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> coordFracture = new AtomicCache<>();
    private final transient AtomicCache<Double> sinr = new AtomicCache<>();
    private final transient AtomicCache<Double> cosr = new AtomicCache<>();
    private final transient AtomicCache<Double> rad = new AtomicCache<>();
    private final transient AtomicCache<Boolean> featuresUsed = new AtomicCache<>();
    @MinNumber(2)
    @Required
    @Desc("The human readable name of this dimension")
    private String name = "A Dimension";
    @MinNumber(1)
    @MaxNumber(2032)
    @Desc("Maximum height at which players can be teleported to through gameplay.")
    private int logicalHeight = 256;
    @Desc("If set to true, Iris will remove chunks to allow visualizing cross sections of chunks easily")
    private boolean debugChunkCrossSections = false;
    @Desc("Studio Mode for testing different parts of the world")
    private StudioMode studioMode = StudioMode.NORMAL;
    @MinNumber(2)
    @MaxNumber(16)
    @Desc("Every X/Z % debugCrossSectionsMod == 0 cuts the chunk")
    private int debugCrossSectionsMod = 3;
    @Desc("Tree growth override settings")
    private IrisTreeSettings treeSettings = new IrisTreeSettings();
    @MinNumber(0)
    @Desc("The version of this dimension. Changing this will stop users from accidentally upgrading (and breaking their worlds).")
    private int version = 1;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The land chance. Up to 1.0 for total land or 0.0 for total sea")
    private double landChance = 0.625;
    @Desc("The placement style of regions")
    private IrisGeneratorStyle regionStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The placement style of land/sea")
    private IrisGeneratorStyle continentalStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The noise style used to select land biomes within a region")
    private IrisGeneratorStyle landBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The noise style used to select shore biomes within a region")
    private IrisGeneratorStyle shoreBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The noise style used to select sea biomes within a region")
    private IrisGeneratorStyle seaBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The noise style used to select cave biomes within a region")
    private IrisGeneratorStyle caveBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("Instead of filling objects with air, fills them with cobweb so you can see them")
    private boolean debugSmartBore = false;
    @Desc("Refuse to place surface objects and trees over carved surface openings.")
    private boolean requireObjectSurfaceSupport = true;
    @MinNumber(0)
    @MaxNumber(16)
    @Desc("Minimum surface-support buffer, in blocks, applied to every surface object placement in this dimension. A placement may ask for more but never less.")
    private int objectSurfaceSupportBuffer = 2;
    @Desc("The world environment")
    private IrisEnvironment environment = IrisEnvironment.NORMAL;
    @RegistryListResource(IrisRegion.class)
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("Define all of the regions to include in this dimension. Dimensions -> Regions -> Biomes -> Objects etc")
    private KList<String> regions = new KList<>();
    @Required
    @MinNumber(0)
    @MaxNumber(1024)
    @Desc("The fluid (sea level) height for this dimension, in world Y. Water fills every sea column up to this height, so 63 puts the water surface at world Y 63.")
    private int fluidHeight = 63;
    @Desc("Define the min and max Y bounds of this dimension. Please keep in mind that Iris internally generates from 0 to (max - min). \n\nFor example at -64 to 320, Iris is internally generating to 0 to 384, then on outputting chunks, it shifts it down by the min height (64 blocks). The default is -64 to 320. \n\nfluidHeight stays in world Y: the engine converts it internally by subtracting the min height, so a fluid height of 63 shows up in the world at Y 63.")
    private IrisRange dimensionHeight = new IrisRange(-64, 320);
    @Desc("Define options for this dimension")
    private IrisDimensionTypeOptions dimensionOptions = new IrisDimensionTypeOptions();
    @Desc("Configure and enforce the native Minecraft world border. When omitted, Iris leaves the native border unchanged.")
    private IrisWorldBoundary worldBoundary = null;
    @ArrayType(type = IrisImageMapBinding.class)
    @Desc("Bind reusable image-map resources to typed world-generation inputs")
    private KList<IrisImageMapBinding> imageMaps = new KList<>();
    @Desc("When true, sets the dimension's ambient light to maximum, making all blocks fully lit regardless of light level.")
    private boolean fullbright = false;
    @RegistryListResource(IrisDimension.class)
    @Desc("When set, generates the referenced dimension's terrain upside-down at the world ceiling, creating a nether-like canopy. Use the dimension's load key (e.g., 'overworld'). Set to 'none' to disable.")
    private String upperDimension = "none";
    @MinNumber(0)
    @MaxNumber(256)
    @Desc("Minimum air gap in blocks between the lower terrain surface and the upper terrain surface.")
    private int upperDimensionGap = 32;
    @Desc("When true, objects from the mantle (structures, trees, etc.) can be placed in the upper dimension terrain zone. When false, the upper terrain is protected from object placement.")
    private boolean upperDimensionObjects = false;
    @Desc("When true, upper-dimension objects force-place regardless of placement restrictions (slope, underwater, clamp, collisions, carving). Normal dimension objects always place first; upper objects place second and may clip or occlude lower-dimension placements when this is enabled.")
    private boolean upperObjectsForcePlace = false;
    @RegistryListResource(IrisBiome.class)
    @Desc("Keep this either undefined or empty. Setting any biome name into this will force iris to only generate the specified biome. Great for testing.")
    private String focus = "";
    @RegistryListResource(IrisRegion.class)
    @Desc("Keep this either undefined or empty. Setting any region name into this will force iris to only generate the specified region. Great for testing.")
    private String focusRegion = "";
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("Zoom in or out the biome size. Higher = bigger biomes")
    private double biomeZoom = 1D;
    @MinNumber(0)
    @MaxNumber(360)
    @Desc("You can rotate the input coordinates by an angle. This can make terrain appear more natural (less sharp corners and lines). This literally rotates the entire dimension by an angle. Hint: Try 12 degrees or something not on a 90 or 45 degree angle.")
    private double dimensionAngleDeg = 0;
    @MinNumber(0)
    @MaxNumber(8192)
    @Desc("Coordinate fracturing applies noise to the input coordinates. This creates the 'iris swirls' and wavy features. The distance pushes these waves further into places they shouldnt be. This is a block value multiplier.")
    private double coordFractureDistance = 20;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("Coordinate fracturing zoom. Higher = less frequent warping, Lower = more frequent and rapid warping / swirls.")
    private double coordFractureZoom = 8;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("This zooms in the land space")
    private double landZoom = 1;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("This zooms oceanic biomes")
    private double seaZoom = 1;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("Zoom in continents")
    private double continentZoom = 1;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("Change the size of regions")
    private double regionZoom = 1;
    @Desc("Disable this to stop placing objects, entities, features & updates")
    private boolean useMantle = true;
    @Desc("Prevent Leaf decay as if placed in creative mode")
    private boolean preventLeafDecay = false;
    @ArrayType(min = 1, type = IrisShapedGeneratorStyle.class)
    @Desc("Overlay additional noise on top of the interoplated terrain.")
    private KList<IrisShapedGeneratorStyle> overlayNoise = new KList<>();
    @ArrayType(type = String.class, min = 1)
    @Desc("External datapack sources owned by this dimension. Bukkit registers their resources server-wide, but Iris excludes their structure sets and definitions from generation and locate state in vanilla worlds and Iris dimensions that do not declare the same source. Editable auto-import is limited to resources supplied by the URLs declared by each Iris pack. Any registered datapack structure can be placed directly through nativeStructures without conversion. Replacing native generation requires a dimension-level structure placement with nativeSuppression set to REPLACE_SOURCE; provenance alone never disables native structures.")
    private KList<String> datapackImports = new KList<>();
    @RegistryListFunction(ComponentFlagFunction.class)
    @ArrayType(type = String.class)
    @Desc("Mantle passes to skip in this dimension: CARVED (caves), OBJECT (objects), FLOATING_OBJECT (objects on floating islands) or JIGSAW (structures), or the id of a pack module's mantle stage, such as example:moss/veins. A name that matches nothing is warned about and ignored.")
    private KList<String> disabledComponents = new KList<>();

    public int getMaxHeight() {
        return (int) getDimensionHeight().getMax();
    }

    public int getMinHeight() {
        return (int) getDimensionHeight().getMin();
    }

    public int getFluidHeight() {
        return fluidHeight - (int) dimensionHeight.getMin();
    }

    public CNG getCoordFracture(RNG rng, int signature) {
        return coordFracture.aquire(() ->
        {
            CNG coordFracture = CNG.signature(rng.nextParallelRNG(signature));
            coordFracture.scale(0.012 / coordFractureZoom);
            return coordFracture;
        });
    }

    public double getDimensionAngle() {
        return rad.aquire(() -> Math.toRadians(dimensionAngleDeg));
    }

    public boolean hasFocusRegion() {
        return !focusRegion.equals("");
    }

    public String getFocusRegion() {
        return focusRegion;
    }

    public double sinRotate() {
        return sinr.aquire(() -> Math.sin(getDimensionAngle()));
    }

    public double cosRotate() {
        return cosr.aquire(() -> Math.cos(getDimensionAngle()));
    }

    public KList<IrisRegion> getAllRegions(DataProvider g) {
        KMap<String, IrisRegion> regions = new KMap<>();

        if (g == null) {
            return regions.v();
        }
        IrisData data = g.getData();
        if (data == null || data.getRegionLoader() == null) {
            return regions.v();
        }

        for (String key : getRegions()) {
            IrisRegion region = data.getRegionLoader().load(key);
            if (region != null) {
                regions.put(key, region);
            }
        }
        for (IrisImageMapBinding binding : getImageMaps()) {
            if (binding == null || binding.getApplication() != IrisImageMapApplication.REGION) {
                continue;
            }
            IrisImageMap map = data.getImageMapLoader().load(binding.getMap());
            if (map == null || map.getColors() == null) {
                continue;
            }
            for (String target : imageMapTargets(map)) {
                String key = localImageMapTarget(target);
                IrisRegion region = data.getRegionLoader().load(key);
                if (region != null) {
                    regions.put(key, region);
                }
            }
        }
        return regions.v();
    }

    public KList<IrisRegion> getAllAnyRegions() {
        KList<IrisRegion> r = new KList<>();

        for (String i : getRegions()) {
            r.add(IrisData.loadAnyRegion(i, getLoader()));
        }

        return r;
    }

    public KList<IrisBiome> getAllBiomes(DataProvider g) {
        if (g == null) {
            return new KList<>();
        }
        IrisData data = g.getData();
        if (data == null || data.getBiomeLoader() == null) {
            return new KList<>();
        }
        return data.getBiomeLoader().loadAll(data.getBiomeLoader().getPossibleKeys());
    }

    public KList<IrisBiome> getReachableBiomes(DataProvider g) {
        KMap<String, IrisBiome> biomes = new KMap<>();
        if (g == null) {
            return biomes.v();
        }

        IrisData data = g.getData();
        if (data == null || data.getRegionLoader() == null || data.getBiomeLoader() == null) {
            return biomes.v();
        }

        Deque<String> pending = new ArrayDeque<>();
        for (IrisRegion region : getAllRegions(g)) {
            if (region == null) {
                continue;
            }
            addReachableBiomeKeys(pending, region.getAllBiomeIds());
        }
        for (IrisImageMapBinding binding : getImageMaps()) {
            if (binding == null || binding.getApplication() != IrisImageMapApplication.BIOME) {
                continue;
            }
            IrisImageMap map = data.getImageMapLoader().load(binding.getMap());
            if (map == null) {
                continue;
            }
            for (String target : imageMapTargets(map)) {
                addReachableBiomeKey(pending, localImageMapTarget(target));
            }
        }

        addReachableBiomeKeys(pending, getModuleSettings().usedBiomes(getModuleSettings()));

        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String biomeKey = pending.removeFirst();
            if (!visited.add(biomeKey)) {
                continue;
            }

            IrisBiome biome = data.getBiomeLoader().load(biomeKey);
            if (biome == null) {
                continue;
            }

            String loadKey = biome.getLoadKey();
            if (loadKey == null || loadKey.isBlank() || biomes.containsKey(loadKey)) {
                continue;
            }

            visited.add(loadKey);
            biomes.put(loadKey, biome);
            addReachableBiomeKeys(pending, biome.getChildren());
            addReachableBiomeKeys(pending, biome.getModuleSettings().treeBiomes());
            addReachableBiomeKeys(pending, biome.getModuleSettings().usedBiomes(getModuleSettings()));
        }

        return biomes.v();
    }

    private Set<String> imageMapTargets(IrisImageMap map) {
        Set<String> targets = new LinkedHashSet<>();
        if (map.getColors() != null) {
            for (String target : map.getColors().values()) {
                if (target != null && !target.isBlank()) {
                    targets.add(target);
                }
            }
        }
        if (map.getFallbackTarget() != null && !map.getFallbackTarget().isBlank()) {
            targets.add(map.getFallbackTarget());
        }
        return targets;
    }

    private String localImageMapTarget(String target) {
        return target.startsWith("iris:") ? target.substring("iris:".length()) : target;
    }

    private void addReachableBiomeKeys(Deque<String> pending, Iterable<String> biomeKeys) {
        if (biomeKeys == null) {
            return;
        }
        for (String biomeKey : biomeKeys) {
            addReachableBiomeKey(pending, biomeKey);
        }
    }

    private void addReachableBiomeKey(Deque<String> pending, String biomeKey) {
        if (biomeKey != null && !biomeKey.isBlank()) {
            pending.addLast(biomeKey);
        }
    }

    public KList<IrisBiome> getAllAnyBiomes() {
        KList<IrisBiome> r = new KList<>();

        for (IrisRegion i : getAllAnyRegions()) {
            if (i == null) {
                continue;
            }

            r.addAll(i.getAllAnyBiomes());
        }

        return r;
    }

    public IrisGeneratorStyle getBiomeStyle(InferredType type) {
        switch (type) {
            case CAVE:
                return caveBiomeStyle;
            case LAND:
                return landBiomeStyle;
            case SEA:
                return seaBiomeStyle;
            case SHORE:
                return shoreBiomeStyle;
            default:
                break;
        }

        return landBiomeStyle;
    }

    public void installBiomes(IDataFixer fixer, DataProvider data, KList<File> datapackRoots, KSet<String> biomes) throws IOException {
        String namespace = getLoadKey().toLowerCase(Locale.ROOT);
        installBiomes(fixer, data, datapackRoots, namespace, "", biomes);
    }

    public void installBiomes(IDataFixer fixer, DataProvider data, KList<File> datapackRoots,
                              String namespace, String pathPrefix, KSet<String> biomes) throws IOException {
        // Tag membership is accumulated in memory and flushed once per tag at the end of the walk. Writing a
        // tag file per (biome, tag) pair made staging quadratic: every write re-read, re-parsed and re-emitted
        // a file that grows with every biome added to that tag.
        KMap<String, KSet<String>> tagMembership = new KMap<>();

        for (IrisBiome irisBiome : getAllBiomes(data)) {
            if (!irisBiome.isCustom()) {
                continue;
            }

            // Tag membership is inherited from the biome's vanilla derivative so that #minecraft:is_overworld
            // style selectors (vanilla's own, and every mod that writes them) hit Iris custom biomes. The
            // features and carvers arrays in the emitted biome JSON stay empty: native feature passthrough
            // comes from the chunk generator's generation-settings getter, not from the datapack.
            String derivativeKey = irisBiome.getVanillaDerivativeKey();

            for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                String customBiomeId = customBiome.getId();
                String json = customBiome.generateJson(fixer);

                synchronized (biomes) {
                    if (!biomes.add(customBiomeId)) {
                        IrisLogging.debug("Duplicate Data Pack Biome: " + getLoadKey() + "/" + customBiomeId);
                        continue;
                    }
                }

                String biomePath = pathPrefix.isBlank()
                        ? customBiomeId
                        : pathPrefix + "/" + customBiomeId;
                for (File datapackRoot : datapackRoots) {
                    File output = new File(datapackRoot, "data/" + namespace + "/worldgen/biome/" + biomePath + ".json");

                    IrisLogging.debug("    Installing Data Pack Biome: " + output.getPath());
                    output.getParentFile().mkdirs();
                    IO.writeAll(output, json);
                }
                collectBiomeTags(tagMembership, namespace + ":" + biomePath,
                        customBiome.getEffectiveTags(derivativeKey));
            }
        }

        for (File datapackRoot : datapackRoots) {
            installBiomeTags(datapackRoot, tagMembership);
        }
    }

    public static void clearGeneratedBiomeTags(KList<File> datapackRoots) {
        for (File datapackRoot : datapackRoots) {
            File dataFolder = new File(datapackRoot, "data");
            File[] namespaces = dataFolder.listFiles(File::isDirectory);
            if (namespaces == null) {
                continue;
            }
            for (File namespace : namespaces) {
                IO.delete(new File(namespace, "tags/worldgen/biome"));
            }
        }
    }

    /**
     * Accumulates one biome's tag membership into the tag-to-biomes map. Normalization and rejection happen
     * here so a malformed author tag is reported once, against the biome that declared it.
     */
    static void collectBiomeTags(KMap<String, KSet<String>> tagMembership, String biomeKey, KList<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String rawTag : tags) {
            String tag = normalizeResourceKey(rawTag);
            if (tag == null) {
                IrisLogging.error("Invalid custom biome tag '" + rawTag + "' for " + biomeKey);
                continue;
            }
            tagMembership.computeIfAbsent(tag, ignored -> new KSet<>()).add(biomeKey);
        }
    }

    /**
     * Writes every accumulated tag exactly once into one datapack root, merging with whatever a previous
     * dimension or pack already wrote to the same file.
     */
    static void installBiomeTags(File datapackRoot, KMap<String, KSet<String>> tagMembership) throws IOException {
        if (tagMembership == null || tagMembership.isEmpty()) {
            return;
        }
        for (Map.Entry<String, KSet<String>> entry : tagMembership.entrySet()) {
            String tag = entry.getKey();
            int separator = tag.indexOf(':');
            String namespace = tag.substring(0, separator);
            String path = tag.substring(separator + 1);
            Path tagRoot = new File(datapackRoot, "data/" + namespace + "/tags/worldgen/biome").toPath()
                    .toAbsolutePath().normalize();
            Path output = tagRoot.resolve(path + ".json").normalize();
            if (!output.startsWith(tagRoot)) {
                IrisLogging.error("Unsafe custom biome tag '" + tag + "' for " + entry.getValue());
                continue;
            }
            writeBiomeTag(output, entry.getValue());
        }
    }

    private static String normalizeResourceKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') < 0) {
            normalized = "minecraft:" + normalized;
        }
        return RESOURCE_KEY_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    static void writeBiomeTag(Path output, Set<String> biomeKeys) throws IOException {
        if (biomeKeys == null || biomeKeys.isEmpty()) {
            return;
        }
        synchronized (IrisDimension.class) {
            Set<String> values = new TreeSet<>();
            if (Files.isRegularFile(output)) {
                JSONObject existing = new JSONObject(Files.readString(output, StandardCharsets.UTF_8));
                JSONArray existingValues = existing.optJSONArray("values");
                if (existingValues != null) {
                    for (int index = 0; index < existingValues.length(); index++) {
                        String value = existingValues.optString(index, null);
                        if (value != null && !value.isBlank()) {
                            values.add(value);
                        }
                    }
                }
            }
            values.addAll(biomeKeys);

            JSONArray outputValues = new JSONArray();
            for (String value : values) {
                outputValues.put(value);
            }
            JSONObject tag = new JSONObject();
            tag.put("replace", false);
            tag.put("values", outputValues);
            writeAtomic(output, tag.toString(4));
        }
    }

    private static void writeAtomic(Path output, String content) throws IOException {
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), output.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Dimension getBaseDimension() {
        return switch (environment) {
            case NETHER -> Dimension.NETHER;
            case THE_END -> Dimension.END;
            default -> Dimension.OVERWORLD;
        };
    }

    public String getDimensionTypeKey() {
        return sanitizeDimensionTypeKeyValue(getLoadKey());
    }

    public static String sanitizeDimensionTypeKeyValue(String value) {
        if (value == null || value.isBlank()) {
            return "dimension";
        }

        String sanitized = value.trim().toLowerCase(Locale.ROOT).replace("\\", "/");
        sanitized = sanitized.replaceAll("[^a-z0-9_\\-./]", "_");
        sanitized = sanitized.replaceAll("/+", "/");
        sanitized = sanitized.replaceAll("^/+", "");
        sanitized = sanitized.replaceAll("/+$", "");
        if (sanitized.contains("..")) {
            sanitized = sanitized.replace("..", "_");
        }

        sanitized = sanitized.replace("/", "_");
        return sanitized.isBlank() ? "dimension" : sanitized;
    }

    public IrisDimensionType getDimensionType() {
        IrisDimensionTypeOptions options = getDimensionOptions();
        if (fullbright) {
            options = options.copy().ambientLight(1.0f);
        }
        return new IrisDimensionType(getBaseDimension(), options, getLogicalHeight(), getMaxHeight() - getMinHeight(), getMinHeight());
    }

    public boolean hasUpperDimension() {
        return upperDimension != null && !upperDimension.isEmpty() && !upperDimension.equalsIgnoreCase("none");
    }

    public void installDimensionType(IDataFixer fixer, KList<File> datapackRoots) throws IOException {
        IrisDimensionType type = getDimensionType();
        String json = type.toJson(fixer);
        String dimensionTypeKey = getDimensionTypeKey();

        IrisLogging.debug("    Installing Data Pack Dimension Type: \"iris:" + dimensionTypeKey + '"');
        for (File datapackRoot : datapackRoots) {
            File output = new File(datapackRoot, "data/iris/dimension_type/" + dimensionTypeKey + ".json");
            output.getParentFile().mkdirs();
            IO.writeAll(output, json);
        }
    }

    @Override
    public String getFolderName() {
        return "dimensions";
    }

    @Override
    public String getTypeName() {
        return "Dimension";
    }

    public static void writeShared(
            KList<File> datapackRoots,
            DimensionHeight height,
            boolean adjustVanillaHeight
    ) throws IOException {
        IrisLogging.debug("    Installing Data Pack Vanilla Dimension Types");
        String[] jsonStrings = height.jsonStrings();
        for (File datapackRoot : datapackRoots) {
            write(datapackRoot, "overworld", jsonStrings[0], adjustVanillaHeight);
            write(datapackRoot, "the_nether", jsonStrings[1], adjustVanillaHeight);
            write(datapackRoot, "the_end", jsonStrings[2], adjustVanillaHeight);
        }

        // Ranged formats spanning every supported runtime (26.1.2 = 101, 26.2 = 107). The bootstrap
        // provisioner stages this datapack before the server (and INMS) exists, so the runtime
        // version cannot be resolved there; a range keeps the emitted bytes identical on both
        // install paths and both Paper versions parse ranged min_format/max_format.
        int minFormat = DataVersion.minSupportedPackFormat();
        int maxFormat = DataVersion.getLatest().getPackFormat();
        String raw = """
                        {
                            "pack": {
                                "description": "Iris Data Pack. This pack contains all installed Iris Packs' resources.",
                                "pack_format": %d,
                                "min_format": %d,
                                "max_format": %d
                            }
                        }
                        """.formatted(maxFormat, minFormat, maxFormat);

        for (File datapackRoot : datapackRoots) {
            File mcm = new File(datapackRoot, "pack.mcmeta");
            IO.writeAll(mcm, raw);
            IrisLogging.debug("    Installing Data Pack MCMeta: " + mcm.getPath());
        }
    }

    private static void write(File datapackRoot, String type, String json, boolean adjustVanillaHeight) throws IOException {
        if (json == null) {
            return;
        }
        File dimTypeVanilla = new File(datapackRoot, "data/minecraft/dimension_type/" + type + ".json");

        if (adjustVanillaHeight || dimTypeVanilla.exists()) {
            dimTypeVanilla.getParentFile().mkdirs();
            IO.writeAll(dimTypeVanilla, json);
        }
    }
}
