package art.arcane.iris.modded;

import art.arcane.iris.platform.ClasspathVanillaDataSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModdedVanillaDataTest {
    private static final String PLAINS = "data/minecraft/worldgen/biome/plains.json";
    private static final String BIOME_TAGS = "data/minecraft/tags/worldgen/biome";

    @BeforeClass
    public static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void overworldLevelClassReadsTheGameVanillaData() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeWorld overworld = mock(NativeWorld.class);
        when(overworld.nativeHandle()).thenReturn(mock(ServerLevel.class));
        when(server.overworld()).thenReturn(overworld);
        Class<?> anchor = ModdedPlatform.overworldClass(server);
        assertSame(ServerLevel.class, anchor);
        ClasspathVanillaDataSource source = new ClasspathVanillaDataSource(() -> anchor);
        String plains = new String(source.read(PLAINS).orElseThrow(), StandardCharsets.UTF_8);
        assertTrue(plains.contains("\"temperature\""));
        assertTrue(source.list(BIOME_TAGS).contains(BIOME_TAGS + "/is_overworld.json"));
    }

    @Test
    public void vanillaDataNeedsALoadedOverworld() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        assertThrows(IllegalStateException.class, () -> ModdedPlatform.overworldClass(null));
        assertThrows(IllegalStateException.class, () -> ModdedPlatform.overworldClass(server));
    }
}
