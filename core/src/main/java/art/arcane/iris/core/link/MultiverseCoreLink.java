/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.link;

import art.arcane.iris.core.IrisWorldStorage;
import art.arcane.iris.core.IrisWorlds;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.platform.bukkit.BukkitEnvironment;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.locale.message.Message;
import org.mvplugins.multiverse.core.utils.result.Attempt;
import org.mvplugins.multiverse.core.utils.result.FailureReason;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.ImportWorldOptions;
import org.mvplugins.multiverse.core.world.options.LoadWorldOptions;
import org.mvplugins.multiverse.core.world.options.RemoveWorldOptions;
import org.mvplugins.multiverse.core.world.reasons.LoadFailureReason;
import org.mvplugins.multiverse.core.world.reasons.RemoveFailureReason;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Multiverse indexes its world store by NamespacedKey and by the Bukkit world name it saw when the
 * world was imported, and records that name as {@code legacy-world-name}. Iris creates and registers
 * its worlds under the Paper startup name ({@code <level>_<namespace>_<key>}), so the name Multiverse
 * sees at import is already the name it will see again after every restart, and it is the name every
 * Iris unregistration uses.
 * <p>
 * Every entry point here degrades instead of throwing when Multiverse is absent, not yet loaded, or
 * has moved the internals this link reflects into. A server without Multiverse must behave exactly as
 * it did before this link existed.
 */
public class MultiverseCoreLink {
    public static final String MULTIVERSE_PLUGIN = "Multiverse-Core";
    private static final String GENERATOR_PREFIX = "Iris:";
    /**
     * Reflection into Multiverse internals is warned about once. A server that keeps logging the same
     * broken accessor for every world tells the admin nothing the first line did not.
     */
    private static volatile boolean worldConfigUnwritable;

    /**
     * Best-effort scrub for worlds Iris never registered (studio worlds). Multiverse not knowing the
     * world is the normal case here, so a miss is not worth a warning.
     */
    public boolean removeIfPresent(World world) {
        String worldName = Objects.requireNonNull(world, "world").getName();
        WorldManager manager = worldManager();
        if (manager == null) {
            return false;
        }
        try {
            MultiverseWorld multiverseWorld = resolve(manager, worldName);
            return multiverseWorld != null && remove(manager, multiverseWorld, worldName);
        } catch (Throwable failure) {
            // Studio teardown is not allowed to fail over a Multiverse bookkeeping entry.
            IrisLogging.warn("Multiverse refused to drop \"%s\": %s", worldName, describe(failure));
            return false;
        }
    }

    /**
     * Authoritative unregistration for a world Iris registered. A miss leaves a ghost entry behind in
     * Multiverse's worlds.yml, so it is reported. A refusal from Multiverse itself is raised, because
     * the caller is a removal the admin asked for and a silent success would delete the world files
     * while Multiverse kept pointing at them.
     */
    public boolean removeFromConfig(String configuredWorldName) {
        WorldManager manager = worldManager();
        if (manager == null) {
            IrisLogging.debug("Multiverse is not available; skipped unregistering \""
                    + configuredWorldName + "\".");
            return false;
        }
        String worldName = requireWorldName(configuredWorldName);
        MultiverseWorld multiverseWorld;
        try {
            multiverseWorld = resolve(manager, worldName);
        } catch (Throwable failure) {
            // Only a refusal Multiverse states is worth stopping a removal for; an unusable link is not.
            IrisLogging.warn("Could not look %s up in Multiverse: %s; its worlds.yml entry was left as-is.",
                    worldName, describe(failure));
            return false;
        }
        if (multiverseWorld == null) {
            IrisLogging.warn("Multiverse has no world registered as %s; its worlds.yml entry was left as-is.",
                    worldName);
            return false;
        }
        return remove(manager, multiverseWorld, worldName);
    }

