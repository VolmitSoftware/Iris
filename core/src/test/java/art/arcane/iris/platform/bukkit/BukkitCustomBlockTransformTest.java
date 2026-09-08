package art.arcane.iris.platform.bukkit;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.iris.util.common.data.registry.RegistryUtil;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

public class BukkitCustomBlockTransformTest {
    @BeforeClass
    public static void installServer() {
        BukkitTestServer.install();
        try (MockedStatic<RegistryUtil> registry = mockStatic(RegistryUtil.class)) {
            registry.when(() -> RegistryUtil.find(Material.class, "grass", "short_grass")).thenReturn(Material.SHORT_GRASS);
            BukkitBlockResolution.getAir();
        }
    }

    @Test
    public void waterloggingUpdatesSemanticIdentityAndResolvedCarrier() {
        IrisCustomData original = custom("craftengine:forest/amber_slab[axis=x,waterlogged=false]", 1);
        IrisCustomData expected = custom("craftengine:forest/amber_slab[axis=x,waterlogged=true]", 2);
        PlatformBlockState state = BukkitBlockState.of(original);

        try (MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class)) {
            resolveTo(resolution, expected);
            PlatformBlockState result = state.withProperty("waterlogged", "true");

            assertSame(expected, result.nativeHandle());
            assertEquals(expected.getCustom().toString(), result.deferredPlacementKey());
            assertEquals(expected.getBase().getAsString(), result.placementBaseState().key());
            assertEquals("craftengine:forest/amber_slab[axis=x,waterlogged=false]", state.key());
            resolution.verify(() -> BukkitBlockResolution.resolveOrNull(anyString()));
        }
    }

    @Test
    public void customPropertyRejectsAnUnresolvableValue() {
        PlatformBlockState state = BukkitBlockState.of(custom("craftengine:forest/amber_log[axis=x]", 1));

        try (MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class)) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> state.withProperty("axis", "invalid"));

            assertTrue(error.getMessage().contains("axis=invalid"));
            assertEquals("craftengine:forest/amber_log[axis=x]", state.key());
            resolution.verify(() -> BukkitBlockResolution.resolveOrNull("craftengine:forest/amber_log[axis=invalid]"));
        }
    }

    @Test
    public void semanticFacingRotatesWithoutDirectionalCarrier() {
        IrisCustomData original = custom("craftengine:forest/amber_lamp[facing=north,waterlogged=true]", 1);
        IrisCustomData expected = custom("craftengine:forest/amber_lamp[facing=west,waterlogged=true]", 2);
        PlatformBlockState state = BukkitBlockState.of(original);

        try (MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class)) {
            resolveTo(resolution, expected);
            PlatformBlockState result = IrisObjectRotation.of(0, 90, 0).rotate(state, 0, 0, 0);

            assertSame(expected, result.nativeHandle());
            assertEquals(expected.getCustom().toString(), result.key());
            assertEquals("craftengine:forest/amber_lamp[facing=north,waterlogged=true]", state.key());
            resolution.verify(() -> BukkitBlockResolution.resolveOrNull(anyString()));
        }
    }

    @Test
    public void semanticAxisRotatesThroughRawBlockDataEntryPoint() {
        IrisCustomData original = custom("craftengine:forest/amber_log[axis=x,waterlogged=false]", 1);
        IrisCustomData expected = custom("craftengine:forest/amber_log[axis=z,waterlogged=false]", 2);

        try (MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class)) {
            resolveTo(resolution, expected);
            BlockData result = IrisObjectRotation.of(0, 90, 0).rotate(original, 0, 0, 0);

            assertSame(expected, result);
            assertEquals("craftengine:forest/amber_log[axis=x,waterlogged=false]", original.getCustom().toString());
            resolution.verify(() -> BukkitBlockResolution.resolveOrNull(anyString()));
        }
    }

    @Test
    public void semanticRotationPreservesSixteenWayOrientation() {
        PlatformBlockState state = BukkitBlockState.of(custom("craftengine:forest/amber_sign[rotation=0]", 1));
        IrisCustomData expected = custom("craftengine:forest/amber_sign[rotation=12]", 2);

        try (MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class)) {
            resolveTo(resolution, expected);
            PlatformBlockState result = IrisObjectRotation.of(0, 90, 0).rotate(state, 0, 0, 0);

            assertSame(expected, result.nativeHandle());
            resolution.verify(() -> BukkitBlockResolution.resolveOrNull(anyString()));
        }
    }

    @Test
    public void semanticConnectionsRotateTogether() {
        PlatformBlockState state = BukkitBlockState.of(custom(
                "craftengine:forest/amber_fence[north=true,east=false,south=false,west=false]", 1));
        IrisCustomData expected = custom(
                "craftengine:forest/amber_fence[north=false,east=false,south=false,west=true]", 2);

        try (MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class)) {
            resolveTo(resolution, expected);
            PlatformBlockState result = IrisObjectRotation.of(0, 90, 0).rotate(state, 0, 0, 0);

            assertSame(expected, result.nativeHandle());
            resolution.verify(() -> BukkitBlockResolution.resolveOrNull(anyString()));
        }
    }

    @Test
    public void unsupportedRotationRetainsTheOriginalCustomBlock() {
        PlatformBlockState state = BukkitBlockState.of(custom("craftengine:forest/amber_lamp[facing=north]", 1));

        try (MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class)) {
            assertSame(state, IrisObjectRotation.of(90, 0, 0).rotate(state, 0, 0, 0));
            resolution.verify(() -> BukkitBlockResolution.resolveOrNull(anyString()));
        }
    }

    @Test
    public void unchangedSemanticAxisDoesNotResolveOrMutateCarrier() {
        PlatformBlockState state = BukkitBlockState.of(custom("craftengine:forest/amber_log[axis=y]", 1));

        try (MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class)) {
            assertSame(state, IrisObjectRotation.of(0, 90, 0).rotate(state, 0, 0, 0));
            resolution.verifyNoInteractions();
        }
    }

    private static IrisCustomData custom(String key, int note) {
        BlockData carrier = BukkitTestServer.blockData("minecraft:note_block[instrument=harp,note=" + note + ",powered=false]");
        return IrisCustomData.of(carrier, Identifier.fromString(key));
    }

    private static void resolveTo(MockedStatic<BukkitBlockResolution> resolution, IrisCustomData expected) {
        resolution.when(() -> BukkitBlockResolution.resolveOrNull(anyString())).thenAnswer(invocation -> {
            Identifier actual = Identifier.fromString(invocation.getArgument(0, String.class));
            assertEquals(ExternalDataSVC.parseState(expected.getCustom()), ExternalDataSVC.parseState(actual));
            return expected;
        });
    }
}
