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

package art.arcane.iris.core.service;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.commands.CommandIris;
import art.arcane.iris.core.tooling.ToolingCommands;
import art.arcane.iris.core.tooling.ToolingSender;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.util.common.director.DirectorContext;
import art.arcane.iris.util.common.director.DirectorContextHandler;
import art.arcane.iris.util.common.director.DirectorSystem;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.IrisService;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorInvocationHook;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu.DirectorHelpPage;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.IrisMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
public class CommandSVC implements IrisService, CommandExecutor, TabCompleter, DirectorInvocationHook {
    private static final String ROOT_COMMAND = "iris";
    static final String ROOT_PERMISSION = "iris.all";

    private final transient ToolingCommands tooling = ToolingCommands.forBoundPlatform();
    private final transient AtomicCache<DirectorRuntimeEngine> directorCache = new AtomicCache<>();
    private final transient ThreadLocal<CommandSender> dispatchSenders = new ThreadLocal<>();

    @Override
    public void onEnable() {
        PluginCommand command = findBukkitCommand();
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        } else {
            registerPaperCommand();
        }
        J.a(this::getDirector);
    }

    @Override
    public void onDisable() {

    }

    public DirectorRuntimeEngine getDirector() {
        return directorCache.aquireNastyPrint(() -> DirectorEngineFactory.create(
                new CommandIris(),
                DirectorEngineOptions.builder()
                        .contexts(buildDirectorContexts())
                        .dispatcher(this::dispatchDirector)
                        .invocationHook(this)
                        .legacyHandlers(DirectorSystem.handlers)
                        .textResolver(IrisLanguage.directorResolver())
                        .build()
        ));
    }

    private DirectorContextRegistry buildDirectorContexts() {
        DirectorContextRegistry contexts = new DirectorContextRegistry();

        for (Map.Entry<Class<?>, DirectorContextHandler<?>> entry : DirectorContextHandler.contextHandlers.entrySet()) {
            registerContextHandler(contexts, entry.getKey(), entry.getValue());
        }

        return contexts;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerContextHandler(DirectorContextRegistry contexts, Class<?> type, DirectorContextHandler<?> handler) {
        contexts.register((Class) type, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender) {
                return ((DirectorContextHandler) handler).handle(new VolmitSender(sender.sender()));
            }

            return null;
        });
    }

    private void dispatchDirector(DirectorExecutionMode mode, Runnable runnable) {
        if (mode != DirectorExecutionMode.SYNC) {
            runnable.run();
            return;
        }
        CommandSender sender = dispatchSenders.get();
        if (sender instanceof Player player) {
            if (!J.runEntity(player, runnable)) {
                throw new IllegalStateException("Failed to schedule player command on its entity thread");
            }
            return;
        }
        J.s(runnable);
    }

    @Override
    public void beforeInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
        if (invocation.getSender() instanceof BukkitDirectorSender sender) {
            DirectorContext.touch(new VolmitSender(sender.sender()));
        }
    }

    @Override
    public void afterInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
        DirectorContext.remove();
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
            return List.of();
        }

        return tabCompleteRoot(sender, alias, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
            return false;
        }

        executeRoot(sender, label, args);
        return true;
    }

    public void executeRoot(CommandSender sender, String label, String[] args) {
        ToolingSender toolingSender = new BukkitToolingSender(sender);
        if (!sender.hasPermission(ROOT_PERMISSION)) {
            J.aBukkit(() -> denyUnlessOpenToEveryone(sender, toolingSender, args));
            return;
        }

        J.aBukkit(() -> executeCommand(sender, toolingSender, label, args));
    }

    private void denyUnlessOpenToEveryone(CommandSender sender, ToolingSender toolingSender, String[] args) {
        if (tooling.executeIfOpenToEveryone(toolingSender, Arrays.asList(args))) {
            return;
        }
        ComponentMessenger.sendSection(sender, IrisLanguage.text(
                IrisMessages.COMMAND_PERMISSION_DENIED,
                MessageArgument.trusted("permission", ROOT_PERMISSION)
        ));
    }

    public List<String> tabCompleteRoot(CommandSender sender, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>(runDirectorTab(sender, alias, args));
        for (String toolingSuggestion : tooling.complete(new BukkitToolingSender(sender), Arrays.asList(args))) {
            if (!suggestions.contains(toolingSuggestion)) {
                suggestions.add(toolingSuggestion);
            }
        }
        if (sender instanceof Player player && IrisSettings.get().getGeneral().isCommandSounds()) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, RNG.r.f(0.125f, 1.95f));
        }
        return suggestions;
    }

    private PluginCommand findBukkitCommand() {
        try {
            return Iris.instance.getCommand(ROOT_COMMAND);
        } catch (UnsupportedOperationException ignored) {
            return null;
        }
    }

    private void registerPaperCommand() {
        try {
            Class<?> registrarType = Class.forName(
                    "art.arcane.iris.core.commands.PaperCommandRegistrar",
                    true,
                    getClass().getClassLoader()
            );
            Method register = registrarType.getDeclaredMethod("register", Iris.class, CommandSVC.class);
            register.invoke(null, Iris.instance, this);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Paper command registration failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Paper command registrar is unavailable", e);
        } catch (LinkageError e) {
            throw new IllegalStateException("Paper command APIs are unavailable", e);
        }
    }

    private void executeCommand(CommandSender sender, ToolingSender toolingSender, String label, String[] args) {
        if (sendHelpIfRequested(sender, args)) {
            playSuccessSound(sender);
            return;
        }

        DirectorExecutionResult result = runDirector(sender, label, args);

        if (result.isSuccess()) {
            playSuccessSound(sender);
            return;
        }

        if (!result.isHandled() && tooling.execute(toolingSender, Arrays.asList(args))) {
            playSuccessSound(sender);
            return;
        }

        playFailureSound(sender);
        if (result.getMessage() == null || result.getMessage().trim().isEmpty()) {
            new VolmitSender(sender).sendMessage(C.RED + IrisLanguage.text(IrisMessages.COMMAND_UNKNOWN));
        }
    }

    private boolean sendHelpIfRequested(CommandSender sender, String[] args) {
        Optional<DirectorHelpPage> request = DirectorMiniMenu.resolveHelp(getDirector(), Arrays.asList(args));
        if (request.isEmpty()) {
            return false;
        }

        DirectorMiniMenu.deliver(
                sender,
                request.get(),
                DirectorMiniMenu.Theme.irisGreen(),
                IrisLanguage.directorResolver()
        );
        return true;
    }

    private DirectorExecutionResult runDirector(CommandSender sender, String label, String[] args) {
        dispatchSenders.set(sender);
        try {
            return getDirector().execute(new DirectorInvocation(new BukkitDirectorSender(sender), label, Arrays.asList(args)));
        } catch (Throwable e) {
            Iris.warn("Director command execution failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return DirectorExecutionResult.notHandled();
        } finally {
            dispatchSenders.remove();
        }
    }

    private List<String> runDirectorTab(CommandSender sender, String alias, String[] args) {
        DirectorContext.touch(new VolmitSender(sender));
        try {
            return getDirector().tabComplete(new DirectorInvocation(new BukkitDirectorSender(sender), alias, Arrays.asList(args)));
        } catch (Throwable e) {
            Iris.warn("Director tab completion failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return List.of();
        } finally {
            DirectorContext.remove();
        }
    }

    private void playFailureSound(CommandSender sender) {
        if (!IrisSettings.get().getGeneral().isCommandSounds()) {
            return;
        }

        if (sender instanceof Player player) {
            J.runEntity(player, () -> playSounds(player, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.77f, 0.25f,
                    Sound.BLOCK_BEACON_DEACTIVATE, 0.2f, 0.45f));
        }
    }

    private void playSuccessSound(CommandSender sender) {
        if (!IrisSettings.get().getGeneral().isCommandSounds()) {
            return;
        }

        if (sender instanceof Player player) {
            J.runEntity(player, () -> playSounds(player, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.77f, 1.65f,
                    Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.125f, 2.99f));
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean SOUND_LOGGED = new java.util.concurrent.atomic.AtomicBoolean(false);

    private void playSounds(Player player, Sound a, float av, float ap, Sound b, float bv, float bp) {
        try {
            player.playSound(player.getLocation(), a, av, ap);
            player.playSound(player.getLocation(), b, bv, bp);
        } catch (Throwable e) {
            if (SOUND_LOGGED.compareAndSet(false, true)) {
                try {
                    java.io.File f = Iris.instance.getDataFile("adventure-debug.txt");
                    java.nio.file.Files.writeString(f.toPath(), "SOUND FAIL: " + e.getClass().getName() + ": " + e.getMessage() + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private record BukkitDirectorSender(CommandSender sender) implements DirectorSender {
        @Override
        public String getName() {
            return sender.getName();
        }

        @Override
        public boolean isPlayer() {
            return sender instanceof Player;
        }

        @Override
        public void sendMessage(String message) {
            if (message != null && !message.trim().isEmpty()) {
                ComponentMessenger.sendLiteral(sender, message);
            }
        }
    }
}
