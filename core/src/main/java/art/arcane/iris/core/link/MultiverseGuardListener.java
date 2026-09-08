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
import art.arcane.iris.core.lifecycle.ManagedWorldLoader;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.mvplugins.multiverse.core.event.world.MVWorldDeleteEvent;
import org.mvplugins.multiverse.core.event.world.MVWorldImportedEvent;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/**
 * Keeps Multiverse's destructive world commands away from Iris storage.
 * <p>
 * An Iris world folder is not a vanilla world folder: {@code <level>/dimensions/iris/<key>} also holds
 * {@code iris/pack}, the frozen pack snapshot the world is generated from, and {@code iris/engine-data}.
 * Multiverse deletes a world by recursively deleting its Bukkit world folder, and its {@code --keep-files}
 * only matches file basenames, so a pack directory can never survive. The delete leaves the world, the
 * pack and the Multiverse entry gone while bukkit.yml and Iris' worlds.json still point at them, and a
 * half-deleted tree reads back as an owned-but-broken world.
 * <p>
 * This listener is only registered while Multiverse is enabled. Every handler here names a Multiverse
 * type, so registering it without Multiverse on the classpath would fail at handler resolution.
 */
public final class MultiverseGuardListener implements Listener {
    private static final String IRIS_REMOVE_HINT = "Use /iris remove world=%s delete=true instead.";
    private static final String MULTIVERSE_LOAD_PERMISSION = "multiverse.core.load";
    private final MultiverseCoreLink link;
    /**
     * Multiverse world events carry no command sender, and its own cancellation message is the generic
     * failure string, so the sender who typed the command has to be remembered across the dispatch. The
     * capture is consumed on first read so a later API-driven delete cannot message an unrelated player.
     */
    private volatile WeakReference<CommandSender> multiverseCommandSender;

    public MultiverseGuardListener(MultiverseCoreLink link) {
        this.link = Objects.requireNonNull(link, "link");
    }

    /**
     * Cancels {@code mv delete} and {@code mv regen} for Iris worlds.
     * <p>
     * Both commands funnel through {@code WorldManager#doDeleteWorld}, which fires this event after
     * resolving the world folder but before it unloads the world or touches disk, so a cancel here is
     * free of side effects. Regen is refused with delete because the event cannot tell them apart, and
     * because a regen Iris did not drive would have Multiverse re-create the world through its own
     * {@code CreateWorldOptions} rather than through Iris storage.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMultiverseWorldDelete(MVWorldDeleteEvent event) {
        MultiverseWorld multiverseWorld = event.getWorld();
        String worldName = multiverseWorld.getName();
        if (!isIrisWorld(multiverseWorld)) {
            return;
        }
        event.setCancelled(true);
        String irisName = irisCommandName(multiverseWorld, worldName);
        refuse(consumeCommandSender(),
                "Multiverse cannot delete or regenerate Iris world " + worldName
                        + "; it would destroy the world-local pack snapshot.",
                String.format(IRIS_REMOVE_HINT, irisName));
    }

    /**
     * Multiverse auto-imports every world it finds that is not already in worlds.yml, using its own
     * defaults, so an Iris world reconciled out of bukkit.yml arrives with {@code auto-load} and
     * {@code adjust-spawn} on. Iris drives its own loading and owns the spawn, so both are turned back off.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMultiverseWorldImported(MVWorldImportedEvent event) {
        MultiverseWorld multiverseWorld = event.getWorld();
        if (!isIrisWorld(multiverseWorld)) {
            return;
        }
        try {
            if (!link.applyIrisWorldSettings(multiverseWorld, null)) {
                return;
            }
            WorldManager manager = link.worldManager();
            if (manager != null) {
                manager.saveWorldsConfig().get();
            }
            IrisLogging.debug("Reset Multiverse auto-load and adjust-spawn for " + multiverseWorld.getName());
        } catch (Throwable failure) {
            IrisLogging.warn("Could not apply Iris settings to the Multiverse import of %s: %s",
                    multiverseWorld.getName(), failure.getClass().getSimpleName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (inspectCommand(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (inspectCommand(event.getSender(), event.getCommand())) {
            event.setCancelled(true);
        }
    }

    /**
     * Records the sender of a Multiverse command and vetoes a clone that reads from, or writes over, Iris
     * storage.
     * <p>
     * Multiverse fires no cancellable event for a clone: it copies the source world folder first and only
     * then imports the copy, and the import of an Iris copy always fails. That leaves an orphaned copy of
     * the world - frozen pack included - under {@code <level>/dimensions/minecraft/<name>}, which
     * Multiverse then advertises as importable, so a later plain {@code mv import} would build a vanilla
     * world on top of Iris region files. The command is the only place left to stop it.
     * <p>
     * The destination is checked as well. Multiverse decides a name is free with its own
     * {@code hasWorldFolder} check, which resolves the vanilla {@code <container>/<name>} path and never
     * looks in the Iris dimension namespace, so a clone can be aimed straight at a live Iris world's slot.
     *
     * @return true when the command must be cancelled
     */
    private boolean inspectCommand(CommandSender sender, String commandLine) {
        if (multiverseCommandRoot(commandLine) == null) {
            return false;
        }
        multiverseCommandSender = new WeakReference<>(sender);
        String load = multiverseLoad(commandLine);
        if (load != null) {
            return interceptLoad(sender, load);
        }
        CloneCommand clone = multiverseClone(commandLine);
        if (clone == null) {
            return false;
        }
        if (isIrisWorld(clone.source())) {
            refuse(sender,
                    "Multiverse cannot clone Iris world " + clone.source()
                            + "; its pack snapshot is world-local and the copy is not a loadable world.",
                    "Create another Iris world with /iris create <name> type=<pack> instead.");
            return true;
        }
        if (clone.destination() != null && isIrisWorld(clone.destination())) {
            refuse(sender,
                    "Multiverse cannot clone onto Iris world " + clone.destination()
                            + "; that name already owns Iris world storage.",
                    "Choose another destination name, or free it with /iris remove world="
                            + clone.destination() + " delete=true.");
            return true;
        }
        return false;
    }

