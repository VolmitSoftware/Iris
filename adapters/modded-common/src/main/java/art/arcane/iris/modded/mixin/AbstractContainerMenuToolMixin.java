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

package art.arcane.iris.modded.mixin;

import art.arcane.iris.modded.toolworld.ToolWorldHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clicks in the inventory of a container block of a tool world are put to the tool; closing one tells it the contents may have changed. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuToolMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void iris$askAboutContainerClick(int slot, int button, ContainerInput input, Player player, CallbackInfo info) {
        if (player instanceof ServerPlayer serverPlayer
                && !ToolWorldHooks.containerClickAllowed(serverPlayer, (AbstractContainerMenu) (Object) this)) {
            info.cancel();
            ((AbstractContainerMenu) (Object) this).sendAllDataToRemote();
        }
    }

    @Inject(method = "removed", at = @At("RETURN"))
    private void iris$tellAboutClosedContainer(Player player, CallbackInfo info) {
        ToolWorldHooks.containerClosed(player, (AbstractContainerMenu) (Object) this);
    }
}
