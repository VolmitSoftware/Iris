package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CustomBiomeSourceStructureContractTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void stackedCustomBiomesUseTheirOwningDimensionNamespace() {
        IrisDimension host = new IrisDimension();
        host.setLoadKey("Host");
        IrisDimension upper = new IrisDimension();
        upper.setLoadKey("Layers/Upper");

        assertEquals("layers:upper/aurora", CustomBiomeSource.customBiomeKey(upper, "Aurora"));
        assertNotEquals(
                CustomBiomeSource.customBiomeKey(host, "Aurora"),
                CustomBiomeSource.customBiomeKey(upper, "Aurora")
        );
    }

    @Test
    public void temporaryNaturalBiomeAnswersAreNotMemoized() {
        Engine temporary = engineAnsweringNaturalFallback(true, 12, -8);
        Engine stable = engineAnsweringNaturalFallback(false, 12, -8);
        assertFalse(CustomBiomeSource.isBiomeCacheable(temporary, 12, -8));
        assertTrue(CustomBiomeSource.isBiomeCacheable(stable, 12, -8));
    }

    private static Engine engineAnsweringNaturalFallback(
            boolean naturalFallback,
            int expectedX,
            int expectedZ
    ) {
        return (Engine) Proxy.newProxyInstance(
                Engine.class.getClassLoader(),
                new Class<?>[]{Engine.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("answersFromNaturalTerrain")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    assertEquals(Integer.valueOf(expectedX), arguments[0]);
                    assertEquals(Integer.valueOf(expectedZ), arguments[1]);
                    return naturalFallback;
                }
        );
    }
}
