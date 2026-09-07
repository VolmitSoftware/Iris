package art.arcane.iris.testsupport;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.PluginManager;
import org.mockito.invocation.InvocationOnMock;

import java.util.Locale;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public final class BukkitTestServer {
    private static final Object LOCK = new Object();
    private static volatile Server installed;

    private BukkitTestServer() {
    }

    public static Server install() {
        Server current = installed;

        if (current != null) {
            return current;
        }

        synchronized (LOCK) {
            if (installed != null) {
                return installed;
            }

            Server existing = Bukkit.getServer();

            if (existing != null) {
                installed = existing;
                return existing;
            }

            Server server = mock(Server.class);
            doReturn(Logger.getLogger("IrisTest")).when(server).getLogger();
            doReturn(mock(PluginManager.class)).when(server).getPluginManager();
            doReturn("IrisTestServer").when(server).getName();
            doReturn("1.0").when(server).getVersion();
            doReturn("1.0").when(server).getBukkitVersion();
            doAnswer((InvocationOnMock invocation) -> blockData(invocation.getArgument(0, Material.class).name().toLowerCase(Locale.ROOT)))
                    .when(server).createBlockData(any(Material.class));
            doAnswer((InvocationOnMock invocation) -> blockData(invocation.getArgument(0, String.class)))
                    .when(server).createBlockData(anyString());

            try {
                Bukkit.setServer(server);
            } catch (Throwable e) {
                if (Bukkit.getServer() != server) {
                    throw new IllegalStateException("The shared Bukkit test server could not be installed", e);
                }
            }

            installed = server;
            return server;
        }
    }

    public static void brand(String name, String version) {
        Server server = install();
        doReturn(name).when(server).getName();
        doReturn(version).when(server).getVersion();
        doReturn(version).when(server).getBukkitVersion();
    }

    public static BlockData blockData(String key) {
        String canonical = key.indexOf(':') >= 0 ? key : "minecraft:" + key;
        BlockData data = mock(BlockData.class);
        doReturn(canonical).when(data).getAsString();
        doReturn(canonical).when(data).getAsString(anyBoolean());
        return data;
    }
}