    /**
     * Registers a freshly created world with Multiverse. Never throws: a Multiverse failure here used
     * to unwind {@code IrisCreator}, whose rollback deletes the world storage that was just built.
     */
    public void updateWorld(World bukkitWorld, String configuredWorldName, String pack) {
        try {
            World world = Objects.requireNonNull(bukkitWorld, "bukkitWorld");
            String worldName = requireWorldName(configuredWorldName);
            WorldManager manager = worldManager();
            if (manager == null) {
                return;
            }
            if (!worldName.equals(world.getName())) {
                // Multiverse records the live name as legacy-world-name. A live name that is not the
                // startup name makes it re-import the world next boot and collide with its own config key.
                IrisLogging.info("World %s is live as %s; Multiverse will record the live name.",
                        worldName, world.getName());
            }
            String generator = GENERATOR_PREFIX + Objects.requireNonNull(pack, "pack");
            MultiverseWorld multiverseWorld = manager.getWorld(world)
                    .orElse(() -> manager.getWorld(worldName))
                    .getOrElse(() -> {
                        // Import through the live world so Multiverse binds its own config key to the
                        // key Paper gave the world and records the startup name it was created under.
                        ImportWorldOptions options = ImportWorldOptions.worldName(world.getName())
                                .generator(generator)
                                .environment(world.getEnvironment())
                                .useSpawnAdjust(false);
                        return manager.importWorld(options).get();
                    });

            applyIrisWorldSettings(multiverseWorld, generator);
            manager.saveWorldsConfig().get();
        } catch (Throwable failure) {
            IrisLogging.warn("Multiverse registration failed for %s: %s; the world is unaffected.",
                    String.valueOf(configuredWorldName), describe(failure));
        }
    }

    /**
     * Re-imposes Iris' intended Multiverse state on every world Iris owns.
     * <p>
     * Iris registers worlds it creates, but worlds reconciled out of bukkit.yml at boot are registered
     * by Multiverse's own auto-import instead, which defaults {@code auto-load} and {@code adjust-spawn}
     * on. Auto-load makes Multiverse try to load an Iris world every boot and log a SEVERE when Paper
     * has already loaded it, and adjust-spawn hands Multiverse the authority to relocate and persist a
     * spawn Iris chose.
     * <p>
     * The stored environment is corrected here too. Multiverse refuses to adopt a world whose live
     * environment differs from the one it stored, and an Iris world's live environment is not stable across a
     * restart: it is whatever the pack declares in the session the world was created or loaded in, and
     * {@code CUSTOM} once the server enumerates the level from {@code <levelRoot>/dimensions/iris/<key>} on
     * the next boot. A runtime {@code /mv load} therefore leaves Multiverse holding the pack's environment,
     * and this pass is what moves it back to the live one before adoption is attempted.
     * <p>
     * This is also where a world Iris already loaded is offered to Multiverse, which is what makes the
     * running world visible to every Multiverse command that needs a loaded one.
     *
     * @return the number of worlds whose Multiverse entry was corrected; adoption is reported separately
     * because it changes Multiverse's registry rather than the world's entry
     */
    public int reconcileOwnedWorlds() {
        WorldManager manager = worldManager();
        if (manager == null) {
            return 0;
        }
        Map<String, String> owned;
        String levelName;
        try {
            levelName = IrisWorldStorage.levelRoot().getName();
            owned = IrisWorlds.get().getWorlds();
        } catch (Throwable failure) {
            IrisLogging.warn("Could not read the Iris world registry for Multiverse reconciliation: %s",
                    describe(failure));
            return 0;
        }

        int corrected = 0;
        int adopted = 0;
        for (Map.Entry<String, String> entry : owned.entrySet()) {
            NamespacedKey worldKey;
            String worldName;
            try {
                worldKey = WorldIdentity.parse(entry.getKey());
                worldName = IrisWorldStorage.configuredWorldName(worldKey, levelName);
            } catch (Throwable unreadable) {
                // Not a world with a Bukkit startup name; Multiverse cannot be holding an entry for it.
                IrisLogging.debug("Skipping Iris world registry entry " + entry.getKey()
                        + " for Multiverse reconciliation: " + describe(unreadable));
                continue;
            }
            try {
                MultiverseWorld multiverseWorld = resolve(manager, worldName, levelName);
                if (multiverseWorld == null) {
                    continue;
                }
                warnOnStaleRecordedName(multiverseWorld, worldName);
                boolean changed = applyIrisWorldEnvironment(
                        multiverseWorld,
                        ownedWorldEnvironment(worldKey, entry.getValue()));
                changed = applyIrisWorldSettings(multiverseWorld, GENERATOR_PREFIX + entry.getValue()) || changed;
                if (changed) {
                    corrected++;
                }
                if (adoptLoadedWorld(manager, multiverseWorld, worldKey)) {
                    adopted++;
                }
            } catch (Throwable failure) {
                IrisLogging.warn("Could not correct the Multiverse entry for %s: %s",
                        worldName, describe(failure));
            }
        }
        if (corrected > 0) {
            try {
                manager.saveWorldsConfig().get();
            } catch (Throwable failure) {
                IrisLogging.warn("Could not save worlds.yml after Multiverse reconciliation: %s",
                        describe(failure));
            }
            IrisLogging.info("Corrected %d Multiverse world entr%s.", corrected, corrected == 1 ? "y" : "ies");
        }
        if (adopted > 0) {
            IrisLogging.notice("Adopted %d live Iris world%s into Multiverse.", adopted, adopted == 1 ? "" : "s");
        }
        return corrected;
    }