    /**
     * Takes over {@code /mv load} for an Iris world Iris can load itself.
     * <p>
     * Multiverse only adopts a world it finds already in Bukkit; otherwise it rebuilds one from a
     * WorldCreator carrying the environment it stored. Neither half of that works for an Iris world. The
     * stored environment is written from the live world every time Multiverse binds it, and a custom
     * dimension reports {@code NORMAL} in the session it was created and {@code CUSTOM} on every later boot,
     * so {@code CraftServer.createWorld} refuses it with "Illegal dimension (CUSTOM)". Writing {@code normal}
     * instead only trades that for the other failure: Multiverse would build a vanilla world beside the level
     * root under the Iris world's name, and the next boot's adoption would fail on the mismatch.
     * <p>
     * So the load is handed to Iris, which creates the world from a keyed creator at
     * {@code <levelRoot>/dimensions/iris/<key>} with the pack's own environment, and the result is offered
     * back to Multiverse. With no loader installed the command is refused rather than passed through, because
     * passing it through is the failure this exists to prevent.
     *
     * @return true when the command must be cancelled
     */
    private boolean interceptLoad(CommandSender sender, String worldNameOrAlias) {
        String worldName = irisWorldName(worldNameOrAlias);
        if (worldName == null || Bukkit.getWorld(worldName) != null) {
            // A live world is adopted by Multiverse without a WorldCreator; that path already works.
            return false;
        }
        if (sender != null && !sender.hasPermission(MULTIVERSE_LOAD_PERMISSION)) {
            // Taking the command over must never load a world for someone Multiverse would have refused.
            return false;
        }
        ManagedWorldLoader loader = IrisServices.getOrNull(ManagedWorldLoader.class);
        if (loader == null) {
            refuse(sender,
                    "Iris owns loading for " + worldName + "; Multiverse would build a vanilla world in its place.",
                    "Load it with /iris load " + worldName + ", or restart the server.");
            return true;
        }
        link.prepareOwnedWorldLoad(worldName);
        IrisLogging.info("Multiverse load of %s handed to Iris.", worldName);
        WeakReference<CommandSender> target = new WeakReference<>(sender);
        try {
            // The load settles off the main thread, and handing the result to Multiverse fires a Bukkit
            // event, so the whole tail runs back on the main thread exactly as /iris load's does.
            loader.load(worldName).whenComplete((result, failure) ->
                    J.s(() -> settleLoad(target.get(), worldName, result, failure)));
        } catch (Throwable failure) {
            refuse(sender, "Iris could not load " + worldName + ": " + describe(failure),
                    "Load it with /iris load " + worldName + ", or restart the server.");
        }
        return true;
    }

