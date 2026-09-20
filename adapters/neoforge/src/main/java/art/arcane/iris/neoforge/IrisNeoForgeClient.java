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

package art.arcane.iris.neoforge;

import art.arcane.iris.client.IrisClient;
import art.arcane.volmlib.nativelib.minecraft26_2.neoforge.NativeNeoForgeClientHooks;
import art.arcane.iris.client.IrisClientHud;
import art.arcane.iris.modded.ModdedProtocolDefinition;
import art.arcane.volmlib.nativelib.minecraft26_2.neoforge.NativeNeoForgeProtocolNetworking;
import net.neoforged.bus.api.IEventBus;

public final class IrisNeoForgeClient {
    private IrisNeoForgeClient() {
    }

    public static void init(IEventBus modBus) {
        IrisClient.bindSender(frame -> NativeNeoForgeProtocolNetworking.sendClient(ModdedProtocolDefinition.PROTOCOL, frame));
        NativeNeoForgeClientHooks.install(modBus, IrisClientHud.binding(), IrisClient::onWorldJoin, IrisClient::onDisconnect);
    }
}
