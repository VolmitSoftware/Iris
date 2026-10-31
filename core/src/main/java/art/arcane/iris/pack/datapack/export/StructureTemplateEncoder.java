package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.mca.MCABlockStateCodecSupport;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

public final class StructureTemplateEncoder {
    private static final Comparator<TemplateBlock> BLOCK_ORDER = Comparator
            .comparingInt(TemplateBlock::y)
            .thenComparingInt(TemplateBlock::x)
            .thenComparingInt(TemplateBlock::z);

    public static TemplateVolume volume(IrisObject object, String resource, List<ExportDiagnostic> diagnostics) {
        List<TemplateBlock> inside = new ArrayList<>();
        int outside = 0;
        String firstOutside = null;
        for (TemplateBlock block : blocks(object, resource, diagnostics)) {
            if (block.x() < 0 || block.y() < 0 || block.z() < 0
                    || block.x() >= object.getW() || block.y() >= object.getH() || block.z() >= object.getD()) {
                outside++;
                if (firstOutside == null) {
                    firstOutside = block.x() + "," + block.y() + "," + block.z();
                }
                continue;
            }
            inside.add(block);
        }
        if (outside > 0) {
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.OBJECT_BOUNDS, resource,
                    outside + " blocks outside the " + object.getW() + "x" + object.getH() + "x" + object.getD()
                            + " object size are left out of the template, first at " + firstOutside));
        }
        return new TemplateVolume(object.getW(), object.getH(), object.getD(), inside);
    }

    public static List<TemplateBlock> blocks(IrisObject object, String resource, List<ExportDiagnostic> diagnostics) {
        List<TemplateBlock> blocks = new ArrayList<>();
        Vector3i center = object.getCenter();
        int droppedTiles = 0;
        String firstDropped = null;
        Set<String> customBlocks = new TreeSet<>();
        for (Map.Entry<IrisBlockVector, NativeBlockState> entry : object.getBlocks()) {
            IrisBlockVector signed = entry.getKey();
            int x = signed.getBlockX() + center.getX();
            int y = signed.getBlockY() + center.getY();
            int z = signed.getBlockZ() + center.getZ();
            VanillaBlockState state;
            try {
                state = VanillaBlockState.parse(entry.getValue().key());
            } catch (IllegalArgumentException exception) {
                customBlocks.add(entry.getValue().key());
                continue;
            }
            TileData tile = object.getStates().get(signed);
            CompoundTag nbt = null;
            if (tile != null) {
                Optional<CompoundTag> converted = TileEntityNbt.toNbt(tile.getMaterialKey(), tile.getProperties());
                if (converted.isPresent()) {
                    nbt = converted.get();
                } else {
                    droppedTiles++;
                    if (firstDropped == null) {
                        firstDropped = state.name() + " at " + x + "," + y + "," + z;
                    }
                }
            }
            blocks.add(new TemplateBlock(x, y, z, state, nbt));
        }
        if (!customBlocks.isEmpty()) {
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.CUSTOM_BLOCK, resource,
                    "Blocks outside Minecraft are left out of the template: " + String.join(", ", customBlocks)));
        }
        if (droppedTiles > 0) {
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.TILE_ENTITY, resource,
                    droppedTiles + " tile entities have no vanilla block entity form and export as plain blocks, first "
                            + firstDropped));
        }
        return blocks;
    }

    public byte[] encode(IrisObject object, List<TemplateBlock> extraBlocks, DataVersion target) throws IOException {
        List<ExportDiagnostic> lossy = new ArrayList<>();
        TemplateVolume volume = volume(object, object.getLoadKey(), lossy);
        if (!lossy.isEmpty()) {
            throw new IllegalArgumentException("Object '" + object.getLoadKey() + "' cannot be encoded: " + lossy.getFirst().message());
        }
        Map<BlockPosition, TemplateBlock> blocks = new LinkedHashMap<>();
        for (TemplateBlock block : volume.blocks()) {
            blocks.put(new BlockPosition(block.x(), block.y(), block.z()), block);
        }
        for (TemplateBlock extra : extraBlocks) {
            blocks.put(new BlockPosition(extra.x(), extra.y(), extra.z()), extra);
        }
        return encode(new TemplateVolume(volume.sizeX(), volume.sizeY(), volume.sizeZ(), new ArrayList<>(blocks.values())), target);
    }

    public byte[] encode(TemplateVolume volume, DataVersion target) throws IOException {
        MCABlockStateCodecSupport.Format format = paletteFormat(target);
        List<TemplateBlock> orderedBlocks = new ArrayList<>(volume.blocks());
        orderedBlocks.sort(BLOCK_ORDER);
        for (int index = 1; index < orderedBlocks.size(); index++) {
            if (BLOCK_ORDER.compare(orderedBlocks.get(index - 1), orderedBlocks.get(index)) == 0) {
                TemplateBlock duplicate = orderedBlocks.get(index);
                throw new IllegalArgumentException("Template has two blocks at " + duplicate.x() + "," + duplicate.y() + "," + duplicate.z());
            }
        }

        Map<String, Integer> paletteIndexes = new LinkedHashMap<>();
        List<VanillaBlockState> paletteStates = new ArrayList<>();
        for (TemplateBlock block : orderedBlocks) {
            String key = block.state().canonical();
            if (!paletteIndexes.containsKey(key)) {
                paletteIndexes.put(key, paletteStates.size());
                paletteStates.add(block.state());
            }
        }

        CompoundTag root = new CompoundTag();
        root.put("size", intList(volume.sizeX(), volume.sizeY(), volume.sizeZ()));
        root.put("palette", palette(paletteStates, format));
        root.put("blocks", blocks(orderedBlocks, paletteIndexes));
        root.put("entities", new ListTag<>(CompoundTag.class));
        root.putInt("DataVersion", target.getWorldDataVersion());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            NBTUtil.write(new NamedTag("", root), gzip, false);
        }
        return output.toByteArray();
    }

    private static MCABlockStateCodecSupport.Format paletteFormat(DataVersion target) {
        return switch (target) {
            case V26_3 -> MCABlockStateCodecSupport.Format.LOWERCASE;
            case V1_21_1, V26_1_2, V26_2 -> MCABlockStateCodecSupport.Format.CAPITALIZED;
            case UNSUPPORTED -> throw new IllegalArgumentException("Structure templates need a supported target version");
        };
    }

    private ListTag<CompoundTag> palette(List<VanillaBlockState> states, MCABlockStateCodecSupport.Format format) {
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        for (VanillaBlockState state : states) {
            palette.add(state.toNbt(format));
        }
        return palette;
    }

    private ListTag<CompoundTag> blocks(List<TemplateBlock> entries, Map<String, Integer> paletteIndexes) {
        ListTag<CompoundTag> blocks = new ListTag<>(CompoundTag.class);
        for (TemplateBlock entry : entries) {
            CompoundTag block = new CompoundTag();
            block.put("pos", intList(entry.x(), entry.y(), entry.z()));
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
}
