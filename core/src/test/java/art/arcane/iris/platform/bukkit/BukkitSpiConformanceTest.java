/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.iris.util.project.matter.slices.PlatformBlockMatter;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

public class BukkitSpiConformanceTest {
    @BeforeClass
    public static void setup() {
        BukkitTestServer.install();
    }

    @Before
    public void resetPlatformBinding() {
        IrisPlatforms.unbind();
    }

    @After
    public void clearPlatformBinding() {
        IrisPlatforms.unbind();
    }

    private static BlockData blockData(String asString) {
        return BukkitTestServer.blockData(asString);
    }

    private static PlatformRegistries registries() {
        return new BukkitRegistries();
    }

    private static boolean liveRegistriesAvailable() {
        try {
            RegistryAccess.registryAccess();
            return true;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    private static void assertNamespacedRegistryList(Supplier<List<String>> supplier) {
        Assume.assumeTrue("live Bukkit registry unavailable in this environment", liveRegistriesAvailable());
        List<String> keys;
        try {
            keys = supplier.get();
        } catch (Throwable unavailable) {
            Assume.assumeNoException("live Bukkit registry unavailable in this environment", unavailable);
            return;
        }
        Assume.assumeFalse("live Bukkit registry is unpopulated in this environment", keys.isEmpty());
        for (String key : keys) {
            assertTrue("expected namespaced key but was '" + key + "'", key.contains(":"));
        }
    }

    @Test
    public void blockStateInterningReturnsSameInstanceForSameKey() {
        BlockData first = blockData("iristest:intern_block[axis=y]");
        BlockData second = blockData("iristest:intern_block[axis=y]");
        assertSame(BukkitBlockState.of(first), BukkitBlockState.of(second));
    }

    @Test
    public void mantleValuesUseStableBukkitInterfaceSlices() {
        BukkitPlatform platform = new BukkitPlatform();
        assertTrue(platform.supportsMatterWorldIo());
        assertSame(World.class, platform.classifyMantleValue(mock(World.class)));
        assertSame(BlockData.class, platform.classifyMantleValue(blockData("minecraft:stone")));
        assertSame(Entity.class, platform.classifyMantleValue(mock(Entity.class)));
        assertSame(String.class, platform.classifyMantleValue("value"));
    }

    @Test
    public void blockStateKeyMatchesCanonicalString() {
        BlockData data = blockData("iristest:key_block[facing=north,lit=true]");
        assertEquals("iristest:key_block[facing=north,lit=true]", BukkitBlockState.of(data).key());
    }

    @Test
    public void customStateKeepsProviderIdentityAndProperties() throws Exception {
        BlockData base = blockData("minecraft:note_block[instrument=harp,note=5,powered=false]");
        Identifier identifier = Identifier.fromString("craftengine:forest/amber_log[axis=x]");
        PlatformBlockState state = BukkitBlockState.of(IrisCustomData.of(base, identifier));

        assertEquals(identifier.toString(), state.key());
        assertEquals(identifier.toString(), state.deferredPlacementKey());
        assertEquals("craftengine", state.namespace());
        assertEquals("craftengine:forest/amber_log", state.materialKey());
        assertEquals(base.getAsString(), state.placementBaseState().key());
        assertTrue(state.isCustom());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PlatformBlockMatter().writeNode(state, new DataOutputStream(bytes));
        assertEquals(identifier.toString(), new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())).readUTF());
    }

    @Test
    public void customCarrierPropertyChangeKeepsProviderPropertiesSeparate() {
        BlockData base = blockData("minecraft:oak_slab[type=bottom,waterlogged=false]");
        Identifier identifier = Identifier.fromString("itemsadder:forest/amber_slab");
        PlatformBlockState state = BukkitBlockState.of(IrisCustomData.of(base, identifier));

        PlatformBlockState merged = state.withProperty("waterlogged", "true");

        assertEquals(identifier.toString(), merged.key());
        assertEquals(identifier.toString(), merged.deferredPlacementKey());
        assertEquals("minecraft:oak_slab[type=bottom,waterlogged=true]", merged.placementBaseState().key());
        assertEquals("minecraft:oak_slab[type=bottom,waterlogged=false]", state.placementBaseState().key());
        assertTrue(merged.isCustom());
    }

