package art.arcane.iris.util.project.matter.slices;

import art.arcane.iris.core.link.Identifier;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class IdentifierMatter extends RawMatter<Identifier> {

	public IdentifierMatter() {
		this(1, 1, 1);
	}

	public IdentifierMatter(int width, int height, int depth) {
		super(width, height, depth, Identifier.class);
	}

	@Override
	public Palette<Identifier> getGlobalPalette() {
		return null;
	}

	@Override
	public void writeNode(Identifier b, DataOutputStream dos) throws IOException {
		dos.writeUTF(b.toString());
	}

	@Override
	public Identifier readNode(DataInputStream din) throws IOException {
		return Identifier.fromString(din.readUTF());
	}
}
