package art.arcane.iris.engine;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.common.data.B;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Binds a platform whose registries answer every block name with a usable, namespaced block. {@code B} keeps the
 * registries it first sees, so binding and unbinding here also clear that cache: what a test binds is what it
 * reads, and nothing it binds is left behind for the tests that run after it.
 */
public final class UsableBlockPlatform {
    private UsableBlockPlatform() {
    }

    public static void bind() {
        IrisPlatforms.unbind();
        forgetCachedRegistries();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        Map<String, PlatformBlockState> states = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> states.computeIfAbsent(
                invocation.<String>getArgument(0).toLowerCase(Locale.ROOT), UsableBlockPlatform::block));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    public static void unbind() {
        IrisPlatforms.unbind();
        forgetCachedRegistries();
    }

    private static void forgetCachedRegistries() {
        try {
            Field cached = B.class.getDeclaredField("registries");
            cached.setAccessible(true);
            cached.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("B no longer caches its registries in a field named registries", e);
        }
    }

    /** A solid, occluding block of the given name, unless it is an air. */
    public static PlatformBlockState block(String name) {
        String key = name.contains(":") ? name : "minecraft:" + name;
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.materialKey()).thenReturn(key.split("\\[", 2)[0]);
        when(block.isSolid()).thenReturn(!key.contains("air"));
        when(block.isOccluding()).thenReturn(!key.contains("air"));
        return block;
    }
}
