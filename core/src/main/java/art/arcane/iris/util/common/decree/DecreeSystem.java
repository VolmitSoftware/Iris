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

package art.arcane.iris.util.decree;

import art.arcane.volmlib.util.decree.DecreeSystemSupport;
import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.decree.virtual.VirtualDecreeCommand;
import art.arcane.iris.util.format.C;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.plugin.VolmitSender;
import art.arcane.iris.util.scheduling.J;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DecreeSystem extends CommandExecutor, TabCompleter {
    KList<DecreeParameterHandler<?>> handlers = Iris.initialize("art.arcane.iris.util.decree.handlers", null).convert((i) -> (DecreeParameterHandler<?>) i);

    static KList<String> enhanceArgs(String[] args) {
        return new KList<>(DecreeSystemSupport.enhanceArgs(args));
    }

    static KList<String> enhanceArgs(String[] args, boolean trim) {
        return new KList<>(DecreeSystemSupport.enhanceArgs(args, trim));
    }

    /**
     * Get the handler for the specified type
     *
     * @param type The type to handle
     * @return The corresponding {@link DecreeParameterHandler}, or null
     */
    static DecreeParameterHandler<?> getHandler(Class<?> type) {
        DecreeParameterHandler<?> handler = DecreeSystemSupport.getHandler(handlers, type, (h, t) -> h.supports(t));
        if (handler != null) {
            return handler;
        }

        Iris.error("Unhandled type in Decree Parameter: " + type.getName() + ". This is bad!");
        return null;
    }

    /**
     * The root class to start command searching from
     */
    VirtualDecreeCommand getRoot();

    default boolean call(VolmitSender sender, String[] args) {
        DecreeContext.touch(sender);
        try {
            return getRoot().invoke(sender, enhanceArgs(args));
        } finally {
            DecreeContext.remove();
        }
    }

    @Nullable
    @Override
    default List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        DecreeContext.touch(new VolmitSender(sender));
        try {
            KList<String> enhanced = new KList<>(args);
            KList<String> v = getRoot().tabComplete(enhanced, enhanced.toString(" "));
            v.removeDuplicates();

            if (sender instanceof Player) {
                if (IrisSettings.get().getGeneral().isCommandSounds()) {
                    ((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, RNG.r.f(0.125f, 1.95f));
                }
            }

            return v;
        } finally {
            DecreeContext.remove();
        }
    }

    @Override
    default boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("iris.all")) {
            sender.sendMessage("You lack the Permission 'iris.all'");
            return true;
        }

        J.aBukkit(() -> {
            var volmit = new VolmitSender(sender);
            if (!call(volmit, args)) {

                if (IrisSettings.get().getGeneral().isCommandSounds()) {
                    if (sender instanceof Player) {
                        ((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.77f, 0.25f);
                        ((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.2f, 0.45f);
                    }
                }

                volmit.sendMessage(C.RED + "Unknown Iris Command");
            } else {
                if (IrisSettings.get().getGeneral().isCommandSounds()) {
                    if (sender instanceof Player) {
                        ((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.77f, 1.65f);
                        ((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.125f, 2.99f);
                    }
                }
            }
        });
        return true;
    }
}
