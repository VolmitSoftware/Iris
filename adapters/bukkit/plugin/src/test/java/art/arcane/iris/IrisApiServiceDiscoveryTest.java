package art.arcane.iris;

import art.arcane.iris.world.pregen.PregenApiSink;
import art.arcane.iris.platform.bukkit.api.IrisApiEventSVC;
import art.arcane.iris.platform.bukkit.api.IrisTerrainSVC;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class IrisApiServiceDiscoveryTest {
    @Test
    public void bothApiServicesSatisfyTheServiceLoaderRule() {
        assertTrue(Iris.isConcreteImplementation(IrisTerrainSVC.class, IrisService.class));
        assertTrue(Iris.isConcreteImplementation(IrisApiEventSVC.class, IrisService.class));
    }

    @Test
    public void theEventServiceIsTheSinkThePregeneratorLooksUp() {
        assertTrue(PregenApiSink.class.isAssignableFrom(IrisApiEventSVC.class));
    }
}