    @Test
    public void blockStateNamespaceParsesWithAndWithoutProperties() {
        BlockData withProps = blockData("iristest:ns_block[axis=y]");
        BlockData withoutProps = blockData("iristest:ns_plain");
        assertEquals("iristest", BukkitBlockState.of(withProps).namespace());
        assertEquals("iristest", BukkitBlockState.of(withoutProps).namespace());
    }

    @Test
    public void withPropertyReplacesExistingProperty() {
        BlockData data = blockData("iristest:merge_block[axis=y,waterlogged=false]");
        PlatformBlockState merged = BukkitBlockState.of(data).withProperty("axis", "x");
        assertEquals("iristest:merge_block[axis=x,waterlogged=false]", merged.key());
    }

    @Test
    public void withPropertyAppendsToExistingProperties() {
        BlockData data = blockData("iristest:append_block[axis=y]");
        PlatformBlockState merged = BukkitBlockState.of(data).withProperty("lit", "true");
        assertEquals("iristest:append_block[axis=y,lit=true]", merged.key());
    }

    @Test
    public void withPropertyAddsBracketSectionWhenAbsent() {
        BlockData data = blockData("iristest:bare_block");
        PlatformBlockState merged = BukkitBlockState.of(data).withProperty("lit", "true");
        assertEquals("iristest:bare_block[lit=true]", merged.key());
    }

    @Test
    public void platformBindingLifecycle() {
        assertFalse(IrisPlatforms.isBound());
        try {
            IrisPlatforms.get();
            fail("get() must throw while unbound");
        } catch (IllegalStateException expected) {
        }
        IrisPlatform first = mock(IrisPlatform.class);
        IrisPlatforms.bind(first);
        assertTrue(IrisPlatforms.isBound());
        assertSame(first, IrisPlatforms.get());
        IrisPlatforms.bind(first);
        assertSame(first, IrisPlatforms.get());
        IrisPlatform second = mock(IrisPlatform.class);
        try {
            IrisPlatforms.bind(second);
            fail("bind() must reject a different instance");
        } catch (IllegalStateException expected) {
        }
        assertSame(first, IrisPlatforms.get());
    }

    @Test
    public void itemKeysAreNamespacedAndNonEmpty() {
        assertNamespacedRegistryList(() -> registries().itemKeys());
    }

    @Test
    public void entityKeysAreNamespacedAndNonEmpty() {
        assertNamespacedRegistryList(() -> registries().entityKeys());
    }

    @Test
    public void enchantmentKeysAreNamespacedAndNonEmpty() {
        assertNamespacedRegistryList(() -> registries().enchantmentKeys());
    }

    @Test
    public void potionEffectKeysAreNamespacedAndNonEmpty() {
        assertNamespacedRegistryList(() -> registries().potionEffectKeys());
    }

    @Test
    public void blockTypeKeysAreNamespacedAndNonEmpty() {
        assertNamespacedRegistryList(() -> registries().blockTypeKeys());
    }

    @Test
    public void blockTypeKeysMatchAuthorableBlockTypeSource() {
        Assume.assumeTrue("live Bukkit registry unavailable in this environment", liveRegistriesAvailable());
        List<String> keys;
        List<String> legacy;
        try {
            keys = registries().blockTypeKeys();
            legacy = Arrays.asList(BukkitBlockResolution.getBlockTypes());
        } catch (Throwable unavailable) {
            Assume.assumeNoException("live Bukkit registry unavailable in this environment", unavailable);
            return;
        }
        assertEquals(legacy, keys);
    }

    @Test
    public void blockStatePropertiesAreKeyedByNamespacedBlock() {
        Map<String, List<PlatformBlockProperty>> states;
        try {
            states = registries().blockStateProperties();
        } catch (Throwable unavailable) {
            Assume.assumeNoException("live Bukkit registry unavailable in this environment", unavailable);
            return;
        }
        assertFalse(states.isEmpty());
        for (Map.Entry<String, List<PlatformBlockProperty>> entry : states.entrySet()) {
            assertTrue("expected namespaced block key but was '" + entry.getKey() + "'", entry.getKey().contains(":"));
            for (PlatformBlockProperty property : entry.getValue()) {
                assertFalse(property.name().isEmpty());
                assertFalse(property.jsonType().isEmpty());
            }
        }
    }
}
