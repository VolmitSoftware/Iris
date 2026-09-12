package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.platform.agent.Agent;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import java.util.function.LongPredicate;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.List;

final class NmsGenerationHooks {
    private NmsGenerationHooks() {
    }

    static void install() {
        ByteBuddy buddy = new ByteBuddy();
        for (Class<?> clazz : List.of(ChunkAccess.class, ProtoChunk.class)) {
            buddy.redefine(clazz)
                    .visit(Advice.to(ChunkAccessAdvice.class).on(ElementMatchers.isMethod().and(ElementMatchers.takesArguments(ShortList.class, int.class))))
                    .make()
                    .load(clazz.getClassLoader(), Agent.installed());
        }
        buddy.redefine(WorldGenRegion.class)
                .visit(Advice.to(WorldGenerationWriteAdvice.class).on(ElementMatchers.named("ensureCanWrite")
                        .and(ElementMatchers.takesArguments(BlockPos.class))))
                .make()
                .load(WorldGenRegion.class.getClassLoader(), Agent.installed());
    }

    private static class WorldGenerationWriteAdvice {
        @Advice.OnMethodExit
        static void exit(@Advice.This WorldGenRegion region, @Advice.Argument(0) BlockPos position,
                         @Advice.Return(readOnly = false) boolean allowed) {
            if (!allowed) {
                return;
            }
            ChunkGenerator generator = region.getLevel().getChunkSource().getGenerator();
            if (generator instanceof LongPredicate predicate
                    && generator.getClass().getName().equals("art.arcane.iris.platform.bukkit.nms.v26_2_R1.IrisChunkGenerator")
                    && region.getChunk(position.getX() >> 4, position.getZ() >> 4)
                    .getPersistedStatus().isOrAfter(ChunkStatus.FULL)) {
                allowed = predicate.test(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4));
            }
        }
    }

    private static class ChunkAccessAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean enter(@Advice.This ChunkAccess access, @Advice.Argument(1) int index) {
            return index >= access.getPostProcessing().length;
        }
    }
}
