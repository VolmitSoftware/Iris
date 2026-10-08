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

import art.arcane.iris.engine.object.material.BlockDataMergeSupport;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformFace;
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

        private StubBlockState(String key) {
            this.key = key;
        }

        static StubBlockState of(String key) {
            return CACHE.computeIfAbsent(key, StubBlockState::new);
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
            return false;
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
        public boolean isLog() {
            String blockKey = blockKey();
            return blockKey.endsWith("_log")
                    || blockKey.endsWith("_wood")
                    || blockKey.endsWith("_stem")
                    || blockKey.endsWith("_hyphae");
        }

        @Override
        public boolean isLeaves() {
            return blockKey().endsWith("_leaves");
        }

        @Override
        public boolean hasGravity() {
            String blockKey = blockKey();
            return blockKey.endsWith(":sand")
                    || blockKey.endsWith(":red_sand")
                    || blockKey.endsWith(":gravel")
                    || blockKey.endsWith("_concrete_powder")
                    || blockKey.endsWith("anvil");
        }

        /** The stub has no shapes: every solid block is sturdy on every face. */
        @Override
        public boolean canAttach(PlatformBlockState support, PlatformFace face) {
            return support.isSolid();
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
            return true;
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
            ParsedState parsed = ParsedState.parse(key);
            parsed.properties().put(normalizeProperty(name), normalizeProperty(value));
            return of(parsed.serialize());
        }

        @Override
        public Object nativeHandle() {
            return key;
        }

        private String blockKey() {
            int properties = key.indexOf('[');
            return properties >= 0 ? key.substring(0, properties) : key;
        }
    }

    private static PlatformBlockState rotateState(IrisObjectRotation rotation, PlatformBlockState state,
                                                   int spinX, int spinY, int spinZ) {
        if (state == null || rotation == null || !rotation.canRotate()) {
            return state;
        }
        ParsedState parsed = ParsedState.parse(state.key());
        Map<String, String> properties = parsed.properties();
        if (properties.containsKey("facing")) {
            properties.put("facing", rotateFace(rotation, properties.get("facing"), spinX, spinY, spinZ));
        } else if (properties.containsKey("rotation")) {
            properties.put("rotation", rotateSegment(rotation, properties.get("rotation"), spinX, spinY, spinZ));
        } else if (properties.containsKey("axis")) {
            properties.put("axis", rotateAxis(rotation, properties.get("axis"), spinX, spinY, spinZ));
        } else {
            rotateFaceProperties(rotation, properties, spinX, spinY, spinZ);
        }
        return StubBlockState.of(parsed.serialize());
    }

    private static PlatformBlockState mergeStates(PlatformBlockState base, PlatformBlockState update) {
        if (base == null) {
            return update;
        }
        if (update == null) {
            return base;
        }
        ParsedState parsedBase = ParsedState.parse(base.key());
        ParsedState parsedUpdate = ParsedState.parse(update.key());
        if (!parsedBase.blockKey().equals(parsedUpdate.blockKey())) {
            return update;
        }
        parsedBase.properties().putAll(parsedUpdate.properties());
        return StubBlockState.of(parsedBase.serialize());
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

    private static void rotateFaceProperties(IrisObjectRotation rotation, Map<String, String> properties,
                                             int spinX, int spinY, int spinZ) {
        List<String> faces = List.of("north", "east", "south", "west", "up", "down");
        int present = 0;
        for (String face : faces) {
            if (properties.containsKey(face)) {
                present++;
            }
        }
        if (present < 2) {
            return;
        }
        Map<String, String> rotated = new LinkedHashMap<>();
        for (String face : faces) {
            String value = properties.get(face);
            if (value != null) {
                rotated.put(rotateFace(rotation, face, spinX, spinY, spinZ), value);
            }
        }
        for (String face : faces) {
            if (properties.containsKey(face)) {
                String defaultValue = "true".equals(properties.get(face)) || "false".equals(properties.get(face))
                        ? "false" : "none";
                properties.put(face, rotated.getOrDefault(face, defaultValue));
            }
        }
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
            String body = normalized.substring(open + 1, normalized.length() - 1);
            if (!body.isBlank()) {
                for (String property : body.split(",")) {
                    int separator = property.indexOf('=');
                    if (separator > 0 && separator < property.length() - 1) {
                        properties.put(property.substring(0, separator), property.substring(separator + 1));
                    }
                }
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

        /** The stub knows the tags of the block traits it has: logs, leaves and the falling blocks. */
        @Override
        public boolean isInTag(PlatformBlockState block, String tag) {
            return switch (tag) {
                case "minecraft:logs" -> block.isLog();
                case "minecraft:leaves" -> block.isLeaves();
                case "minecraft:sand" -> block.key().endsWith(":sand") || block.key().endsWith(":red_sand");
                default -> false;
            };
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
