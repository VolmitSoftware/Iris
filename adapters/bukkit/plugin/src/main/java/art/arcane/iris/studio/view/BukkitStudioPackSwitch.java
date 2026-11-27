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

package art.arcane.iris.studio.view;

import art.arcane.iris.Iris;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.studio.bridge.StudioBridge;
import art.arcane.iris.studio.bridge.StudioBridges;
import art.arcane.iris.studio.bridge.StudioPack;
import art.arcane.iris.studio.bridge.StudioPackSwitch;
import art.arcane.iris.studio.object.StudioOpenCoordinator;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Switches the studio between the packs in the workspace by opening a studio world for the chosen one. */
public final class BukkitStudioPackSwitch implements StudioPackSwitch {
    /** The seed {@code /iris studio open} opens with unless told otherwise. */
    private static final long STUDIO_SEED = 1337L;

    public static void install() {
        StudioBridges.usePackSwitch(new BukkitStudioPackSwitch());
    }

    @Override
    public List<StudioPack> packs() {
        return StudioPack.inWorkspace(Iris.service(StudioSVC.class).getWorkspaceFolder());
    }

    @Override
    public CompletableFuture<StudioBridge> open(String pack, UUID openerId) {
        CompletableFuture<StudioOpenCoordinator.StudioOpenResult> opening = new CompletableFuture<>();
        J.s(() -> Iris.service(StudioSVC.class)
                .openTracked(senderFor(openerId), STUDIO_SEED, pack, StudioOpenCoordinator.StudioOpenKind.STANDARD, () -> {
                }, world -> {
                })
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        opening.completeExceptionally(failure);
                        return;
                    }
                    opening.complete(result);
                }));
        return opening.thenApply(result -> bridgeTo(result.world(), openerId));
    }

    /** The opener, so the new studio moves them as {@code /iris studio open} would; the console when they are gone. */
    private static VolmitSender senderFor(UUID openerId) {
        Player opener = openerId == null ? null : Bukkit.getPlayer(openerId);
        return new VolmitSender(opener == null ? Bukkit.getConsoleSender() : opener);
    }

    private static StudioBridge bridgeTo(World world, UUID openerId) {
        try {
            return StudioBridges.open(world.getName(), IrisToolbelt.access(world)::getEngine, openerId);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
