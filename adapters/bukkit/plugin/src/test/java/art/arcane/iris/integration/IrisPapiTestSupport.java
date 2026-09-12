package art.arcane.iris.integration;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

final class IrisPapiTestSupport {
    private IrisPapiTestSupport() {
    }

    static World world(String name) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> switch (method.getName()) {
            case "getName" -> name;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "World[" + name + "]";
            default -> throw new AssertionError("a placeholder must not call World#" + method.getName());
        };

        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                handler);
    }

    static OfflinePlayer player(UUID id) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "OfflinePlayer[" + id + "]";
            default -> throw new AssertionError("a placeholder must not call OfflinePlayer#" + method.getName());
        };

        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class},
                handler);
    }

    static final class StandingPlayer {
        private final UUID id;
        private final AtomicReference<Location> standing = new AtomicReference<>();
        private final Player handle;

        StandingPlayer(UUID id, Location location) {
            this.id = id;
            this.standing.set(location);
            InvocationHandler handler = (Object proxy, Method method, Object[] args) -> switch (method.getName()) {
                case "getUniqueId" -> this.id;
                case "getLocation" -> this.standing.get();
                case "getWorld" -> this.standing.get().getWorld();
                case "getName" -> "StandingPlayer";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "Player[" + this.id + "]";
                default -> throw new AssertionError("a placeholder listener must not call Player#" + method.getName());
            };

            this.handle = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    handler);
        }

        UUID id() {
            return id;
        }

        Player handle() {
            return handle;
        }

        Location standing() {
            return standing.get();
        }

        void standAt(Location location) {
            standing.set(location);
        }
    }

    static final class Clock implements LongSupplier {
        private final AtomicLong now = new AtomicLong(1_000_000L);

        void advance(long millis) {
            now.addAndGet(millis);
        }

        @Override
        public long getAsLong() {
            return now.get();
        }
    }
}
