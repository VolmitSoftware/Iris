package art.arcane.iris.testsupport;


import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.PluginManager;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEFAULTS;

public final class BukkitTestServer {
    private static final String DEFAULT_NAME = "IrisTestServer";
    private static final String DEFAULT_VERSION = "1.0";
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
            applyBranding(server, DEFAULT_NAME, DEFAULT_VERSION);
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

    public static RegistryAccess registryAccess() {
        Map<Object, Object> registries = new HashMap<>();
        return mock(RegistryAccess.class, invocation -> {
            if (!invocation.getMethod().getName().equals("getRegistry")) {
                return RETURNS_DEFAULTS.answer(invocation);
            }
            Object key = invocation.getArgument(0);
            return registries.computeIfAbsent(key, registryKey -> registryProxy(registryKey == RegistryKey.GAME_RULE
                    || registryKey == GameRule.class));
        });
    }

    private static Object registryProxy(boolean gameRules) {
        Map<Key, Object> values = new HashMap<>();
        return Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getOrThrow", "get" -> values.computeIfAbsent((Key) arguments[0],
                            key -> gameRules ? mock(GameRule.class) : biome(key));
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> gameRules ? "iris-test-game-rule-registry" : "iris-test-biome-registry";
                    case "size" -> values.size();
                    case "hasTag" -> false;
                    default -> null;
                });
    }

    private static Biome biome(Key key) {
        NamespacedKey namespaced = new NamespacedKey(key.namespace(), key.value());
        return (Biome) Proxy.newProxyInstance(Biome.class.getClassLoader(), new Class<?>[]{Biome.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getKey" -> namespaced;
                    case "hashCode" -> namespaced.hashCode();
                    case "equals" -> proxy == arguments[0];
                    case "toString", "name" -> key.value();
                    default -> null;
                });
    }

    public static ServerBranding branding() {
        return new ServerBranding();
    }

    private static void applyBranding(Server server, String name, String version) {
        doReturn(name).when(server).getName();
        doReturn(version).when(server).getVersion();
        doReturn(version).when(server).getBukkitVersion();
    }

    public static final class ServerBranding implements TestRule {
        private boolean applied;

        private ServerBranding() {
        }

        public void set(String name, String version) {
            applyBranding(install(), name, version);
            applied = true;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        base.evaluate();
                    } finally {
                        restore();
                    }
                }
            };
        }

        private void restore() {
            if (!applied) {
                return;
            }

            applied = false;
            applyBranding(install(), DEFAULT_NAME, DEFAULT_VERSION);
        }
    }

    public static BlockData blockData(String key) {
        String canonical = key.indexOf(':') >= 0 ? key : "minecraft:" + key;
        BlockData data = mock(BlockData.class);
        doReturn(canonical).when(data).getAsString();
        doReturn(canonical).when(data).getAsString(anyBoolean());
        return data;
    }
}
