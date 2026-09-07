package art.arcane.iris.core;

import art.arcane.iris.testsupport.BukkitTestServer;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mockingDetails;

public class IrisRuntimeSchedulerModeRoutingTest {
    @Test
    public void autoResolvesToPaperLikeOnPurpurBranding() {
        installServer("Purpur", "git-Purpur-2562 (MC: 26.2)");
        IrisSettings.IrisSettingsPregen pregen = new IrisSettings.IrisSettingsPregen();
        pregen.runtimeSchedulerMode = IrisRuntimeSchedulerMode.AUTO;

        IrisRuntimeSchedulerMode resolved = IrisRuntimeSchedulerMode.resolve(pregen);
        assertEquals(IrisRuntimeSchedulerMode.PAPER_LIKE, resolved);
    }

    @Test
    public void autoResolvesToFoliaWhenBrandingContainsFolia() {
        installServer("Folia", "git-Folia-123 (MC: 26.2)");
        IrisSettings.IrisSettingsPregen pregen = new IrisSettings.IrisSettingsPregen();
        pregen.runtimeSchedulerMode = IrisRuntimeSchedulerMode.AUTO;

        IrisRuntimeSchedulerMode resolved = IrisRuntimeSchedulerMode.resolve(pregen);
        assertEquals(IrisRuntimeSchedulerMode.FOLIA, resolved);
    }

    @Test
    public void autoResolvesToPaperLikeOnCanvasBranding() {
        installServer("Canvas", "git-Canvas-101 (MC: 26.2)");
        IrisSettings.IrisSettingsPregen pregen = new IrisSettings.IrisSettingsPregen();
        pregen.runtimeSchedulerMode = IrisRuntimeSchedulerMode.AUTO;

        IrisRuntimeSchedulerMode resolved = IrisRuntimeSchedulerMode.resolve(pregen);
        assertEquals(IrisRuntimeSchedulerMode.PAPER_LIKE, resolved);
    }

    @Test
    public void explicitModeBypassesAutoDetection() {
        installServer("Purpur", "git-Purpur-2562 (MC: 26.2)");
        IrisSettings.IrisSettingsPregen pregen = new IrisSettings.IrisSettingsPregen();

        pregen.runtimeSchedulerMode = IrisRuntimeSchedulerMode.FOLIA;
        IrisRuntimeSchedulerMode foliaResolved = IrisRuntimeSchedulerMode.resolve(pregen);
        assertEquals(IrisRuntimeSchedulerMode.PAPER_LIKE, foliaResolved);

        pregen.runtimeSchedulerMode = IrisRuntimeSchedulerMode.PAPER_LIKE;
        IrisRuntimeSchedulerMode paperResolved = IrisRuntimeSchedulerMode.resolve(pregen);
        assertEquals(IrisRuntimeSchedulerMode.PAPER_LIKE, paperResolved);
    }

    private void installServer(String name, String version) {
        assumeTrue(mockingDetails(BukkitTestServer.install()).isMock());
        BukkitTestServer.brand(name, version);
    }
}