    /**
     * Points Multiverse's stored environment at the one Iris is about to create the world with, so the
     * adoption Multiverse runs from its own {@code WorldLoadEvent} handler does not fail on the mismatch.
     * <p>
     * Multiverse compares the stored environment to the live world's before it will take a world, and it
     * rewrites the stored value from the live world every time it binds one. An Iris world reports the
     * environment its pack declares in the session it was created and {@code CUSTOM} on every later boot, so
     * the stored value is only ever right for one of the two - and it is this call, plus the boot-time pass
     * in {@link #reconcileOwnedWorlds()}, that moves it between them.
     */
    public void prepareOwnedWorldLoad(String configuredWorldName) {
        try {
            WorldManager manager = worldManager();
            if (manager == null) {
                return;
            }
            String worldName = requireWorldName(configuredWorldName);
            String levelName = IrisWorldStorage.levelRoot().getName();
            NamespacedKey worldKey = IrisWorldStorage.managedKeyFromName(worldName, levelName);
            MultiverseWorld multiverseWorld = resolve(manager, worldName, levelName);
            if (multiverseWorld == null) {
                return;
            }
            if (applyIrisWorldEnvironment(
                    multiverseWorld,
                    ownedWorldEnvironment(worldKey, ownedWorldPack(worldKey)))) {
                manager.saveWorldsConfig().get();
            }
        } catch (Throwable failure) {
            IrisLogging.warn("Could not prepare the Multiverse entry for %s: %s",
                    String.valueOf(configuredWorldName), describe(failure));
        }
    }

    /**
     * Offers a world Iris has just loaded to Multiverse, importing it when Multiverse has no entry at all.
     *
     * @return true when Multiverse now tracks the world as loaded
     */
    public boolean adoptOwnedWorld(String configuredWorldName) {
        try {
            WorldManager manager = worldManager();
            if (manager == null) {
                return false;
            }
            String worldName = requireWorldName(configuredWorldName);
            String levelName = IrisWorldStorage.levelRoot().getName();
            NamespacedKey worldKey = IrisWorldStorage.managedKeyFromName(worldName, levelName);
            World live = WorldIdentity.resolve(worldKey).orElse(null);
            if (live == null) {
                return false;
            }
            String pack = ownedWorldPack(worldKey);
            MultiverseWorld multiverseWorld = resolve(manager, worldName, levelName);
            if (multiverseWorld == null) {
                if (pack == null) {
                    return false;
                }
                updateWorld(live, worldName, pack);
                return true;
            }
            boolean changed = applyIrisWorldEnvironment(multiverseWorld, live.getEnvironment());
            changed = applyIrisWorldSettings(multiverseWorld, pack == null ? null : GENERATOR_PREFIX + pack)
                    || changed;
            boolean adopted = adoptLoadedWorld(manager, multiverseWorld, worldKey);
            if (changed || adopted) {
                manager.saveWorldsConfig().get();
            }
            return adopted;
        } catch (Throwable failure) {
            IrisLogging.warn("Could not hand %s to Multiverse: %s",
                    String.valueOf(configuredWorldName), describe(failure));
            return false;
        }
    }

