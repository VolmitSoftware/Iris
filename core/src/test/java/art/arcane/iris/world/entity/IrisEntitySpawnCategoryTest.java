package art.arcane.iris.world.entity;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.nativelib.entity.NativeEntityType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisEntitySpawnCategoryTest {
    private IrisPlatform previous;

    @Before
    public void saveBinding() {
        previous = IrisPlatforms.getOrNull();
        IrisPlatforms.unbind();
    }

    @After
    public void restoreBinding() {
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }

    @Test
    public void unavailableAndUnknownTypesUseMisc() {
        IrisEntity entity = new IrisEntity().setType("minecraft:zombie");
        assertEquals("misc", entity.spawnCategory());
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        assertEquals("misc", entity.spawnCategory());
        assertEquals("misc", new IrisEntity().setType(null).spawnCategory());
    }

    @Test
    public void currentRegistryCategoryIsNeverStuckAcrossPlatformRebinding() {
        IrisEntity entity = new IrisEntity().setType("minecraft:zombie");
        bind("monster");
        assertEquals("monster", entity.spawnCategory());
        IrisPlatforms.unbind();
        bind("creature");
        assertEquals("creature", entity.spawnCategory());
    }

    @Test
    public void acceptedMixedCaseAndWhitespaceTypesKeepNativeCategory() {
        bind("monster");
        assertEquals("monster", new IrisEntity().setType(" MINECRAFT:ZoMbIe ").spawnCategory());
    }

    private static void bind(String category) {
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        NativeEntityType type = mock(NativeEntityType.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.entity("minecraft:zombie")).thenReturn(type);
        when(type.spawnCategory()).thenReturn(category);
        IrisPlatforms.bind(platform);
    }
}
