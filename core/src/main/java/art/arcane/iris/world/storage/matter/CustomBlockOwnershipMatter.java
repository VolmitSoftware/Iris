package art.arcane.iris.world.storage.matter;

import art.arcane.iris.generation.mantle.CustomBlockOwnership;
import art.arcane.iris.integration.Identifier;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Sliced
public final class CustomBlockOwnershipMatter extends RawMatter<CustomBlockOwnership> {
    public CustomBlockOwnershipMatter() {
        this(1, 1, 1);
    }

    public CustomBlockOwnershipMatter(int width, int height, int depth) {
        super(width, height, depth, CustomBlockOwnership.class);
    }

    @Override
    public Palette<CustomBlockOwnership> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(CustomBlockOwnership ownership, DataOutputStream output) throws IOException {
        output.writeInt(ownership.entries().size());
        for (CustomBlockOwnership.Entry entry : ownership.entries()) {
            output.writeUTF(entry.identifier().toString());
            output.writeUTF(entry.token());
            output.writeBoolean(entry.invalidated());
        }
    }

    @Override
    public CustomBlockOwnership readNode(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > 65_536) {
            throw new IOException("Invalid custom block ownership count: " + size);
        }
        List<CustomBlockOwnership.Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new CustomBlockOwnership.Entry(Identifier.fromString(input.readUTF()),
                    input.readUTF(), input.readBoolean()));
        }
        return new CustomBlockOwnership(entries);
    }
}
