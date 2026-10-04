package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.world.IrisWorld;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class BukkitSubterrainBiomePrecedenceTest {
    @Test
    public void featureOwnsPublishedBiomeAboveDepthGateAndBeforeStackOwnership() throws Exception {
        Engine engine = mock(Engine.class);
        IrisBiome feature = mock(IrisBiome.class);
        IrisDimension host = new IrisDimension();
        when(engine.getComplex()).thenReturn(mock(IrisComplex.class));
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build());
        when(engine.getDimension()).thenReturn(host);
        when(engine.getSubterrainBiome(12, 224, -20)).thenReturn(feature);
        @SuppressWarnings("unchecked")
        BukkitBiomePolicy<String, String> policy = mock(BukkitBiomePolicy.class, CALLS_REAL_METHODS);
        Field owner = BukkitBiomePolicy.class.getDeclaredField("engine");
        owner.setAccessible(true);
        owner.set(policy, engine);
        DimensionStackLayout stack = mock(DimensionStackLayout.class);
        Method resolve = BukkitBiomePolicy.class.getDeclaredMethod("resolveBiomeResolution", int.class, int.class,
                int.class, IrisDimensionCarvingResolver.State.class, boolean.class, DimensionStackLayout.class);
        resolve.setAccessible(true);
        Object resolved = resolve.invoke(policy, 3, 40, -5, null, true, stack);
        assertSame(feature, field(resolved, "irisBiome"));
        assertSame(host, field(resolved, "dimension"));
        assertEquals(true, field(resolved, "underground"));
        verify(engine).getSubterrainBiome(12, 224, -20);
        verifyNoInteractions(stack);
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
