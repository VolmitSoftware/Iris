package art.arcane.iris.testsupport;

import art.arcane.iris.generation.decoration.DecoratorPlatformHooks;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.Set;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class PlantPlatform implements AutoCloseable {
    private final IrisPlatform previous;
    private final DecoratorPlatformHooks.Bindings hooks;

    private PlantPlatform(IrisPlatform previous, DecoratorPlatformHooks.Bindings hooks) {
        this.previous = previous;
        this.hooks = hooks;
    }

    public static PlantPlatform bind(Set<String> known) {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.air()).thenReturn(PlantBlockState.of("minecraft:air"));
        when(registries.blockOrNull(anyString(), anyBoolean())).thenAnswer(invocation -> known(known, invocation.getArgument(0)));
        when(registries.blockOrNull(anyString())).thenAnswer(invocation -> known(known, invocation.getArgument(0)));
        IrisPlatforms.bind(platform);
        return new PlantPlatform(previous, DecoratorPlatformHooks.bind(null, (surface, upward) -> surface.isSolid()));
    }

    private static NativeBlockState known(Set<String> known, String key) {
        PlantBlockState state = PlantBlockState.of(key);
        return known.contains(state.block()) ? state : null;
    }

    @Override
    public void close() {
        DecoratorPlatformHooks.restore(hooks);
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }
}
