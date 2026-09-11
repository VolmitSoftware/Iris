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

package art.arcane.iris.probe;

import art.arcane.iris.engine.decorator.DecoratorPlatformHooks;
import art.arcane.iris.engine.object.BlockDataMergeSupport;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformItem;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.util.common.math.IrisBlockVector;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class StubPlatform implements IrisPlatform {
    private static volatile boolean VERBOSE = false;
    private static volatile Consumer<Throwable> ERROR_SINK = null;

    public static void verbose(boolean verbose) {
        VERBOSE = verbose;
    }

    public static void errorSink(Consumer<Throwable> sink) {
        ERROR_SINK = sink;
    }

    public static void bindGenerationStateHandlers() {
        DecoratorPlatformHooks.bind((state, hunk, rX, rZ, x, y, z, mantle) -> state,
                (surface, upward) -> surface.isOccluding());
        IrisObjectRotation.bindPlatformRotator(StubPlatform::rotateState);
        BlockDataMergeSupport.bindPlatformMerger(StubPlatform::mergeStates);
        TileData.bindPlatformReader(StubTileData::read);
        TileData.bindPlatformFactory(StubTileData::fromProperties);
    }

    static PlatformBlockState rotateForTest(IrisObjectRotation rotation, PlatformBlockState state) {
        return rotateState(rotation, state, 0, 0, 0);
    }

    static PlatformBlockState mergeForTest(PlatformBlockState base, PlatformBlockState update) {
        return mergeStates(base, update);
    }

    static PlatformBlockState blockStateForTest(String key) {
        return StubBlockState.of(key);
    }

    private final StubRegistries registries = new StubRegistries();
    private final StubScheduler scheduler = new StubScheduler();
    private final StubStructureHooks structureHooks = new StubStructureHooks();
    private final StubBiomeWriter biomeWriter = new StubBiomeWriter();
    private final File dataFolder;

    public StubPlatform() {
        this(new File(System.getProperty("java.io.tmpdir"), "iris-probe"));
    }

    public StubPlatform(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    private static final class StubBlockState implements PlatformBlockState {
        private static final ConcurrentHashMap<String, StubBlockState> CACHE = new ConcurrentHashMap<>();
        private final String key;
        private final String blockKey;
        private final LinkedHashMap<String, String> properties;

        private StubBlockState(String key, ParsedState parsed) {
            this.key = key;
            this.blockKey = parsed.blockKey();
            this.properties = new LinkedHashMap<>(parsed.properties());
        }

        static StubBlockState of(String key) {
            StubBlockState cached = key == null ? null : CACHE.get(key);
            return cached == null ? of(ParsedState.parse(key)) : cached;
        }

        static StubBlockState of(ParsedState parsed) {
            String key = parsed.serialize();
            return CACHE.computeIfAbsent(key, ignored -> new StubBlockState(key, parsed));
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String namespace() {
            int colon = key.indexOf(':');
            return colon >= 0 ? key.substring(0, colon) : "minecraft";
        }

        @Override
        public boolean isAir() {
            return blockKey().endsWith("air");
        }

        @Override
        public boolean isSolid() {
            return !isAir() && !isFluid();
        }

        @Override
        public boolean isOccluding() {
            return isSolid();
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public boolean isFluid() {
            String blockKey = blockKey();
            return blockKey.equals("minecraft:water")
                    || blockKey.equals("minecraft:lava")
                    || blockKey.equals("minecraft:bubble_column");
        }

        @Override
        public boolean isWater() {
            String blockKey = blockKey();
            return blockKey.equals("minecraft:water") || blockKey.equals("minecraft:bubble_column");
        }

        @Override
        public boolean isWaterLogged() {
            return "true".equals(properties.get("waterlogged"));
        }

        @Override
        public boolean isLit() {
            return false;
        }

        @Override
        public boolean isUpdatable() {
            return false;
        }

        @Override
        public boolean isFoliage() {
            return false;
        }

        @Override
        public boolean isTreeBlock() {
            String blockKey = blockKey();
            return blockKey.endsWith("_log")
                    || blockKey.endsWith("_wood")
                    || blockKey.endsWith("_stem")
                    || blockKey.endsWith("_hyphae")
                    || blockKey.endsWith("_leaves");
        }

        @Override
        public boolean isFoliagePlantable() {
            return false;
        }

        @Override
        public boolean isDecorant() {
            return false;
        }

        @Override
        public boolean isStorage() {
            return false;
        }

        @Override
        public boolean isStorageChest() {
            return false;
        }

        @Override
        public boolean isOre() {
            return false;
        }

        @Override
        public boolean isDeepSlate() {
            return false;
        }

        @Override
        public boolean isVineBlock() {
            return false;
        }

        @Override
        public boolean canPlaceOnto(PlatformBlockState onto) {
            if (!blockKey.equals("minecraft:sugar_cane")) {
                return true;
            }
            if (onto == null) {
                return false;
            }
            return switch (ParsedState.parse(onto.key()).blockKey()) {
                case "minecraft:sugar_cane", "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
                     "minecraft:podzol", "minecraft:mycelium", "minecraft:rooted_dirt", "minecraft:moss_block",
                     "minecraft:pale_moss_block", "minecraft:mud", "minecraft:muddy_mangrove_roots",
                     "minecraft:sand", "minecraft:red_sand", "minecraft:suspicious_sand" -> true;
                default -> false;
            };
        }

        @Override
        public boolean matches(PlatformBlockState state) {
            return equals(state);
        }

        @Override
        public boolean hasTileEntity() {
            return false;
        }

        @Override
        public PlatformBlockState withProperty(String name, String value) {
            String propertyName = normalizeProperty(name);
            String propertyValue = normalizeProperty(value);
            if (propertyValue.equals(properties.get(propertyName))) {
                return this;
            }
            ParsedState parsed = parsed();
            parsed.properties().put(propertyName, propertyValue);
            return of(parsed);
        }

        @Override
        public Object nativeHandle() {
            return key;
        }

        private String blockKey() {
            return blockKey;
        }

        private ParsedState parsed() {
            return new ParsedState(blockKey, new LinkedHashMap<>(properties));
        }
    }

    private static PlatformBlockState rotateState(IrisObjectRotation rotation, PlatformBlockState state,
                                                   int spinX, int spinY, int spinZ) {
        if (state == null || rotation == null || !rotation.canRotate()) {
            return state;
        }
        ParsedState parsed = parsedState(state);
        Map<String, String> properties = parsed.properties();
        if (properties.containsKey("facing")) {
            String facing = properties.get("facing");
            String rotated = rotateFace(rotation, facing, spinX, spinY, spinZ);
            if (facing.equals(rotated)) {
                return state;
            }
            properties.put("facing", rotated);
        } else if (properties.containsKey("rotation")) {
            String segment = properties.get("rotation");
            String rotated = rotateSegment(rotation, segment, spinX, spinY, spinZ);
            if (segment.equals(rotated)) {
                return state;
            }
            properties.put("rotation", rotated);
        } else if (properties.containsKey("axis")) {
            String axis = properties.get("axis");
            String rotated = rotateAxis(rotation, axis, spinX, spinY, spinZ);
            if (axis.equals(rotated)) {
                return state;
            }
            properties.put("axis", rotated);
        } else if (!rotateFaceProperties(rotation, properties, spinX, spinY, spinZ)) {
            return state;
        }
        return StubBlockState.of(parsed);
    }

    private static PlatformBlockState mergeStates(PlatformBlockState base, PlatformBlockState update) {
        if (base == null) {
            return update;
        }
        if (update == null) {
            return base;
        }
        ParsedState parsedBase = parsedState(base);
        ParsedState parsedUpdate = parsedState(update);
        if (!parsedBase.blockKey().equals(parsedUpdate.blockKey())) {
            return update;
        }
        if (parsedUpdate.properties().isEmpty()) {
            return base;
        }
        LinkedHashMap<String, String> original = new LinkedHashMap<>(parsedBase.properties());
        parsedBase.properties().putAll(parsedUpdate.properties());
        return original.equals(parsedBase.properties()) ? base : StubBlockState.of(parsedBase);
    }

    private static ParsedState parsedState(PlatformBlockState state) {
        return state instanceof StubBlockState stub ? stub.parsed() : ParsedState.parse(state.key());
    }

    private static String rotateFace(IrisObjectRotation rotation, String face,
                                     int spinX, int spinY, int spinZ) {
        IrisBlockVector vector = faceVector(face);
        if (vector == null) {
            return face;
        }
        return faceName(rotation.rotate(vector, spinX, spinY, spinZ));
    }

    private static String rotateAxis(IrisObjectRotation rotation, String axis,
                                     int spinX, int spinY, int spinZ) {
        IrisBlockVector vector = switch (axis) {
            case "x" -> new IrisBlockVector(1, 0, 0);
            case "y" -> new IrisBlockVector(0, 1, 0);
            case "z" -> new IrisBlockVector(0, 0, 1);
            default -> null;
        };
        if (vector == null) {
            return axis;
        }
        IrisBlockVector rotated = rotation.rotate(vector, spinX, spinY, spinZ);
        double x = Math.abs(rotated.getX());
        double y = Math.abs(rotated.getY());
        double z = Math.abs(rotated.getZ());
        if (x >= y && x >= z) {
            return "x";
        }
        return y >= z ? "y" : "z";
    }

    private static String rotateSegment(IrisObjectRotation rotation, String value,
                                        int spinX, int spinY, int spinZ) {
        int segment;
        try {
            segment = Math.floorMod(Integer.parseInt(value), 16);
        } catch (NumberFormatException e) {
            return value;
        }
        double angle = segment * Math.PI * 2D / 16D;
        IrisBlockVector vector = new IrisBlockVector(-Math.sin(angle), 0D, Math.cos(angle));
        IrisBlockVector rotated = rotation.rotate(vector, spinX, spinY, spinZ);
        if (Math.abs(rotated.getY()) > Math.max(Math.abs(rotated.getX()), Math.abs(rotated.getZ()))) {
            return value;
        }
        double rotatedAngle = Math.atan2(-rotated.getX(), rotated.getZ());
        int rotatedSegment = (int) Math.round(rotatedAngle * 16D / (Math.PI * 2D));
        return Integer.toString(Math.floorMod(rotatedSegment, 16));
    }

    private static boolean rotateFaceProperties(IrisObjectRotation rotation, Map<String, String> properties,
                                                int spinX, int spinY, int spinZ) {
        List<String> faces = List.of("north", "east", "south", "west", "up", "down");
        int present = 0;
        for (String face : faces) {
            if (properties.containsKey(face)) {
                present++;
            }
        }
        if (present < 2) {
            return false;
        }
        Map<String, String> rotated = new LinkedHashMap<>();
        for (String face : faces) {
            String value = properties.get(face);
            if (value != null) {
                rotated.put(rotateFace(rotation, face, spinX, spinY, spinZ), value);
            }
        }
        boolean changed = false;
        for (String face : faces) {
            if (properties.containsKey(face)) {
                String defaultValue = "true".equals(properties.get(face)) || "false".equals(properties.get(face))
                        ? "false" : "none";
                String value = rotated.getOrDefault(face, defaultValue);
                changed |= !Objects.equals(properties.put(face, value), value);
            }
        }
        return changed;
    }

    private static IrisBlockVector faceVector(String face) {
        return switch (face) {
            case "north" -> new IrisBlockVector(0, 0, -1);
            case "east" -> new IrisBlockVector(1, 0, 0);
            case "south" -> new IrisBlockVector(0, 0, 1);
            case "west" -> new IrisBlockVector(-1, 0, 0);
            case "up" -> new IrisBlockVector(0, 1, 0);
            case "down" -> new IrisBlockVector(0, -1, 0);
            default -> null;
        };
    }

    private static String faceName(IrisBlockVector vector) {
        double x = Math.abs(vector.getX());
        double y = Math.abs(vector.getY());
        double z = Math.abs(vector.getZ());
        if (x >= y && x >= z) {
            return vector.getX() >= 0D ? "east" : "west";
        }
        if (y >= z) {
            return vector.getY() >= 0D ? "up" : "down";
        }
        return vector.getZ() >= 0D ? "south" : "north";
    }

    private static String normalizeProperty(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ParsedState(String blockKey, LinkedHashMap<String, String> properties) {
        private static ParsedState parse(String key) {
            String normalized = key == null ? "minecraft:air" : key.trim().toLowerCase(Locale.ROOT);
            int open = normalized.indexOf('[');
            if (open < 0 || !normalized.endsWith("]")) {
                return new ParsedState(normalized, new LinkedHashMap<>());
            }
            LinkedHashMap<String, String> properties = new LinkedHashMap<>();
            int end = normalized.length() - 1;
            int start = open + 1;
            while (start < end) {
                int comma = normalized.indexOf(',', start);
                int propertyEnd = comma < 0 || comma > end ? end : comma;
                int separator = normalized.indexOf('=', start);
                if (separator > start && separator < propertyEnd - 1) {
                    properties.put(
                            normalized.substring(start, separator),
                            normalized.substring(separator + 1, propertyEnd)
                    );
                }
                start = propertyEnd + 1;
            }
            return new ParsedState(normalized.substring(0, open), properties);
        }

        private String serialize() {
            if (properties.isEmpty()) {
                return blockKey;
            }
            StringBuilder serialized = new StringBuilder(blockKey).append('[');
            boolean first = true;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!first) {
                    serialized.append(',');
                }
                serialized.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            return serialized.append(']').toString();
        }
    }

    private static final class StubBiome implements PlatformBiome {
        private static final ConcurrentHashMap<String, StubBiome> CACHE = new ConcurrentHashMap<>();
        private final String key;

        private StubBiome(String key) {
            this.key = key;
        }

        static StubBiome of(String key) {
            return CACHE.computeIfAbsent(key, StubBiome::new);
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String namespace() {
            int colon = key.indexOf(':');
            return colon >= 0 ? key.substring(0, colon) : "minecraft";
        }

        @Override
        public Object nativeHandle() {
            return key;
        }
    }

    private static final class StubScheduler implements PlatformScheduler {
        @Override
        public void global(Runnable task) {
            task.run();
        }

        @Override
        public void region(PlatformWorld world, int chunkX, int chunkZ, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void laterGlobal(Runnable task, int ticks) {
        }

        @Override
        public void laterRegion(PlatformWorld world, int chunkX, int chunkZ, Runnable task, int ticks) {
        }
    }

    private static final class StubStructureHooks implements PlatformStructureHooks {
        @Override
        public List<String> structureKeys() {
            return List.of();
        }

        @Override
        public List<String> structureSetKeys() {
            return List.of();
        }

        @Override
        public List<String> structureBiomeKeys(String structureKey) {
            return List.of();
        }

        @Override
        public List<String> objectFeatureKeys() {
            return List.of();
        }

        @Override
        public List<String> reachableStructureKeys(PlatformWorld world) {
            return List.of();
        }

        @Override
        public List<String> possibleBiomeKeys(PlatformWorld world) {
            return List.of();
        }

        @Override
        public boolean placeFeature(PlatformWorld world, int x, int y, int z, String featureKey, long seed) {
            return false;
        }

        @Override
        public int[] placeStructure(PlatformWorld world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan) {
            return null;
        }

        @Override
        public boolean supportsStructurePlacement() {
            return false;
        }
    }

    private static final class StubBiomeWriter implements PlatformBiomeWriter {
        @Override
        public int biomeIdFor(String key) {
            return 0;
        }

        @Override
        public List<PlatformBiome> allBiomes() {
            return List.of();
        }
    }

    private record StubEntityType(String key) implements PlatformEntityType {
        @Override
        public String namespace() {
            return "minecraft";
        }

        @Override
        public String spawnCategory() {
            return "monster";
        }

        @Override
        public Object nativeHandle() {
            return this;
        }
    }

    private static final class StubRegistries implements PlatformRegistries {
        @Override
        public PlatformBlockState block(String key) {
            return StubBlockState.of(key);
        }

        @Override
        public PlatformBlockState blockOrNull(String key) {
            return StubBlockState.of(key);
        }

        @Override
        public PlatformBlockState blockOrNull(String key, boolean warn) {
            return StubBlockState.of(key);
        }

        @Override
        public PlatformBlockState air() {
            return StubBlockState.of("minecraft:air");
        }

        @Override
        public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
            return ore;
        }

        @Override
        public PlatformBiome biome(String key) {
            return StubBiome.of(key);
        }

        @Override
        public PlatformItem item(String key) {
            return null;
        }

        @Override
        public PlatformEntityType entity(String key) {
            return key != null && key.startsWith("minecraft:") ? new StubEntityType(key) : null;
        }

        @Override
        public List<String> blockKeys() {
            return List.of();
        }

        @Override
        public List<String> biomeKeys() {
            return List.of();
        }

        @Override
        public List<String> structureKeys() {
            return List.of();
        }

        @Override
        public List<String> itemKeys() {
            return List.of();
        }

        @Override
        public List<String> entityKeys() {
            return List.of();
        }

        @Override
        public List<String> blockTypeKeys() {
            return List.of();
        }

        @Override
        public List<String> specialEntityKeys() {
            return List.of();
        }

        @Override
        public List<String> enchantmentKeys() {
            return List.of();
        }

        @Override
        public List<String> potionEffectKeys() {
            return List.of();
        }

        @Override
        public List<String> lootTableKeys() {
            return List.of();
        }

        @Override
        public Map<String, List<PlatformBlockProperty>> blockStateProperties() {
            return Map.of();
        }
    }

    @Override
    public String platformName() {
        return "probe";
    }

    @Override
    public String minecraftVersion() {
        return "probe";
    }

    @Override
    public PlatformRegistries registries() {
        return registries;
    }

    @Override
    public PlatformScheduler scheduler() {
        return scheduler;
    }

    @Override
    public PlatformStructureHooks structureHooks() {
        return structureHooks;
    }

    @Override
    public PlatformBiomeWriter biomeWriter() {
        return biomeWriter;
    }

    @Override
    public File dataFolder() {
        return dataFolder;
    }

    @Override
    public File dataFile(String... path) {
        return new File(dataFolder(), String.join(File.separator, path));
    }

    @Override
    public File pluginJar() {
        return new File(dataFolder(), "probe.jar");
    }

    @Override
    public int irisVersionNumber() {
        return 0;
    }

    @Override
    public int minecraftVersionNumber() {
        return 0;
    }

    @Override
    public void callEvent(Object event) {
    }

    @Override
    public void dispatchConsoleCommand(String command) {
    }

    @Override
    public boolean spawnEntity(PlatformWorld world, String entityKey, double x, double y, double z) {
        return false;
    }

    @Override
    public void log(LogLevel level, String message) {
        if (VERBOSE) {
            System.out.println("[stub/" + level + "] " + message);
        }
    }

    @Override
    public void msg(String message) {
        if (VERBOSE) {
            System.out.println("[stub/MSG] " + message);
        }
    }

    @Override
    public void reportError(Throwable error) {
        Consumer<Throwable> sink = ERROR_SINK;
        if (sink != null && error != null) {
            sink.accept(error);
        }
    }
}
