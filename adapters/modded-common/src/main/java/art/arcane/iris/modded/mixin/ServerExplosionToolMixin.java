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

import art.arcane.iris.modded.toolworld.ToolWorldGuard;
import com.volmit.tooling.world.BlockChange.Cause;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An explosion in a tool world puts each block it would destroy to the tool as an explosion change. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionToolMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "explode", at = @At("HEAD"))
    private void iris$beginExplosion(CallbackInfoReturnable<Integer> result) {
        ToolWorldGuard.enter(level, Cause.EXPLOSION, null, false);
    }

    @Inject(method = "explode", at = @At("RETURN"))
    private void iris$endExplosion(CallbackInfoReturnable<Integer> result) {
        ToolWorldGuard.exit(level);
    }
}
