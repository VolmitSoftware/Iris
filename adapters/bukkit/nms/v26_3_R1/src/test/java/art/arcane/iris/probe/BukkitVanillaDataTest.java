package art.arcane.iris.probe;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.VanillaDataSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.bukkit.Bukkit;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class BukkitVanillaDataTest {
    private static final String PLAINS = "data/minecraft/worldgen/biome/plains.json";
    private static final String BIOME_TAGS = "data/minecraft/tags/worldgen/biome";

    @BeforeClass
    public static void bootstrap() {
        HeadlessNativeBootstrap.initialize();
    }

    @Test
    public void bukkitPlatformReadsTheServerPlainsBiome() throws IOException {
        byte[] plains = new BukkitPlatform().vanillaData().read(PLAINS).orElseThrow();
        assertTrue(new String(plains, StandardCharsets.UTF_8).contains("\"temperature\""));
        try (PackResources vanilla = ServerPacksSource.createVanillaPackSource().fullResources()) {
            IoSupplier<InputStream> resource = vanilla.getResource(PackType.SERVER_DATA,
                    Identifier.withDefaultNamespace("worldgen/biome/plains.json"));
            try (InputStream input = resource.get()) {
                assertArrayEquals(input.readAllBytes(), plains);
            }
        }
        assertNull(Bukkit.getServer());
    }

    @Test
    public void bukkitPlatformListsTheBiomeTagsTheGameLoads() {
        List<String> expected = new ArrayList<>();
        try (PackResources vanilla = ServerPacksSource.createVanillaPackSource().fullResources()) {
            vanilla.listResources(PackType.SERVER_DATA, "minecraft", "tags/worldgen/biome",
                    (id, resource) -> expected.add("data/" + id.getNamespace() + "/" + id.getPath()));
        }
        Collections.sort(expected);
        List<String> listed = new BukkitPlatform().vanillaData().list(BIOME_TAGS);
        assertFalse(listed.isEmpty());
        assertTrue(listed.contains(BIOME_TAGS + "/is_overworld.json"));
        assertEquals(expected, listed);
    }

    @Test
    public void headlessPlatformReadsTheSameVanillaData() {
        HeadlessNativePlatform platform = new HeadlessNativePlatform(new File("unused-native-platform"),
                HeadlessNativeTestRegistries::get);
        VanillaDataSource bukkit = new BukkitPlatform().vanillaData();
        assertArrayEquals(bukkit.read(PLAINS).orElseThrow(), platform.vanillaData().read(PLAINS).orElseThrow());
        assertEquals(bukkit.list(BIOME_TAGS), platform.vanillaData().list(BIOME_TAGS));
    }
}
