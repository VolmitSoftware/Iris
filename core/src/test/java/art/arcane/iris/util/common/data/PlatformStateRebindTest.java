package art.arcane.iris.util.common.data;

import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.project.hunk.view.ChunkDataHunkHolder;
import org.bukkit.generator.ChunkGenerator;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlatformStateRebindTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final List<String> CONVERTED = List.of(
            "art.arcane.iris.engine.actuator.IrisDimensionStackActuator",
            "art.arcane.iris.engine.actuator.IrisTerrainNormalActuator",
            "art.arcane.iris.engine.modifier.IrisPerfectionModifier",
            "art.arcane.iris.engine.modifier.IrisPostModifier",
            "art.arcane.iris.engine.object.IrisBiomeLayerGenerator",
            "art.arcane.iris.engine.platform.studio.generators.BiomeBuffetGenerator",
            "art.arcane.iris.engine.mantle.EngineMantle",
            "art.arcane.iris.util.project.hunk.view.ChunkDataHunkHolder");

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void chunkDataAirIsResolvedAgainstTheCurrentlyBoundPlatform() {
        PlatformBlockState first = bindPlatform("first");
        ChunkGenerator.ChunkData chunk = mock(ChunkGenerator.ChunkData.class);
        when(chunk.getMinHeight()).thenReturn(-64);
        when(chunk.getMaxHeight()).thenReturn(320);
        ChunkDataHunkHolder holder = new ChunkDataHunkHolder(chunk);

        assertSame(first, holder.getRaw(0, -1, 0));

        IrisPlatforms.unbind();
        PlatformBlockState second = bindPlatform("second");

        assertSame(second, holder.getRaw(0, -1, 0));
    }

    @Test
    public void mantleAirIsResolvedAgainstTheCurrentlyBoundPlatform() {
        PlatformBlockState first = bindPlatform("first");

        assertSame(first, EngineMantle.AIR.get());

        IrisPlatforms.unbind();
        PlatformBlockState second = bindPlatform("second");

        assertSame(second, EngineMantle.AIR.get());
    }

    @Test
    public void aLatchKeepsServingTheLastResolutionWhileNothingIsBound() {
        PlatformBlockState first = bindPlatform("first");
        BoundBlockState state = BoundBlockState.of("AIR");

        assertSame(first, state.get());

        IrisPlatforms.unbind();

        assertSame(first, state.get());
    }

    @Test
    public void noGenerationClassLatchesABlockStateAtClassInitialisation() throws ClassNotFoundException {
        List<String> latched = new ArrayList<>();

        for (String name : CONVERTED) {
            Class<?> owner = Class.forName(name, false, PlatformStateRebindTest.class.getClassLoader());
            collectLatches(owner, latched);
            for (Class<?> nested : owner.getDeclaredClasses()) {
                collectLatches(nested, latched);
            }
        }

        assertEquals(List.of(), latched);
    }

    private static void collectLatches(Class<?> type, List<String> latched) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Class<?> fieldType = field.getType();
            if (PlatformBlockState.class.isAssignableFrom(fieldType)
                    || (fieldType.isArray() && PlatformBlockState.class.isAssignableFrom(fieldType.getComponentType()))) {
                latched.add(type.getName() + "." + field.getName());
            }
        }
    }

    private static PlatformBlockState bindPlatform(String label) {
        Map<String, PlatformBlockState> states = new HashMap<>();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> states.computeIfAbsent(
                label + ':' + invocation.<String>getArgument(0),
                key -> {
                    PlatformBlockState state = mock(PlatformBlockState.class);
                    when(state.key()).thenReturn(key);
                    return state;
                }));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        return registries.block("AIR");
    }
}
