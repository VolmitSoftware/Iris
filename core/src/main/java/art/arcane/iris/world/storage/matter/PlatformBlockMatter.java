package art.arcane.iris.world.storage.matter;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class PlatformBlockMatter extends RawMatter<PlatformBlockState> {

    public PlatformBlockMatter() {
        this(1, 1, 1);
    }

    public PlatformBlockMatter(int width, int height, int depth) {
        super(width, height, depth, PlatformBlockState.class);
    }

    @Override
    public Palette<PlatformBlockState> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(PlatformBlockState b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.key());
    }

    @Override
    public PlatformBlockState readNode(DataInputStream din) throws IOException {
        PlatformBlockState state = B.getState(din.readUTF());
        return state == null ? B.getAirState() : state;
    }
}
