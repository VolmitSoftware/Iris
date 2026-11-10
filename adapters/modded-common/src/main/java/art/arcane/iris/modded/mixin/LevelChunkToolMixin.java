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

import art.arcane.iris.modded.toolworld.ModdedToolWorldService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A chunk that becomes live in a tool world is told to the tool. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkToolMixin {
    @Inject(method = "setLoaded", at = @At("RETURN"))
    private void iris$tellAboutLoadedChunk(boolean loaded, CallbackInfo info) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (loaded && chunk.getLevel() instanceof ServerLevel level) {
            ModdedToolWorldService.chunkLoaded(level, chunk.getPos().x(), chunk.getPos().z());
        }
    }
}
