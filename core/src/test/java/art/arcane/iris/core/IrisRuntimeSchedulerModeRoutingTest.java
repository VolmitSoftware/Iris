package art.arcane.iris.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

public class IrisRuntimeSchedulerModeRoutingTest {
    @Test
    public void autoResolvesToPaperLikeOnPurpurBranding() {
        installServer("Purpur", "git-Purpur-2562 (MC: 1.21.11)");
        IrisSettings.IrisSettingsPregen pregen = new IrisSettings.IrisSettingsPregen();
        pregen.runtimeSchedulerMode = IrisRuntimeSchedulerMode.AUTO;

        IrisRuntimeSchedulerMode resolved = IrisRuntimeSchedulerMode.resolve(pregen);
        assertEquals(IrisRuntimeSchedulerMode.PAPER_LIKE, resolved);
    }

    @Test
    public void autoResolvesToFoliaWhenBrandingContainsFolia() {
        installServer("Folia", "git-Folia-123 (MC: 1.21.11)");
        IrisSettings.IrisSettingsPregen pregen = new IrisSettings.IrisSettingsPregen();
        pregen.runtimeSchedulerMode = IrisRuntimeSchedulerMode.AUTO;

        IrisRuntimeSchedulerMode resolved = IrisRuntimeSchedulerMode.resolve(pregen);
        assertEquals(IrisRuntimeSchedulerMode.FOLIA, resolved);
    }

    @Test
    public void explicitModeBypassesAutoDetection() {
        installServer("Purpur", "git-Purpur-2562 (MC: 1.21.11)");
        IrisSettings.IrisSettingsPregen pregen = new IrisSettings.IrisSettingsPregen();

        pregen.runtimeSchedulerMode = IrisRuntimeSchedulerMode.FOLIA;
        IrisRuntimeSchedulerMode foliaResolved = IrisRuntimeSchedulerMode.resolve(pregen);
        assertEquals(IrisRuntimeSchedulerMode.PAPER_LIKE, foliaResolved);

        pregen.runtimeSchedulerMode = IrisRuntimeSchedulerMode.PAPER_LIKE;
        IrisRuntimeSchedulerMode paperResolved = IrisRuntimeSchedulerMode.resolve(pregen);
        assertEquals(IrisRuntimeSchedulerMode.PAPER_LIKE, paperResolved);
    }

    private void installServer(String name, String version) {
        Server server = Bukkit.getServer();
        if (server == null) {
            server = mock(Server.class);
            try {
                Bukkit.setServer(server);
            } catch (Throwable ignored) {
                server = Bukkit.getServer();
            }
        }

        assumeTrue(server != null && mockingDetails(server).isMock());

        BlockData emptyBlockData = mock(BlockData.class);
        doReturn(Logger.getLogger("IrisTest")).when(server).getLogger();
        doReturn(name).when(server).getName();
        doReturn(version).when(server).getVersion();
        doReturn(version).when(server).getBukkitVersion();
        doReturn(emptyBlockData).when(server).createBlockData(any(Material.class));
        doReturn(emptyBlockData).when(server).createBlockData(anyString());
    }
}
