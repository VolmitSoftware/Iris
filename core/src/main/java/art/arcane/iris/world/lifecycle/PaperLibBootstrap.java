package art.arcane.iris.world.lifecycle;

import art.arcane.iris.spi.IrisLogging;
import io.papermc.lib.PaperLib;
import io.papermc.lib.environments.PaperEnvironment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PaperLibBootstrap {
    private PaperLibBootstrap() {
    }

    public static void install() {
        if (PaperLib.getEnvironment().getMinecraftVersion() > 0) {
            return;
        }

        String bukkitVersion = Bukkit.getBukkitVersion();
        if (!isModernVersionScheme(bukkitVersion)) {
            return;
        }

        boolean hasAsyncTeleport = hasMethod(Entity.class, "teleportAsync", Location.class, PlayerTeleportEvent.TeleportCause.class);
        boolean hasAsyncChunks = hasMethod(World.class, "getChunkAtAsync", int.class, int.class, boolean.class);
        if (!hasAsyncTeleport || !hasAsyncChunks) {
            return;
        }

        PaperLib.setCustomEnvironment(new ModernPaperEnvironment());
        IrisLogging.debug("Installed forced-modern Paper environment for MC " + bukkitVersion + "; bundled PaperLib predates the two-digit version scheme");
    }

    static boolean isModernVersionScheme(String bukkitVersion) {
        if (bukkitVersion == null || bukkitVersion.isEmpty()) {
            return false;
        }

        int end = 0;
        while (end < bukkitVersion.length() && Character.isDigit(bukkitVersion.charAt(end))) {
            end++;
        }

        if (end == 0) {
            return false;
        }

        try {
            return Integer.parseInt(bukkitVersion.substring(0, end)) > 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            owner.getMethod(name, parameterTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static final class ModernPaperEnvironment extends PaperEnvironment {
        @Override
        public boolean isVersion(int minor, int patch) {
            return true;
        }
    }
}
