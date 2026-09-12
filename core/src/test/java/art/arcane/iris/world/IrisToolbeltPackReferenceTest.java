package art.arcane.iris.world;

import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.bukkit.World;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IrisToolbeltPackReferenceTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @After
    public void cleanServices() {
        IrisServices.remove(StudioSVC.class);
    }

    @Test
    public void plainPackUsesMatchingDimensionKey() {
        IrisToolbelt.PackReference reference = IrisToolbelt.parsePackReference("overworld");

        assertEquals("overworld", reference.pack());
        assertEquals("overworld", reference.dimension());
        assertFalse(reference.explicitDimension());
    }

    @Test
    public void explicitDimensionKeepsPackAndDimensionSeparate() {
        IrisToolbelt.PackReference reference = IrisToolbelt.parsePackReference(" custom_pack : dimensions/sky ");

        assertEquals("custom_pack", reference.pack());
        assertEquals("dimensions/sky", reference.dimension());
        assertTrue(reference.explicitDimension());
    }

    @Test
    public void redundantExplicitDimensionIsRejected() {
        assertNull(IrisToolbelt.parsePackReference("overworld:overworld"));
        assertNull(IrisToolbelt.parsePackReference("OverWorld:overworld"));
    }

    @Test
    public void repositoryShorthandUsesRepositoryAsDefaultDimension() {
        IrisToolbelt.PackReference reference = IrisToolbelt.parsePackReference("IrisDimensions/overworld/stable");

        assertEquals("IrisDimensions/overworld/stable", reference.pack());
        assertEquals("overworld", reference.dimension());
        assertFalse(reference.explicitDimension());
    }

    @Test
    public void malformedReferencesAreRejected() {
        assertNull(IrisToolbelt.parsePackReference(null));
        assertNull(IrisToolbelt.parsePackReference(""));
        assertNull(IrisToolbelt.parsePackReference(":"));
        assertNull(IrisToolbelt.parsePackReference("pack:"));
        assertNull(IrisToolbelt.parsePackReference(":dimension"));
        assertNull(IrisToolbelt.parsePackReference("../outside:dimension"));
        assertNull(IrisToolbelt.parsePackReference("owner/../outside:dimension"));
        assertNull(IrisToolbelt.parsePackReference("/absolute:dimension"));
        assertNull(IrisToolbelt.parsePackReference(".iris-import-stage:dimension"));
        assertNull(IrisToolbelt.parsePackReference("pack:../../outside"));
        assertNull(IrisToolbelt.parsePackReference("pack:/absolute"));
        assertNull(IrisToolbelt.parsePackReference("pack:nested\\outside"));
        assertNull(IrisToolbelt.parsePackReference("pack:.hidden"));
    }

    @Test
    public void accessReturnsNullAfterStudioServiceIsRemoved() {
        IrisServices.remove(StudioSVC.class);
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> null
        );

        assertNull(IrisToolbelt.access(world));
    }
}
