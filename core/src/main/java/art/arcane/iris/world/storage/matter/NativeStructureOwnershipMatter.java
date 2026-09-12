package art.arcane.iris.world.storage.matter;

import art.arcane.iris.structure.nativegen.NativeStructureOwnershipBundle;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public final class NativeStructureOwnershipMatter extends RawMatter<NativeStructureOwnershipBundle> {
    public NativeStructureOwnershipMatter() {
        this(1, 1, 1);
    }

    public NativeStructureOwnershipMatter(int width, int height, int depth) {
        super(width, height, depth, NativeStructureOwnershipBundle.class);
    }

    @Override
    public Palette<NativeStructureOwnershipBundle> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(NativeStructureOwnershipBundle bundle, DataOutputStream output) throws IOException {
        bundle.write(output);
    }

    @Override
    public NativeStructureOwnershipBundle readNode(DataInputStream input) throws IOException {
        return NativeStructureOwnershipBundle.read(input);
    }
}
