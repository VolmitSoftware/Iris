package art.arcane.iris.pack.validation;

import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** Registry fakes for the gate tests: every block is present unless listed as missing; other registries are explicit. */
final class CompatFixtures {
    private CompatFixtures() {
    }

    static FakeRegistries registries(String... missingBlocks) {
        FakeRegistries registries = new FakeRegistries();
        registries.missingBlocks.addAll(List.of(missingBlocks));
        return registries;
    }

    static FakePlatform bind(FakeRegistries registries) {
        IrisPlatforms.unbind();
        FakePlatform platform = new FakePlatform(registries);
        IrisPlatforms.bind(platform);
        IrisServices.register(PreservationRegistry.class, new NoPreservation());
        return platform;
    }

    static void unbind() {
        IrisPlatforms.unbind();
        IrisServices.remove(PreservationRegistry.class);
    }

    /** Loaders register their caches for shutdown; nothing to preserve in a test JVM. */
    private static final class NoPreservation implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }

    static final class FakeRegistries implements PlatformRegistries {
        final Set<String> missingBlocks = new HashSet<>();
        final List<String> blockKeys = new ArrayList<>(List.of("minecraft:stone", "minecraft:air", "minecraft:sand"));
        final Set<String> items = new HashSet<>(List.of("minecraft:diamond", "minecraft:stone"));
        final Set<String> entities = new HashSet<>(List.of("minecraft:cow", "minecraft:zombie"));
        final Set<String> biomes = new HashSet<>(List.of("minecraft:plains", "minecraft:the_void", "minecraft:desert"));
        final Set<String> structures = new HashSet<>(List.of("minecraft:village_plains"));
        final Set<String> enchantments = new HashSet<>(List.of("minecraft:sharpness"));
        final Set<String> potionEffects = new HashSet<>(List.of("minecraft:speed", "minecraft:strength"));
        int blockLookups = 0;

        @Override
        public PlatformBlockState block(String key) {
            PlatformBlockState state = blockOrNull(key, false);
            return state == null ? air() : state;
        }

        @Override
        public PlatformBlockState blockOrNull(String key) {
            return blockOrNull(key, false);
        }

        @Override
        public PlatformBlockState blockOrNull(String key, boolean warn) {
            blockLookups++;
            String normalized = key.trim();
            int props = normalized.indexOf('[');
            String base = (props < 0 ? normalized : normalized.substring(0, props)).toLowerCase(Locale.ROOT);
            String properties = props < 0 ? "" : normalized.substring(props);
            if (base.indexOf(':') < 0) {
                base = "minecraft:" + base;
            }
            if (missingBlocks.contains(base)) {
                return null;
            }
            return new FakeBlockState(base + (base.startsWith("minecraft:") ? properties.toLowerCase(Locale.ROOT) : properties));
        }

        @Override
        public PlatformBlockState air() {
            return new FakeBlockState("minecraft:air");
        }

        @Override
        public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
            return ore;
        }

        @Override
        public PlatformBiome biome(String key) {
            return null;
        }

        @Override
        public PlatformItem item(String key) {
            return null;
        }

        @Override
        public PlatformEntityType entity(String key) {
            return null;
        }

        @Override
        public List<String> blockKeys() {
            return blockKeys;
        }

        @Override
        public List<String> biomeKeys() {
            return new ArrayList<>(biomes);
        }

        @Override
        public List<String> structureKeys() {
            return new ArrayList<>(structures);
        }

        @Override
        public List<String> itemKeys() {
            return new ArrayList<>(items);
        }

        @Override
        public List<String> entityKeys() {
            return new ArrayList<>(entities);
        }

        @Override
        public List<String> blockTypeKeys() {
            return blockKeys;
        }

        @Override
        public List<String> enchantmentKeys() {
            return new ArrayList<>(enchantments);
        }

        @Override
        public List<String> potionEffectKeys() {
            return new ArrayList<>(potionEffects);
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

    record FakeBlockState(String key) implements PlatformBlockState {
        private static final Map<String, Object> HANDLES = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String namespace() {
            return key.substring(0, key.indexOf(':'));
        }

        @Override
        public boolean isAir() {
            return key.equals("minecraft:air");
        }

        @Override
        public boolean isSolid() {
            return !isAir();
        }

        @Override
        public boolean isOccluding() {
            return false;
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public boolean isWater() {
            return false;
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
        public boolean isTreeBlock() {
            return false;
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
            return state != null && key.equals(state.key());
        }

        @Override
        public boolean hasTileEntity() {
            return false;
        }

        @Override
        public PlatformBlockState withProperty(String name, String value) {
            return this;
        }

        /** One mocked {@code BlockData} per key: the Bukkit rotation path casts the handle before it inspects it. */
        @Override
        public Object nativeHandle() {
            return HANDLES.computeIfAbsent(key, ignored -> org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class));
        }
    }

    static final class FakePlatform implements IrisPlatform {
        private final PlatformRegistries registries;
        private final File dataFolder;

        FakePlatform(PlatformRegistries registries) {
            this.registries = registries;
            File folder;
            try {
                folder = java.nio.file.Files.createTempDirectory("iris-compat-platform").toFile();
            } catch (java.io.IOException e) {
                folder = new File(System.getProperty("java.io.tmpdir"), "iris-compat-platform");
            }
            this.dataFolder = folder;
        }

        @Override
        public String platformName() {
            return "fake";
        }

        @Override
        public String minecraftVersion() {
            return "26.1.2";
        }

        @Override
        public PlatformRegistries registries() {
            return registries;
        }

        @Override
        public PlatformScheduler scheduler() {
            return null;
        }

        @Override
        public PlatformStructureHooks structureHooks() {
            return null;
        }

        @Override
        public PlatformBiomeWriter biomeWriter() {
            return null;
        }

        @Override
        public File dataFolder() {
            return dataFolder;
        }

        @Override
        public File dataFile(String... path) {
            return new File(dataFolder, String.join(File.separator, path));
        }

        @Override
        public File pluginJar() {
            return new File(".");
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
            if (level == LogLevel.WARN || level == LogLevel.ERROR) {
                System.err.println("[compat-test " + level + "] " + message);
            }
        }

        @Override
        public void msg(String message) {
        }

        @Override
        public void reportError(Throwable error) {
            error.printStackTrace(System.err);
        }
    }
}
