package art.arcane.iris.world.storage.matter;

import art.arcane.iris.generation.mantle.TreeCanopy;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public final class TreeCanopyMatter extends RawMatter<TreeCanopy> {
    public TreeCanopyMatter() {
        this(1, 1, 1);
    }

    public TreeCanopyMatter(int width, int height, int depth) {
        super(width, height, depth, TreeCanopy.class);
    }

    @Override
    public Palette<TreeCanopy> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(TreeCanopy canopy, DataOutputStream output) throws IOException {
        output.writeByte(1);
    }

    @Override
    public TreeCanopy readNode(DataInputStream input) throws IOException {
        if (input.readUnsignedByte() != 1) {
            throw new IOException("Invalid tree canopy placement condition");
        }
        return TreeCanopy.INSTANCE;
    }
}
