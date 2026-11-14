package art.arcane.iris.world.loot;

import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.CapturedLog;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisLootOnceWarningTest {
    private static final String UNKNOWN_MATERIAL = "Unknown Material: ";

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private CapturedLog log;

    @Before
    public void captureLogAndRegisterEmptyProvider() {
        log = CapturedLog.bind();
        ExternalDataSVC external = mock(ExternalDataSVC.class);
        when(external.getItemStack(any(), any())).thenReturn(Optional.empty());
        IrisServices.register(ExternalDataSVC.class, external);
    }

    @After
    public void release() {
        IrisServices.remove(ExternalDataSVC.class);
        log.close();
    }

    @Test
    public void anUnknownMaterialWarnsOncePerType() {
        IrisLoot loot = lootOf("mymod:ruby");

        resolve(loot, 1);
        resolve(loot, 2);
        resolve(lootOf("mymod:ruby"), 3);

        assertEquals(1, log.warnings(UNKNOWN_MATERIAL).size());
    }

    @Test
    public void aDifferentUnknownMaterialWarnsSeparately() {
        resolve(lootOf("mymod:ruby"), 1);
        resolve(lootOf("mymod:sapphire"), 1);

        assertEquals(2, log.warnings(UNKNOWN_MATERIAL).size());
    }

    private static IrisLoot lootOf(String type) {
        IrisLoot loot = new IrisLoot();
        loot.setType(type);
        return loot;
    }

    /**
     * The warning is logged before the AIR fallback is built, and building an ItemStack needs a Bukkit
     * server this test does not have.
     */
    private static void resolve(IrisLoot loot, long seed) {
        try {
            loot.get(false, new RNG(seed));
        } catch (NullPointerException noBukkitServer) {
            // expected: see above
        }
    }
}
