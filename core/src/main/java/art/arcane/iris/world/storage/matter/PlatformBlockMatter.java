package art.arcane.iris.world.storage.matter;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class PlatformBlockMatter extends RawMatter<NativeBlockState> {

    public PlatformBlockMatter() {
        this(1, 1, 1);
    }

    public PlatformBlockMatter(int width, int height, int depth) {
        super(width, height, depth, NativeBlockState.class);
    }

    @Override
    public Palette<NativeBlockState> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(NativeBlockState b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.key());
    }

    @Override
    public NativeBlockState readNode(DataInputStream din) throws IOException {
        NativeBlockState state = IrisPlatforms.get().registries().decodeBlockState(din.readUTF());
        return state == null ? B.getAirState() : state;
    }
}
