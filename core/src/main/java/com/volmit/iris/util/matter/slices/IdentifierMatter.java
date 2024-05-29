package com.volmit.iris.util.matter.slices;

import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.Sliced;

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
