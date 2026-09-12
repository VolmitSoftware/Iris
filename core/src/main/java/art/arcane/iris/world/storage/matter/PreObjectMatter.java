package art.arcane.iris.world.storage.matter;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Consumer4IO;
import art.arcane.volmlib.util.hunk.storage.MappedHunk;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterReader;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.matter.MatterWriter;
import art.arcane.volmlib.util.matter.Sliced;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public final class PreObjectMatter extends MappedHunk<PreObjectMatterCell>
        implements MatterSlice<PreObjectMatterCell> {
    private static final int BLOCK_CAPTURED = 1;
    private static final int BLOCK_PRESENT = 1 << 1;
    private static final int STRING_CAPTURED = 1 << 2;
    private static final int STRING_PRESENT = 1 << 3;
    private static final int CAVERN_CAPTURED = 1 << 4;
    private static final int CAVERN_PRESENT = 1 << 5;
    private static final int HYDROLOGY_CAPTURED = 1 << 6;
    private static final int HYDROLOGY_PRESENT = 1 << 7;
    private static final HydrologyCaveMatter HYDROLOGY_CODEC = new HydrologyCaveMatter();

    public PreObjectMatter() {
        this(1, 1, 1);
    }

    public PreObjectMatter(int width, int height, int depth) {
        super(width, height, depth);
    }

    @Override
    public PreObjectMatterCell get(int x, int y, int z) {
        return MatterSlice.super.get(x, y, z);
    }

    @Override
    public void set(int x, int y, int z, PreObjectMatterCell value) {
        MatterSlice.super.set(x, y, z, value);
    }

    @Override
    public PreObjectMatter iterateSync(Consumer4<Integer, Integer, Integer, PreObjectMatterCell> consumer) {
        super.iterateSync(consumer);
        return this;
    }

    @Override
    public PreObjectMatter iterateSyncIO(Consumer4IO<Integer, Integer, Integer, PreObjectMatterCell> consumer) throws IOException {
        super.iterateSyncIO(consumer);
        return this;
    }

    @Override
    public void empty(PreObjectMatterCell value) {
        super.empty(value);
    }

    @Override
    public boolean isEmpty() {
        return getEntryCount() == 0;
    }

    @Override
    public Class<PreObjectMatterCell> getType() {
        return PreObjectMatterCell.class;
    }

    @Override
    public Palette<PreObjectMatterCell> getGlobalPalette() {
        return null;
    }

    @Override
    public <W> MatterWriter<W, PreObjectMatterCell> writeInto(Class<W> mediumType) {
        return null;
    }

    @Override
    public <W> MatterReader<W, PreObjectMatterCell> readFrom(Class<W> mediumType) {
        return null;
    }

    @Override
    public void writeNode(PreObjectMatterCell cell, DataOutputStream output) throws IOException {
        int flags = flags(cell);
        output.writeByte(flags);
        if ((flags & BLOCK_PRESENT) != 0) {
            output.writeUTF(cell.block().key());
        }
        if ((flags & STRING_PRESENT) != 0) {
            output.writeUTF(cell.string());
        }
        if ((flags & CAVERN_PRESENT) != 0) {
            output.writeBoolean(cell.cavern().isCavern());
            output.writeUTF(cell.cavern().getCustomBiome());
            output.writeByte(cell.cavern().getLiquid());
        }
        if ((flags & HYDROLOGY_PRESENT) != 0) {
            HYDROLOGY_CODEC.writeNode(cell.hydrology(), output);
        }
    }

    @Override
    public PreObjectMatterCell readNode(DataInputStream input) throws IOException {
        int flags = input.readUnsignedByte();
        validateFlags(flags);
        PlatformBlockState block = (flags & BLOCK_PRESENT) == 0 ? null : B.getState(input.readUTF());
        String string = (flags & STRING_PRESENT) == 0 ? null : input.readUTF();
        MatterCavern cavern = (flags & CAVERN_PRESENT) == 0
                ? null
                : new MatterCavern(input.readBoolean(), input.readUTF(), input.readByte());
        HydrologyCaveCell hydrology = (flags & HYDROLOGY_PRESENT) == 0 ? null : HYDROLOGY_CODEC.readNode(input);
        return new PreObjectMatterCell(
                (flags & BLOCK_CAPTURED) != 0,
                block,
                (flags & STRING_CAPTURED) != 0,
                string,
                (flags & CAVERN_CAPTURED) != 0,
                cavern,
                (flags & HYDROLOGY_CAPTURED) != 0,
                hydrology
        );
    }

    private int flags(PreObjectMatterCell cell) {
        int flags = 0;
        if (cell.blockCaptured()) {
            flags |= BLOCK_CAPTURED;
            if (cell.block() != null) {
                flags |= BLOCK_PRESENT;
            }
        }
        if (cell.stringCaptured()) {
            flags |= STRING_CAPTURED;
            if (cell.string() != null) {
                flags |= STRING_PRESENT;
            }
        }
        if (cell.cavernCaptured()) {
            flags |= CAVERN_CAPTURED;
            if (cell.cavern() != null) {
                flags |= CAVERN_PRESENT;
            }
        }
        if (cell.hydrologyCaptured()) {
            flags |= HYDROLOGY_CAPTURED;
            if (cell.hydrology() != null) {
                flags |= HYDROLOGY_PRESENT;
            }
        }
        return flags;
    }

    private void validateFlags(int flags) throws IOException {
        int knownFlags = BLOCK_CAPTURED | BLOCK_PRESENT | STRING_CAPTURED | STRING_PRESENT
                | CAVERN_CAPTURED | CAVERN_PRESENT | HYDROLOGY_CAPTURED | HYDROLOGY_PRESENT;
        if ((flags & ~knownFlags) != 0) {
            throw new IOException("Unknown pre-object matter flags " + flags);
        }
        if ((flags & (BLOCK_CAPTURED | STRING_CAPTURED | CAVERN_CAPTURED | HYDROLOGY_CAPTURED)) == 0) {
            throw new IOException("Pre-object matter cell captures no values");
        }
        if ((flags & BLOCK_PRESENT) != 0 && (flags & BLOCK_CAPTURED) == 0) {
            throw new IOException("Pre-object block value is not captured");
        }
        if ((flags & STRING_PRESENT) != 0 && (flags & STRING_CAPTURED) == 0) {
            throw new IOException("Pre-object string value is not captured");
        }
        if ((flags & HYDROLOGY_PRESENT) != 0 && (flags & HYDROLOGY_CAPTURED) == 0) {
            throw new IOException("Pre-object hydrology value is not captured");
        }
        if ((flags & CAVERN_PRESENT) != 0 && (flags & CAVERN_CAPTURED) == 0) {
            throw new IOException("Pre-object cavern value is not captured");
        }
    }
}