    private static String ownedWorldPack(NamespacedKey worldKey) {
        try {
            return IrisWorlds.get().getWorlds().get(worldKey.toString());
        } catch (Throwable unreadable) {
            IrisLogging.reportError("Could not read the Iris world registry for " + worldKey
                    + "; Multiverse keeps whatever generator it already recorded.", unreadable);
            return null;
        }
    }

    /**
     * Binds a live Iris world into Multiverse's loaded registry.
     * <p>
     * Iris turns {@code auto-load} off and loads its worlds out of bukkit.yml before Multiverse enables, so
     * Multiverse never adopts them: {@code mv list} calls a running world UNLOADED and every command that
     * needs a loaded world - {@code mv info}, {@code mv gamerule} - refuses it.
     * <p>
     * Multiverse's {@code loadWorld} short-circuits to {@code Bukkit.getWorld(<recorded name>)} when that
     * world already exists and binds it without going near a WorldCreator. That short circuit is the whole
     * safety property here: without a live world under the name Multiverse recorded, the same call would
     * build one over Iris storage instead, so the world is looked up and identified first and adoption is
     * skipped whenever it is not the target.
     *
     * @return true when Multiverse took the world
     */
    boolean adoptLoadedWorld(WorldManager manager, MultiverseWorld multiverseWorld, NamespacedKey worldKey) {
        if (manager.isLoadedWorld(multiverseWorld)) {
            return false;
        }
        World live = Bukkit.getWorld(multiverseWorld.getName());
        if (live == null || !worldKey.equals(WorldIdentity.key(live))) {
            return false;
        }
        Attempt<LoadedMultiverseWorld, LoadFailureReason> adoption =
                manager.loadWorld(LoadWorldOptions.world(multiverseWorld));
        if (adoption.isFailure()) {
            IrisLogging.warn("Multiverse would not adopt live world %s: %s",
                    multiverseWorld.getName(), describeFailure(adoption));
            return false;
        }
        return true;
    }

    /**
     * The Bukkit environment an Iris-owned world is or will be live with, or null when it cannot be
     * determined.
     * <p>
     * A live world already carries it, and it is the value Multiverse compares its own against before it will
     * adopt the world, so it wins whenever there is one. With no live world the pack's dimension answers it -
     * the same {@code BukkitEnvironment.from(dimension.getEnvironment())} Iris' own load path passes to the
     * WorldCreator - so a world about to be loaded gets the environment it is about to have, not a guess at
     * what Multiverse wants to hear.
     */
    World.Environment ownedWorldEnvironment(NamespacedKey worldKey, String pack) {
        World live = WorldIdentity.resolve(worldKey).orElse(null);
        if (live != null) {
            return live.getEnvironment();
        }
        try {
            IrisDimension dimension = IrisWorlds.get().getDimension(worldKey.toString(), pack);
            return dimension == null ? null : BukkitEnvironment.from(dimension.getEnvironment());
        } catch (Throwable unreadable) {
            IrisLogging.reportError("Could not read the Iris dimension for " + worldKey
                    + "; Multiverse keeps whatever environment it already recorded.", unreadable);
            return null;
        }
    }

    /**
     * Corrects the environment Multiverse stored for an Iris world.
     * <p>
     * Multiverse compares the stored value to the live world's and refuses to adopt the world when they
     * differ, and it rewrites the stored value from the live world every time it binds one. An Iris world's
     * live environment flips between the pack's own and {@code CUSTOM} across a restart, so this is the only
     * thing that keeps the two in step; without it {@code mv list} reports a running world as unloaded and
     * every command that needs a loaded one refuses it.
     *
     * @return true when something was changed
     */
    boolean applyIrisWorldEnvironment(MultiverseWorld multiverseWorld, World.Environment environment) {
        if (environment == null || environment == multiverseWorld.getEnvironment()) {
            return false;
        }
        return setWorldConfigValue(multiverseWorld, "setEnvironment", World.Environment.class, environment);
    }

