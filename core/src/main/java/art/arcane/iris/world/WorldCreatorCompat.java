package art.arcane.iris.world;

import art.arcane.iris.spi.IrisLogging;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldCreator;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * WorldCreator.ofKey and WorldCreator#key are Paper-API-only. Once a call throws
 * NoSuchMethodError (plain Spigot/CraftBukkit) this flips and every later call goes
 * straight to the fallback. The fallback derives names/keys through IrisWorldStorage's
 * current configured-name mapping so persistent Spigot worlds round-trip without changing
 * their startup directory.
 *
 * <p>Paper names a keyed creator {@code <namespace>_<key>} and refuses a creator built from a name
 * and a key together, but it names that same world {@code <level>_<namespace>_<key>} when it loads
 * it out of {@code <level>/dimensions/<namespace>/<key>} on every later boot. A persistent world
 * therefore has to be born under the startup name, which is what {@link #ofPersistentKey} does.</p>
 */
public final class WorldCreatorCompat {
    private static volatile boolean keyedCreatorsUnavailable;
    private static volatile boolean creatorNamesUnwritable;
    private static volatile Field creatorNameField;

    private WorldCreatorCompat() {
    }

    public static WorldCreator ofKey(NamespacedKey worldKey) {
        WorldCreator keyedCreator = keyedCreator(worldKey);
        if (keyedCreator != null) {
            return keyedCreator;
        }
        return new WorldCreator(IrisWorldStorage.logicalName(worldKey));
    }

    public static WorldCreator ofKey(NamespacedKey worldKey, String worldName) {
        String name = requireWorldName(worldName);
        WorldCreator keyedCreator = keyedCreator(worldKey);
        if (keyedCreator == null) {
            return new WorldCreator(name);
        }
        renameCreator(keyedCreator, name);
        return keyedCreator;
    }

    public static WorldCreator ofPersistentKey(NamespacedKey worldKey) {
        return ofKey(worldKey, persistentWorldName(worldKey, IrisWorldStorage.levelRoot().getName()));
    }

    public static File persistentDimensionRoot(NamespacedKey worldKey) {
        if (keyedCreator(worldKey) != null) {
            return IrisWorldStorage.requireSafePersistentDimensionRoot(worldKey);
        }
        return IrisWorldStorage.configuredDimensionRoot(
                Bukkit.getWorldContainer(),
                IrisWorldStorage.levelRoot(),
                worldKey
        );
    }

    public static File persistentLevelRoot(NamespacedKey worldKey) {
        if (keyedCreator(worldKey) != null) {
            return persistentDimensionRoot(worldKey);
        }
        return IrisWorldStorage.configuredLevelRoot(
                Bukkit.getWorldContainer(),
                IrisWorldStorage.levelRoot(),
                worldKey
        );
    }

    public static NamespacedKey keyOf(WorldCreator creator) {
        if (!keyedCreatorsUnavailable) {
            try {
                return creator.key();
            } catch (NoSuchMethodError e) {
                keyedCreatorsUnavailable = true;
            }
        }
        return IrisWorldStorage.keyFromConfiguredWorldName(
                creator.name(),
                IrisWorldStorage.levelRoot().getName()
        );
    }

    static String persistentWorldName(NamespacedKey worldKey, String levelName) {
        return IrisWorldStorage.configuredWorldName(worldKey, levelName);
    }

    private static WorldCreator keyedCreator(NamespacedKey worldKey) {
        if (keyedCreatorsUnavailable) {
            return null;
        }
        try {
            return WorldCreator.ofKey(worldKey);
        } catch (NoSuchMethodError e) {
            keyedCreatorsUnavailable = true;
            return null;
        }
    }

    /**
     * WorldCreator rejects a name and a key passed together and derives the name from the key, so the
     * only way to hand a keyed world its startup name at creation is to overwrite the creator's name.
     * If that ever stops working the keyed creator is still correct except for the name, which is what
     * shipped before, so the world is created rather than refused.
     */
    private static void renameCreator(WorldCreator creator, String worldName) {
        if (worldName.equals(creator.name()) || creatorNamesUnwritable) {
            return;
        }
        try {
            creatorNameField().set(creator, worldName);
        } catch (ReflectiveOperationException | RuntimeException | Error e) {
            creatorNamesUnwritable = true;
            IrisLogging.warn("WorldCreator name is not writable; world %s is created as %s and renamed on the next boot.",
                    worldName, creator.name());
        }
    }

    private static Field creatorNameField() throws ReflectiveOperationException {
        Field cached = creatorNameField;
        if (cached != null) {
            return cached;
        }
        Field name = WorldCreator.class.getDeclaredField("name");
        name.setAccessible(true);
        creatorNameField = name;
        return name;
    }

    private static String requireWorldName(String worldName) {
        String name = Objects.requireNonNull(worldName, "worldName").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty.");
        }
        return name;
    }
}
