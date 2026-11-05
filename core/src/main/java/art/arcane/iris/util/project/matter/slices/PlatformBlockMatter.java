package art.arcane.iris.util.project.matter.slices;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
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
        dos.writeUTF(persistedKey(b));
    }

    /** A custom block's key is its base block, so it is saved under its custom id, which reads back through its provider. */
    private static String persistedKey(PlatformBlockState state) {
        String customKey = state.isCustom() ? state.deferredPlacementKey() : null;
        return customKey == null ? state.key() : customKey;
    }

    @Override
    public PlatformBlockState readNode(DataInputStream din) throws IOException {
        PlatformBlockState state = B.getState(din.readUTF());
        return state == null ? B.getAirState() : state;
    }
}
