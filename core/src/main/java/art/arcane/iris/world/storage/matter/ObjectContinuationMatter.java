package art.arcane.iris.world.storage.matter;

import art.arcane.iris.generation.mantle.ObjectContinuationBundle;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public final class ObjectContinuationMatter extends RawMatter<ObjectContinuationBundle> {
    public ObjectContinuationMatter() {
        this(1, 1, 1);
    }

    public ObjectContinuationMatter(int width, int height, int depth) {
        super(width, height, depth, ObjectContinuationBundle.class);
    }

    @Override
    public Palette<ObjectContinuationBundle> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(ObjectContinuationBundle bundle, DataOutputStream output) throws IOException {
        bundle.write(output);
    }

    @Override
    public ObjectContinuationBundle readNode(DataInputStream input) throws IOException {
        return ObjectContinuationBundle.read(input);
    }
}
