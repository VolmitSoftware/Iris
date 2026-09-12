/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedIrisLog;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.function.Predicate;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
final class ModdedDeveloperCommands {
    private static final Predicate<CommandSourceStack> GATE = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private ModdedDeveloperCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return Commands.literal(name).requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), "developer")))
                .then(Commands.literal("network")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> network(context.getSource()))))
                .then(Commands.literal("ip")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> network(context.getSource()))));
    }

    private static int network(CommandSourceStack source) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                ModdedCommandFeedback.ok(source, networkInterface.getDisplayName());
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    ModdedCommandFeedback.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DEVELOPER_COMMANDS_MESSAGE, MessageArgument.untrusted("value", address.getHostAddress())));
                }
            }
            return 1;
        } catch (SocketException error) {
            ModdedIrisLog.error("Iris developer network dump failed", error);
            ModdedCommandFeedback.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DEVELOPER_COMMANDS_NETWORK_SCAN_FAILED, MessageArgument.untrusted("value", error.getClass().getSimpleName())));
            return 0;
        }
    }
}
