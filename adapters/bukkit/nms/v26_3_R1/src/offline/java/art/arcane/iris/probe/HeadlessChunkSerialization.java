package art.arcane.iris.probe;

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class HeadlessChunkSerialization {
    private HeadlessChunkSerialization() {
    }

    static SerializableChunkData copyOf(ProtoChunk chunk, Context context) {
        if (!chunk.canBeSerialized() || chunk.getPersistedStatus().getChunkType() != ChunkType.PROTOCHUNK) {
            throw new IllegalArgumentException("Headless serialization requires a serializable proto chunk");
        }
        List<CompoundTag> blockEntities = new ArrayList<>(chunk.getBlockEntitiesPos().size());
        for (BlockPos position : chunk.getBlockEntitiesPos()) {
            CompoundTag tag = chunk.getBlockEntityNbtForSaving(position, context.structures().registryAccess());
            if (tag != null) {
                blockEntities.add(tag.copy());
            }
        }
        List<CompoundTag> entities = new ArrayList<>(chunk.getEntities().size());
        for (CompoundTag entity : chunk.getEntities()) {
            entities.add(entity.copy());
        }
        Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);
        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            heightmaps.put(entry.getKey(), entry.getValue().getRawData().clone());
        }
        ShortList[] postProcessing = new ShortList[chunk.getPostProcessing().length];
        for (int i = 0; i < postProcessing.length; i++) {
            ShortList source = chunk.getPostProcessing()[i];
            if (source != null && !source.isEmpty()) {
                postProcessing[i] = new ShortArrayList(source);
            }
        }
        CompoundTag persistent = chunk.persistentDataContainer.isEmpty()
                ? null : chunk.persistentDataContainer.toTagCompound().copy();
        return new SerializableChunkData(context.containers(), chunk.getPos(), chunk.getMinSectionY(),
                context.gameTime(), chunk.getInhabitedTime(), chunk.getPersistedStatus(),
                chunk.getBlendingData() == null ? null : chunk.getBlendingData().pack(),
                chunk.getBelowZeroRetrogen(), chunk.getUpgradeData().copy(), heightmaps,
                chunk.getTicksForSerialization(context.gameTime()), postProcessing, chunk.isLightCorrect(),
                sections(chunk), entities, blockEntities, structures(chunk, context.structures()), persistent);
    }

    private static List<SerializableChunkData.SectionData> sections(ProtoChunk chunk) {
        LevelChunkSection[] blocks = chunk.getSections();
        StarlightChunk light = (StarlightChunk) chunk;
        SWMRNibbleArray[] blockLight = light.starlight$getBlockNibbles();
        SWMRNibbleArray[] skyLight = light.starlight$getSkyNibbles();
        List<SerializableChunkData.SectionData> sections = new ArrayList<>(blocks.length + 2);
        for (int i = 0; i < blocks.length + 2; i++) {
            LevelChunkSection blocksCopy = i > 0 && i <= blocks.length ? blocks[i - 1].copy() : null;
            SWMRNibbleArray.SaveState blockState = blockLight[i].getSaveState();
            SWMRNibbleArray.SaveState skyState = skyLight[i].getSaveState();
            if (blocksCopy == null && blockState == null && skyState == null) {
                continue;
            }
            SerializableChunkData.SectionData section = new SerializableChunkData.SectionData(
                    chunk.getMinSectionY() + i - 1, blocksCopy, layer(blockState), layer(skyState));
            if (blockState != null) {
                ((StarlightSectionData) (Object) section).starlight$setBlockLightState(blockState.state);
            }
            if (skyState != null) {
                ((StarlightSectionData) (Object) section).starlight$setSkyLightState(skyState.state);
            }
            sections.add(section);
        }
        return sections;
    }

    private static DataLayer layer(SWMRNibbleArray.SaveState state) {
        return state == null || state.data == null ? null : new DataLayer(state.data);
    }

    private static CompoundTag structures(ProtoChunk chunk, StructurePieceSerializationContext context) {
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        CompoundTag starts = new CompoundTag();
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            starts.put(Objects.requireNonNull(registry.getKey(entry.getKey()), "structure key").toString(),
                    entry.getValue().createTag(context, chunk.getPos()));
        }
        CompoundTag references = new CompoundTag();
        for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                references.putLongArray(Objects.requireNonNull(registry.getKey(entry.getKey()), "structure key").toString(),
                        entry.getValue().toLongArray());
            }
        }
        CompoundTag result = new CompoundTag();
        result.put("starts", starts);
        result.put("References", references);
        return result;
    }

    record Context(StructurePieceSerializationContext structures, PalettedContainerFactory containers, long gameTime) {
        Context {
            Objects.requireNonNull(structures, "structures");
            Objects.requireNonNull(containers, "containers");
        }
    }
}