    /**
     * Re-imposes Iris ownership on one Multiverse entry. Iris drives loading through bukkit.yml and owns
     * the spawn, so Multiverse must neither load the world nor move its spawn.
     *
     * @return true when something was changed
     */
    boolean applyIrisWorldSettings(MultiverseWorld multiverseWorld, String generator) {
        boolean changed = false;
        if (multiverseWorld.isAutoLoad()) {
            multiverseWorld.setAutoLoad(false);
            changed = true;
        }
        if (multiverseWorld.getAdjustSpawn()) {
            multiverseWorld.setAdjustSpawn(false);
            changed = true;
        }
        if (generator != null && !generator.equals(multiverseWorld.getGenerator())) {
            changed = setWorldConfigValue(multiverseWorld, "setGenerator", String.class, generator) || changed;
        }
        return changed;
    }

    /**
     * Multiverse tracks the world name it saw at import and indexes its store by it. Iris cannot rewrite
     * that name without desynchronising Multiverse's own name index, so a mismatch is reported instead.
     */
    void warnOnStaleRecordedName(MultiverseWorld multiverseWorld, String configuredWorldName) {
        String recorded = multiverseWorld.getName();
        if (configuredWorldName.equals(recorded)) {
            return;
        }
        IrisLogging.warn("Multiverse records %s as %s; remove that worlds.yml entry so Iris can re-register it.",
                configuredWorldName, recorded);
    }

    /**
     * Reports whether a Bukkit world name names a persistent world Iris owns on disk. Never throws, so a
     * name Iris has no opinion about simply answers false.
     */
    public static boolean isIrisOwnedWorldName(String worldName, File levelRoot) {
        if (worldName == null || levelRoot == null) {
            return false;
        }
        String levelName = levelRoot.getName();
        NamespacedKey key;
        try {
            key = IrisWorldStorage.managedKeyFromName(worldName.trim(), levelName);
        } catch (RuntimeException notAnIrisName) {
            return false;
        }
        return IrisWorldStorage.isExistingManagedDimensionRoot(levelRoot, key);
    }

    private boolean remove(WorldManager manager, MultiverseWorld multiverseWorld, String worldName) {
        Attempt<String, RemoveFailureReason> removal = manager.removeWorld(removalOptions(multiverseWorld));
        if (removal.isFailure()) {
            throw new IllegalStateException("Multiverse refused to remove world \"" + worldName + "\": "
                    + describeFailure(removal));
        }
        manager.saveWorldsConfig().get();
        return true;
    }

    /**
     * Removal options for one Multiverse world.
     * <p>
     * Iris evacuates, unloads and closes a world before it unregisters it, but Multiverse still holds it in
     * its loaded registry and its unload-before-remove dereferences the Bukkit world that is already gone,
     * failing the whole removal. Multiverse only owns the unload while the Bukkit world is genuinely live.
     */
    static RemoveWorldOptions removalOptions(MultiverseWorld multiverseWorld) {
        RemoveWorldOptions options = RemoveWorldOptions.world(multiverseWorld);
        if (hasLiveBukkitWorld(multiverseWorld)) {
            return options;
        }
        return options.unloadBukkitWorld(false).saveBukkitWorld(false);
    }

    private static boolean hasLiveBukkitWorld(MultiverseWorld multiverseWorld) {
        LoadedMultiverseWorld loaded = multiverseWorld.asLoadedWorld().getOrNull();
        return loaded != null && loaded.getBukkitWorld().getOrNull() != null;
    }

    MultiverseWorld resolve(WorldManager manager, String worldName) {
        return resolve(manager, worldName, IrisWorldStorage.levelRoot().getName());
    }

    /**
     * The Multiverse entry for one Iris world, under whichever of its names Multiverse currently holds.
     * <p>
     * A name other than the one asked for only identifies the same world when Multiverse keys its entry by
     * the Iris world key, so an entry sitting on the runtime name under some other key is never returned:
     * a removal must not delete a world that is not its target.
     */
    MultiverseWorld resolve(WorldManager manager, String worldName, String levelName) {
        NamespacedKey expectedKey = managedKeyOrNull(worldName, levelName);
        for (String candidate : lookupNames(worldName, levelName)) {
            MultiverseWorld multiverseWorld = manager.getWorld(candidate).getOrElse((MultiverseWorld) null);
            if (multiverseWorld == null) {
                continue;
            }
            if (candidate.equals(worldName) || expectedKey != null && expectedKey.equals(multiverseWorld.getKey())) {
                return multiverseWorld;
            }
        }
        return null;
    }