    void settleLoad(
            CommandSender sender,
            String worldName,
            ManagedWorldLoader.ManagedWorldLoad result,
            Throwable failure
    ) {
        if (failure != null || result == null || !result.loaded()) {
            String detail = failure != null
                    ? describe(failure)
                    : result == null ? "the load completed without a result" : result.message();
            refuse(sender, "Iris could not load " + worldName + ": " + detail,
                    "Check the Iris log, then retry with /iris load " + worldName + ".");
            return;
        }
        boolean adopted = link.adoptOwnedWorld(worldName);
        IrisLogging.info("Iris loaded %s; multiverse adopted=%s.", worldName, adopted);
        announce(sender, "Iris loaded " + worldName + ".");
    }

    /**
     * The Bukkit startup name of the Iris world a Multiverse world argument names, or null when it names no
     * Iris world. Multiverse accepts its own alias, so the argument is resolved through Multiverse first and
     * the name Multiverse recorded is what Iris is asked for.
     */
    private String irisWorldName(String worldNameOrAlias) {
        if (isIrisOwnedName(worldNameOrAlias)) {
            return startupName(worldNameOrAlias);
        }
        WorldManager manager = link.worldManager();
        if (manager == null) {
            return null;
        }
        MultiverseWorld multiverseWorld = manager.getWorldByNameOrAlias(worldNameOrAlias)
                .getOrElse((MultiverseWorld) null);
        if (multiverseWorld == null) {
            return null;
        }
        String recorded = multiverseWorld.getName();
        return isIrisOwnedName(recorded) ? startupName(recorded) : null;
    }

    /**
     * The Bukkit startup name for an Iris world named in any of the forms Iris answers to - the startup name
     * itself, the bare key, or {@code iris:<key>} - so the live-world check and the loader both see the one
     * name the world is actually registered under.
     */
    private static String startupName(String worldName) {
        try {
            String levelName = IrisWorldStorage.levelRoot().getName();
            return IrisWorldStorage.configuredWorldName(
                    IrisWorldStorage.managedKeyFromName(worldName, levelName),
                    levelName);
        } catch (Throwable notAnIrisName) {
            return worldName;
        }
    }

    /**
     * The world argument of a Multiverse load command, or null when the line is not one. Syntax is
     * {@code /mv load <world> [flags]} with the legacy alias {@code /mvload}.
     */
    static String multiverseLoad(String commandLine) {
        String root = multiverseCommandRoot(commandLine);
        if (root == null) {
            return null;
        }
        String line = commandLine.trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        String[] parts = line.split("\\s+");
        if ("mv".equals(root)) {
            return parts.length >= 3 && "load".equalsIgnoreCase(parts[1]) ? worldArgument(parts, 2) : null;
        }
        if ("mvload".equals(root)) {
            return parts.length >= 2 ? worldArgument(parts, 1) : null;
        }
        return null;
    }

    /**
     * The Multiverse command root, or null when the line is not one. Multiverse registers {@code /mv} plus
     * per-command legacy aliases; anything Iris does not recognise is left alone, which is the behaviour a
     * server without this listener already has.
     */
    static String multiverseCommandRoot(String commandLine) {
        if (commandLine == null) {
            return null;
        }
        String line = commandLine.trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        int firstSpace = line.indexOf(' ');
        String root = (firstSpace < 0 ? line : line.substring(0, firstSpace)).toLowerCase(Locale.ENGLISH);
        // Bukkit always accepts the "plugin:command" form as well.
        int namespace = root.lastIndexOf(':');
        if (namespace >= 0) {
            root = root.substring(namespace + 1);
        }
        // Every Multiverse alias starts with "mv". Matching the prefix rather than an enumeration keeps a
        // new legacy alias from silently escaping the guard; a false match only stores a sender reference
        // that nothing but a Multiverse world event ever reads.
        return root.startsWith("mv") ? root : null;
    }

