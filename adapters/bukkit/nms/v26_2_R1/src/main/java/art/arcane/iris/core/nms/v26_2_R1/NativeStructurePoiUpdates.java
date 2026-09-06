package art.arcane.iris.core.nms.v26_2_R1;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Optional;

final class NativeStructurePoiUpdates {
    private static final NamespacedKey DIRTY_SECTIONS = new NamespacedKey("iris", "native_poi_sections");

    private final int skipPoiFlag;

    NativeStructurePoiUpdates(int skipPoiFlag) {
        this.skipPoiFlag = skipPoiFlag;
    }

    static NativeStructurePoiUpdates create() {
        try {
            return new NativeStructurePoiUpdates(Block.class.getField("UPDATE_SKIP_POI").getInt(null));
        } catch (NoSuchFieldException e) {
            return null;
        } catch (IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    boolean setBlock(WorldGenLevel world, BlockPos position, BlockState state, int flags, int limit) {
        if ((flags & skipPoiFlag) != 0) {
            return world.setBlock(position, state, flags, limit);
        }
        ChunkAccess chunk = world.getChunk(position);
        if (!(chunk instanceof ProtoChunk)) {
            return world.setBlock(position, state, flags, limit);
        }
        if (!(chunk instanceof ImposterProtoChunk)
                && !PoiTypes.forState(chunk.getBlockState(position)).equals(PoiTypes.forState(state))) {
            markSection(chunk, chunk.getSectionIndex(position.getY()));
        }
        return world.setBlock(position, state, flags | skipPoiFlag, limit);
    }

    static void reconcile(LevelChunk chunk) {
        long[] sections = chunk.persistentDataContainer.get(DIRTY_SECTIONS, PersistentDataType.LONG_ARRAY);
        if (sections == null) {
            return;
        }
        validateSections(chunk, sections);
        ServerLevel world = (ServerLevel) chunk.getLevel();
        TickThread.ensureTickThread(world, chunk.getPos(), "Native structure POIs require the chunk owning thread");
        PoiManager manager = world.getPoiManager();
        for (int word = 0; word < sections.length; word++) {
            long remaining = sections[word];
            while (remaining != 0) {
                int index = (word << 6) + Long.numberOfTrailingZeros(remaining);
                reconcileSection(chunk, manager, index);
                remaining &= remaining - 1;
            }
        }
        chunk.persistentDataContainer.remove(DIRTY_SECTIONS);
        chunk.markUnsaved();
    }

    private static void markSection(ChunkAccess chunk, int index) {
        long[] sections = chunk.persistentDataContainer.get(DIRTY_SECTIONS, PersistentDataType.LONG_ARRAY);
        int word = index >>> 6;
        long bit = 1L << (index & 63);
        if (sections == null) {
            sections = new long[word + 1];
        } else {
            validateSections(chunk, sections);
            if (word < sections.length && (sections[word] & bit) != 0) {
                return;
            }
            if (word >= sections.length) {
                sections = Arrays.copyOf(sections, word + 1);
            }
        }
        sections[word] |= bit;
        chunk.persistentDataContainer.set(DIRTY_SECTIONS, PersistentDataType.LONG_ARRAY, sections);
    }

    private static void validateSections(ChunkAccess chunk, long[] sections) {
        int count = chunk.getSectionsCount();
        int words = (count + 63) >>> 6;
        if (sections.length > words
                || sections.length == words && (count & 63) != 0
                && (sections[words - 1] >>> (count & 63)) != 0) {
            throw new IllegalArgumentException("Native POI repair sections exceed chunk height at " + chunk.getPos());
        }
    }

    private static void reconcileSection(LevelChunk chunk, PoiManager manager, int index) {
        SectionPos sectionPosition = SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(index));
        Optional<PoiSection> existing = manager.getOrLoad(sectionPosition.asLong());
        if (existing.isPresent()) {
            for (PoiRecord record : existing.get().getRecords(type -> true, PoiManager.Occupancy.ANY).toList()) {
                if (!PoiTypes.forState(chunk.getBlockState(record.getPos())).equals(Optional.of(record.getPoiType()))) {
                    manager.remove(record.getPos());
                }
            }
        }
        LevelChunkSection section = chunk.getSection(index);
        if (!section.maybeHas(PoiTypes::hasPoi)) {
            return;
        }
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Optional<Holder<PoiType>> type = PoiTypes.forState(section.getBlockState(x, y, z));
                    if (type.isEmpty()) {
                        continue;
                    }
                    position.set(sectionPosition.minBlockX() + x, sectionPosition.minBlockY() + y,
                            sectionPosition.minBlockZ() + z);
                    if (!manager.getType(position).equals(type)) {
                        manager.add(position.immutable(), type.get());
                    }
                }
            }
        }
    }
}
