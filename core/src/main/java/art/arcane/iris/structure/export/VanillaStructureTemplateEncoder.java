package art.arcane.iris.structure.export;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

final class VanillaStructureTemplateEncoder {
    private static final int DATA_VERSION_26_2 = 4903;
    private static final Comparator<BlockEntry> BLOCK_ORDER = Comparator
            .comparingInt((BlockEntry entry) -> entry.position().y())
            .thenComparingInt(entry -> entry.position().x())
            .thenComparingInt(entry -> entry.position().z());

    byte[] encode(
            IrisObject object,
            IrisJigsawPiece piece,
            Function<String, String> poolIdentifier
    ) throws IOException {
        Map<BlockPosition, BlockEntry> blocks = objectBlocks(object);
        addConnectors(blocks, piece, poolIdentifier);
        List<BlockEntry> orderedBlocks = new ArrayList<>(blocks.values());
        orderedBlocks.sort(BLOCK_ORDER);

        Map<String, Integer> paletteIndexes = new LinkedHashMap<>();
        List<VanillaBlockState> paletteStates = new ArrayList<>();
        for (BlockEntry block : orderedBlocks) {
            String key = block.state().canonical();
            if (!paletteIndexes.containsKey(key)) {
                paletteIndexes.put(key, paletteStates.size());
                paletteStates.add(block.state());
            }
        }

        CompoundTag root = new CompoundTag();
        root.put("size", intList(object.getW(), object.getH(), object.getD()));
        root.put("palette", palette(paletteStates));
        root.put("blocks", blocks(orderedBlocks, paletteIndexes));
        root.put("entities", new ListTag<>(CompoundTag.class));
        root.putInt("DataVersion", DATA_VERSION_26_2);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NBTUtil.write(new NamedTag("", root), output, true);
        return output.toByteArray();
    }

    private Map<BlockPosition, BlockEntry> objectBlocks(IrisObject object) {
        Map<BlockPosition, BlockEntry> blocks = new LinkedHashMap<>();
        Vector3i center = object.getCenter();
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : object.getBlocks()) {
            IrisBlockVector signed = entry.getKey();
            BlockPosition position = new BlockPosition(
                    signed.getBlockX() + center.getX(),
                    signed.getBlockY() + center.getY(),
                    signed.getBlockZ() + center.getZ());
            VanillaBlockState state = VanillaBlockState.parse(entry.getValue().key());
            blocks.put(position, new BlockEntry(position, state, null));
        }
        return blocks;
    }

    private void addConnectors(
            Map<BlockPosition, BlockEntry> blocks,
            IrisJigsawPiece piece,
            Function<String, String> poolIdentifier
    ) {
        for (IrisJigsawConnector connector : piece.getConnectors()) {
            IrisPosition sourcePosition = connector.getPosition();
            BlockPosition position = new BlockPosition(
                    sourcePosition.getX(), sourcePosition.getY(), sourcePosition.getZ());
            String orientation = orientation(connector.getDirection(), connector.getTop());
            VanillaBlockState state = VanillaBlockState.parse(
                    "minecraft:jigsaw[orientation=" + orientation + "]");
            CompoundTag nbt = connectorNbt(connector, poolIdentifier.apply(connector.getPool()));
            blocks.put(position, new BlockEntry(position, state, nbt));
        }
    }

    private CompoundTag connectorNbt(IrisJigsawConnector connector, String poolIdentifier) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:jigsaw");
        nbt.putString("name", VanillaResourceIdentifier.normalizeConnectorIdentifier(connector.getName()));
        nbt.putString("target", VanillaResourceIdentifier.normalizeConnectorIdentifier(connector.getTargetName()));
        nbt.putString("pool", poolIdentifier);
        nbt.putString("final_state", VanillaBlockState.parse(connector.getFinalState()).canonical());
        nbt.putString("joint", connector.getJoint().name().toLowerCase(Locale.ROOT));
        nbt.putInt("selection_priority", connector.getSelectionPriority());
        nbt.putInt("placement_priority", connector.getPlacementPriority());
        return nbt;
    }

    static String orientation(IrisDirection front, IrisDirection top) {
        if (front == IrisDirection.UP_POSITIVE_Y || front == IrisDirection.DOWN_NEGATIVE_Y) {
            if (top == IrisDirection.NORTH_NEGATIVE_Z
                    || top == IrisDirection.SOUTH_POSITIVE_Z
                    || top == IrisDirection.EAST_POSITIVE_X
                    || top == IrisDirection.WEST_NEGATIVE_X) {
                return directionName(front) + "_" + directionName(top);
            }
            throw new IllegalArgumentException("Vertical jigsaw fronts require a horizontal top direction");
        }
        if (top != IrisDirection.UP_POSITIVE_Y) {
            throw new IllegalArgumentException("Horizontal jigsaw fronts require an upward top direction");
        }
        return directionName(front) + "_up";
    }

    private static String directionName(IrisDirection direction) {
        return switch (direction) {
            case UP_POSITIVE_Y -> "up";
            case DOWN_NEGATIVE_Y -> "down";
            case NORTH_NEGATIVE_Z -> "north";
            case SOUTH_POSITIVE_Z -> "south";
            case EAST_POSITIVE_X -> "east";
            case WEST_NEGATIVE_X -> "west";
        };
    }

    private ListTag<CompoundTag> palette(List<VanillaBlockState> states) {
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        for (VanillaBlockState state : states) {
            palette.add(state.toNbt());
        }
        return palette;
    }

    private ListTag<CompoundTag> blocks(
            List<BlockEntry> entries,
            Map<String, Integer> paletteIndexes
    ) {
        ListTag<CompoundTag> blocks = new ListTag<>(CompoundTag.class);
        for (BlockEntry entry : entries) {
            CompoundTag block = new CompoundTag();
            block.put("pos", intList(entry.position().x(), entry.position().y(), entry.position().z()));
            block.putInt("state", paletteIndexes.get(entry.state().canonical()));
            if (entry.nbt() != null) {
                block.put("nbt", entry.nbt());
            }
            blocks.add(block);
        }
        return blocks;
    }

    private ListTag<IntTag> intList(int... values) {
        ListTag<IntTag> list = new ListTag<>(IntTag.class);
        for (int value : values) {
            list.add(new IntTag(value));
        }
        return list;
    }

    private record BlockPosition(int x, int y, int z) {
    }

    private record BlockEntry(BlockPosition position, VanillaBlockState state, CompoundTag nbt) {
    }
}