    /**
     * The source and destination world arguments of a Multiverse clone command, or null when the line is
     * not one. Syntax is {@code /mv clone <world> <new-world-name> [flags]} with the legacy aliases
     * {@code /mvcl} and {@code /mvclone}. The destination is null when the admin has not typed it yet.
     */
    static CloneCommand multiverseClone(String commandLine) {
        String root = multiverseCommandRoot(commandLine);
        if (root == null) {
            return null;
        }
        String line = commandLine.trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        String[] parts = line.split("\\s+");
        int worldIndex;
        if ("mv".equals(root)) {
            if (parts.length < 3 || !"clone".equalsIgnoreCase(parts[1])) {
                return null;
            }
            worldIndex = 2;
        } else if ("mvcl".equals(root) || "mvclone".equals(root)) {
            if (parts.length < 2) {
                return null;
            }
            worldIndex = 1;
        } else {
            return null;
        }
        String source = worldArgument(parts, worldIndex);
        if (source == null) {
            return null;
        }
        return new CloneCommand(source, worldArgument(parts, worldIndex + 1));
    }

    /**
     * A world-name argument, or null when it is absent or a flag. Multiverse quotes names containing
     * spaces; the guard only needs the leading token, so the opening quote is dropped and the rest kept.
     */
    private static String worldArgument(String[] parts, int index) {
        if (index >= parts.length) {
            return null;
        }
        String world = parts[index];
        if (world.startsWith("-")) {
            return null;
        }
        if (world.startsWith("\"")) {
            world = world.substring(1);
        }
        return world.isEmpty() ? null : world;
    }

    record CloneCommand(String source, String destination) {
        CloneCommand {
            Objects.requireNonNull(source, "source");
        }
    }

    private boolean isIrisWorld(MultiverseWorld multiverseWorld) {
        LoadedMultiverseWorld loaded = multiverseWorld.asLoadedWorld().getOrNull();
        World bukkitWorld = loaded == null ? null : loaded.getBukkitWorld().getOrNull();
        if (bukkitWorld != null && IrisToolbelt.isIrisWorld(bukkitWorld)) {
            return true;
        }
        return isIrisOwnedName(multiverseWorld.getName());
    }

    private boolean isIrisWorld(String worldNameOrAlias) {
        World live = Bukkit.getWorld(worldNameOrAlias);
        if (live != null && IrisToolbelt.isIrisWorld(live)) {
            return true;
        }
        WorldManager manager = link.worldManager();
        if (manager != null) {
            MultiverseWorld multiverseWorld = manager.getWorldByNameOrAlias(worldNameOrAlias)
                    .getOrElse((MultiverseWorld) null);
            if (multiverseWorld != null) {
                return isIrisWorld(multiverseWorld);
            }
        }
        return isIrisOwnedName(worldNameOrAlias);
    }

    private static boolean isIrisOwnedName(String worldName) {
        try {
            return MultiverseCoreLink.isIrisOwnedWorldName(worldName, IrisWorldStorage.levelRoot());
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /**
     * The name {@code /iris remove} accepts. Multiverse reports the name it recorded at import, which is
     * the Bukkit startup name for every world Iris registered.
     */
    private static String irisCommandName(MultiverseWorld multiverseWorld, String fallback) {
        LoadedMultiverseWorld loaded = multiverseWorld.asLoadedWorld().getOrNull();
        World bukkitWorld = loaded == null ? null : loaded.getBukkitWorld().getOrNull();
        return bukkitWorld == null ? fallback : bukkitWorld.getName();
    }

    private CommandSender consumeCommandSender() {
        WeakReference<CommandSender> captured = multiverseCommandSender;
        multiverseCommandSender = null;
        return captured == null ? null : captured.get();
    }

    /**
     * Multiverse reports a cancelled destructive command as its generic failure string, so the refusal has
     * to reach the sender from here or the admin only sees "something went wrong".
     */
    private static void refuse(CommandSender sender, String reason, String remedy) {
        // The refusal is the guard working, and the admin who typed the command is told directly. This is
        // the console's copy of the same sentence, not a warning about the state of the server.
        IrisLogging.info("%s %s", reason, remedy);
        announce(sender, reason, remedy);
    }

    private static void announce(CommandSender sender, String... lines) {
        if (sender == null || sender instanceof ConsoleCommandSender) {
            // The console already saw it through the Iris log.
            return;
        }
        try {
            VolmitSender target = new VolmitSender(sender);
            for (String line : lines) {
                target.sendMessage(line);
            }
        } catch (Throwable undeliverable) {
            IrisLogging.debug("Falling back to plain delivery for a Multiverse guard message: "
                    + describe(undeliverable));
            for (String line : lines) {
                ComponentMessenger.sendSection(sender, line);
            }
        }
    }

    private static String describe(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
