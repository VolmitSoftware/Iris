package art.arcane.iris.modded.mixin;

import art.arcane.iris.world.history.NativeTerrainReceipt;
import art.arcane.iris.modded.ModdedNativeTerrainReceipts;
import art.arcane.iris.modded.NativeTerrainReceiptHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SerializableChunkData.class)
public abstract class SerializableTerrainReceiptMixin implements NativeTerrainReceiptHolder {
    @Unique
    private byte[] iris$naturalTerrain;
    @Unique
    private long iris$structureActivation;

    @Override
    public long iris$getStructureActivation() {
        return iris$structureActivation;
    }

    @Override
    public void iris$setStructureActivation(long activation) {
        if (activation < 0) {
            throw new IllegalArgumentException("Native structure activation cannot be negative");
        }
        iris$structureActivation = activation;
    }

    @Override
    public byte[] iris$getNaturalTerrain() {
        return iris$naturalTerrain == null ? null : iris$naturalTerrain.clone();
    }

    @Override
    public void iris$setNaturalTerrain(byte[] receipt) {
        iris$naturalTerrain = receipt == null ? null : receipt.clone();
    }

    @Inject(method = "parse", at = @At("RETURN"))
    private static void iris$parseTerrain(LevelHeightAccessor height, PalettedContainerFactory containers,
                                          CompoundTag tag, CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData parsed = callback.getReturnValue();
        if (parsed != null) {
            ((NativeTerrainReceiptHolder) (Object) parsed).iris$setStructureActivation(
                    tag.getLongOr(NativeTerrainReceipt.STRUCTURE_ACTIVATION_KEY, 0));
            ((NativeTerrainReceiptHolder) (Object) parsed).iris$setNaturalTerrain(
                    tag.getByteArray(NativeTerrainReceipt.NBT_KEY).orElse(null));
        }
    }

    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void iris$copyTerrain(ServerLevel level, ChunkAccess chunk,
                                         CallbackInfoReturnable<SerializableChunkData> callback) {
        ((NativeTerrainReceiptHolder) (Object) callback.getReturnValue()).iris$setStructureActivation(
                ModdedNativeTerrainReceipts.structureActivation(chunk));
        ((NativeTerrainReceiptHolder) (Object) callback.getReturnValue()).iris$setNaturalTerrain(
                ModdedNativeTerrainReceipts.get(chunk));
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void iris$restoreTerrain(ServerLevel level, PoiManager points, RegionStorageInfo storage, ChunkPos position,
                                     CallbackInfoReturnable<ProtoChunk> callback) {
        ModdedNativeTerrainReceipts.set(callback.getReturnValue(), iris$naturalTerrain);
        ModdedNativeTerrainReceipts.setStructureActivation(callback.getReturnValue(), iris$structureActivation);
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void iris$writeTerrain(CallbackInfoReturnable<CompoundTag> callback) {
        if (iris$structureActivation > 0) {
            callback.getReturnValue().putLong(NativeTerrainReceipt.STRUCTURE_ACTIVATION_KEY, iris$structureActivation);
        }
        if (iris$naturalTerrain != null) {
            callback.getReturnValue().putByteArray(NativeTerrainReceipt.NBT_KEY, iris$naturalTerrain.clone());
        }
    }
}
