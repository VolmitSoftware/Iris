package art.arcane.iris.probe;

import net.minecraft.world.level.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.spigotmc.SpigotConfig;
import org.spigotmc.SpigotWorldConfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HeadlessStructureConfigurationTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass
    public static void initializeNative() {
        HeadlessNativeBootstrap.initialize();
    }

    @Test
    public void nativeConstructorInitializesEveryDefaultWithoutChangingTheSourceOrGlobals() throws Exception {
        Path source = temporary.newFile("spigot.yml").toPath();
        Path staging = temporary.newFolder().toPath();
        byte[] original = Files.readAllBytes(source);
        YamlConfiguration previous = SpigotConfig.config;
        Object previousFile = nativeField("CONFIG_FILE").get(null);
        int previousVersion = nativeField("version").getInt(null);
        SpigotWorldConfig configuration = HeadlessStructureConfiguration.load(options(source, staging));
        assertEquals(SpigotWorldConfig.class, configuration.getClass());
        int checked = 0;
        for (Field field : seedFields()) {
            if (field.getType() == int.class) {
                assertTrue(field.getName(), field.getInt(configuration) > 0);
            } else {
                assertNull(field.getName(), field.get(configuration));
            }
            checked++;
        }
        assertTrue(checked >= 22);
        assertEquals(100, configuration.cactusModifier);
        assertArrayEquals(original, Files.readAllBytes(source));
        assertSame(previous, SpigotConfig.config);
        assertEquals(previousFile, nativeField("CONFIG_FILE").get(null));
        assertEquals(previousVersion, nativeField("version").getInt(null));
        assertEmpty(staging);
    }

    @Test
    public void everyNativeSeedHonorsDefaultAndNamespacedOverrides() throws Exception {
        Path source = temporary.newFile("overrides.yml").toPath();
        Path staging = temporary.newFolder().toPath();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", 13);
        int ordinal = 0;
        for (Field field : seedFields()) {
            String path = seedPath(field);
            yaml.set("world-settings.default." + path, 12000 + ordinal);
            yaml.set("world-settings.minecraft:overworld." + path,
                    field.getType() == Long.class ? 9000000000L + ordinal : 24000L + ordinal);
            ordinal++;
        }
        yaml.set("world-settings.default.verbose", true);
        yaml.set("world-settings.legacy.seed-swamp", 9);
        yaml.save(source.toFile());
        byte[] original = Files.readAllBytes(source);
        SpigotWorldConfig configured = HeadlessStructureConfiguration.load(options(source, staging));
        ordinal = 0;
        for (Field field : seedFields()) {
            assertEquals(field.getName(), field.getType() == Long.class ? 9000000000L + ordinal : 24000L + ordinal,
                    ((Number) field.get(configured)).longValue());
            ordinal++;
        }
        assertArrayEquals(original, Files.readAllBytes(source));
        yaml.set("world-settings.minecraft:overworld", null);
        yaml.save(source.toFile());
        SpigotWorldConfig defaults = HeadlessStructureConfiguration.load(options(source, staging));
        ordinal = 0;
        for (Field field : seedFields()) {
            assertEquals(field.getName(), 12000L + ordinal++, ((Number) field.get(defaults)).longValue());
        }
        assertEmpty(staging);
    }

    @Test
    public void legacyMigrationRetainsNativePrecedenceAndFailuresRestoreState() throws Exception {
        Path source = temporary.newFile("legacy.yml").toPath();
        Path staging = temporary.newFolder().toPath();
        Files.writeString(source, """
                config-version: 12
                world-settings:
                  default:
                    seed-swamp: 11
                  legacy:
                    seed-swamp: 73
                    verbose: true
                  minecraft:overworld:
                    seed-swamp: 99
                """);
        byte[] original = Files.readAllBytes(source);
        assertEquals(73, HeadlessStructureConfiguration.load(options(source, staging)).swampSeed);
        assertArrayEquals(original, Files.readAllBytes(source));
        assertEquals(99, HeadlessStructureConfiguration.load(new HeadlessStructureConfiguration.Options(
                source, "minecraft:overworld", Level.OVERWORLD, staging)).swampSeed);
        assertArrayEquals(original, Files.readAllBytes(source));
        YamlConfiguration previous = SpigotConfig.config;
        Object previousFile = nativeField("CONFIG_FILE").get(null);
        int previousVersion = nativeField("version").getInt(null);
        for (String invalid : List.of("world-settings: [broken", "world-settings:\n  default:\n    seed-stronghold: '2.5'\n")) {
            Files.writeString(source, invalid);
            IOException failure = assertThrows(IOException.class,
                    () -> HeadlessStructureConfiguration.load(options(source, staging)));
            assertNotNull(failure.getCause());
            assertEquals(invalid, Files.readString(source));
            assertSame(previous, SpigotConfig.config);
            assertEquals(previousFile, nativeField("CONFIG_FILE").get(null));
            assertEquals(previousVersion, nativeField("version").getInt(null));
            assertEmpty(staging);
        }
    }

    private static HeadlessStructureConfiguration.Options options(Path source, Path staging) {
        return new HeadlessStructureConfiguration.Options(source, "legacy", Level.OVERWORLD, staging);
    }

    private static List<Field> seedFields() {
        return Stream.of(SpigotWorldConfig.class.getFields()).filter(field -> field.getName().endsWith("Seed"))
                .sorted(Comparator.comparing(Field::getName)).toList();
    }

    private static String seedPath(Field field) {
        return "seed-" + field.getName().substring(0, field.getName().length() - 4).toLowerCase(Locale.ROOT);
    }

    private static Field nativeField(String name) throws Exception {
        Field field = SpigotConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void assertEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            assertEquals(0L, entries.count());
        }
    }
}