    /**
     * Every name one Iris world can be registered under in Multiverse: the Bukkit startup name Iris
     * registers, the Multiverse config key, and the {@code <namespace>_<key>} runtime name a world gets
     * when Multiverse re-creates it from a keyed WorldCreator. Without all three an entry Multiverse
     * reloaded under a different form can never be removed.
     */
    static List<String> lookupNames(String worldName, String levelName) {
        List<String> candidates = new ArrayList<>(3);
        candidates.add(worldName);
        NamespacedKey key = managedKeyOrNull(worldName, levelName);
        if (key != null && IrisWorldStorage.configuredWorldName(key, levelName).equals(worldName)) {
            candidates.add(key.toString());
            candidates.add(key.getNamespace() + "_" + key.getKey());
        }
        return List.copyOf(candidates);
    }

    private static NamespacedKey managedKeyOrNull(String worldName, String levelName) {
        try {
            return IrisWorldStorage.managedKeyFromName(worldName, levelName);
        } catch (IllegalArgumentException notAnIrisName) {
            // Not an Iris startup name; the plain name is the only identity Multiverse can have.
            return null;
        }
    }

    /**
     * {@code WorldConfig} and its setters are package-private in Multiverse, so its stored values can only
     * be corrected reflectively. A Multiverse release that renames either must degrade to leaving the value
     * alone rather than taking down whatever asked for the write.
     */
    private static boolean setWorldConfigValue(
            MultiverseWorld world,
            String setter,
            Class<?> parameterType,
            Object value
    ) {
        if (worldConfigUnwritable) {
            return false;
        }
        try {
            Field field = MultiverseWorld.class.getDeclaredField("worldConfig");
            field.setAccessible(true);

            Object config = field.get(world);
            Method method = config.getClass().getDeclaredMethod(setter, parameterType);
            method.setAccessible(true);
            method.invoke(config, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | Error failure) {
            worldConfigUnwritable = true;
            // A probe on a private Multiverse field. It is latched, the fallback is Multiverse's own
            // handling of the setting, and nothing an operator can do changes the answer.
            IrisLogging.info("Multiverse WorldConfig.%s is not reachable; Iris world settings are left to Multiverse.",
                    setter);
            return false;
        }
    }

    /**
     * A Multiverse failure reason as text. {@code getFailureMessage} is a Multiverse {@code Message}, whose
     * rendering goes through a locale manager that is not installed in every teardown order, so the reason
     * name is the fallback rather than an object identity string.
     */
    private static String describeFailure(Attempt<?, ? extends FailureReason> attempt) {
        Message message = attempt.getFailureMessage();
        if (message != null) {
            try {
                String formatted = message.formatted();
                if (formatted != null && !formatted.isBlank()) {
                    return formatted;
                }
            } catch (RuntimeException | Error unrenderable) {
                // Fall through to the reason name.
            }
        }
        FailureReason reason = attempt.getFailureReason();
        return reason == null ? "no reason given" : reason.toString();
    }

    private static String requireWorldName(String worldName) {
        String name = Objects.requireNonNull(worldName, "worldName").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty.");
        }
        return name;
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    /**
     * The world manager, or null when Multiverse cannot serve one. Plugin enablement alone is not enough:
     * the API singleton is installed during Multiverse's own enable, and Iris is asked for generators
     * before that finishes.
     */
    WorldManager worldManager() {
        if (!isActive()) {
            return null;
        }
        try {
            MultiverseCoreApi api = MultiverseCoreApi.get();
            return api.getWorldManager();
        } catch (RuntimeException | Error failure) {
            IrisLogging.debug("Multiverse world manager is unavailable: " + describe(failure));
            return null;
        }
    }

    public boolean isActive() {
        if (!Bukkit.getPluginManager().isPluginEnabled(MULTIVERSE_PLUGIN)) {
            return false;
        }
        try {
            return MultiverseCoreApi.isLoaded();
        } catch (Error missingApi) {
            // Multiverse older than 5.1 has no readiness probe; enablement is the only signal there is.
            return true;
        }
    }
}
