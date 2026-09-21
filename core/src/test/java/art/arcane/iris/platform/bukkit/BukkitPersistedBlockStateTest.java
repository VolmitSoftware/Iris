package art.arcane.iris.platform.bukkit;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.platform.registry.RegistryUtil;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class BukkitPersistedBlockStateTest {
    @BeforeClass
    public static void installServer() {
        BukkitTestServer.install();
        try (MockedStatic<RegistryUtil> registry = mockStatic(RegistryUtil.class)) {
            registry.when(() -> RegistryUtil.find(Material.class, "grass", "short_grass"))
                    .thenReturn(Material.SHORT_GRASS);
            BukkitBlockResolution.getAir();
        }
    }

    @Test
    public void decodingPreservesExplicitPropertiesDespiteGenerationLeafPolicy() {
        BukkitRegistries registries = new BukkitRegistries();
        IrisSettings configuration = new IrisSettings();
        configuration.getGenerator().setPreventLeafDecay(true);
        String key = "minecraft:oak_leaves[distance=2,persistent=false,waterlogged=true]";
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
             MockedStatic<IrisSettings> settings = mockStatic(IrisSettings.class)) {
            bukkit.when(() -> Bukkit.createBlockData(anyString()))
                    .thenAnswer(invocation -> parsed(invocation.getArgument(0)));
            settings.when(IrisSettings::get).thenReturn(configuration);

            assertEquals(key.replace("persistent=false", "persistent=true"), registries.blockOrNull(key).key());
            NativeBlockState decoded = registries.decodeBlockState(key);
            assertEquals(key, decoded.key());
            assertSame(decoded, registries.decodeBlockState(key));
            assertEquals(key, ((BlockData) decoded.nativeHandle()).getAsString());
        }
    }

    @Test
    public void bothMatterSlicesRoundTripFullNativeProperties() throws IOException {
        BukkitRegistries registries = new BukkitRegistries();
        IrisPlatform previous = IrisPlatforms.getOrNull();
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createBlockData(anyString()))
                    .thenAnswer(invocation -> parsed(invocation.getArgument(0)));
            IrisMatterSupport.ensureRegistered();
            Matter matter = new IrisMatter(16, 16, 16);
            String[] keys = {
                    "minecraft:oak_leaves[distance=3,persistent=false,waterlogged=false]",
                    "minecraft:oak_leaves[distance=6,persistent=true,waterlogged=true]",
                    "minecraft:oak_stairs[facing=east,half=top,shape=inner_left,waterlogged=true]"
            };
            for (int i = 0; i < keys.length; i++) {
                NativeBlockState block = BukkitBlockState.of(parsed(keys[i]));
                matter.<NativeBlockState>slice(NativeBlockState.class).set(i, 2, 3, block);
                matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class)
                        .set(i, 2, 3, PreObjectMatterCell.block(block));
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            matter.write(bytes);
            Matter restored = Matter.read(new ByteArrayInputStream(bytes.toByteArray()));
            for (int i = 0; i < keys.length; i++) {
                assertEquals(keys[i], restored.<NativeBlockState>getSlice(NativeBlockState.class).get(i, 2, 3).key());
                assertEquals(keys[i], restored.<PreObjectMatterCell>getSlice(PreObjectMatterCell.class)
                        .get(i, 2, 3).block().key());
            }
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    @Test
    public void decodingRetainsCustomProviderIdentityAndRegisteredStates() {
        BukkitRegistries registries = new BukkitRegistries();
        String key = "test:persisted_leaves[distance=2,persistent=false]";
        BlockData carrier = parsed("minecraft:oak_leaves[distance=2,persistent=false,waterlogged=false]");
        IrisCustomData custom = IrisCustomData.of(carrier, Identifier.fromString(key));
        ExternalDataSVC provider = mock(ExternalDataSVC.class);
        when(provider.getBlockData(Identifier.fromString(key))).thenReturn(Optional.of(custom));
        try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            services.when(() -> IrisServices.get(ExternalDataSVC.class)).thenReturn(provider);
            NativeBlockState decoded = registries.decodeBlockState(key);
            assertEquals(key, decoded.key());
            assertEquals(key, decoded.deferredPlacementKey());
            assertSame(custom, decoded.nativeHandle());
        }
        BukkitBlockResolution.registerCustomBlockData("test", "registered_persisted_leaves", carrier);
        assertSame(carrier, registries.decodeBlockState("test:registered_persisted_leaves").nativeHandle());
    }

    @Test
    public void invalidPersistedPropertiesAreNotSilentlyStripped() {
        String key = "minecraft:oak_leaves[distance=invalid,persistent=false]";
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createBlockData(key)).thenThrow(new IllegalArgumentException("invalid distance"));
            assertThrows(IllegalArgumentException.class, () -> new BukkitRegistries().decodeBlockState(key));
        }
    }

    private static BlockData parsed(String key) {
        if (!key.startsWith("minecraft:oak_leaves[")) {
            return BukkitTestServer.blockData(key);
        }
        Leaves leaves = mock(Leaves.class);
        AtomicBoolean persistent = new AtomicBoolean(key.contains("persistent=true"));
        doAnswer(invocation -> key.replaceFirst("persistent=(true|false)", "persistent=" + persistent.get()))
                .when(leaves).getAsString();
        doAnswer(invocation -> key.replaceFirst("persistent=(true|false)", "persistent=" + persistent.get()))
                .when(leaves).getAsString(anyBoolean());
        doAnswer(invocation -> {
            persistent.set(invocation.getArgument(0));
            return null;
        }).when(leaves).setPersistent(anyBoolean());
        doReturn(Material.OAK_LEAVES).when(leaves).getMaterial();
        return leaves;
    }
}
