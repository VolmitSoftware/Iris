package art.arcane.iris.world.storage.matter;

import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.Sliced;
import art.arcane.volmlib.util.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class TreeBlockMaterialMatter extends RawMatter<TreeBlockMaterial> {
    public TreeBlockMaterialMatter() {
        this(1, 1, 1);
    }

    public TreeBlockMaterialMatter(int width, int height, int depth) {
        super(width, height, depth, TreeBlockMaterial.class);
    }

    @Override
    public Palette<TreeBlockMaterial> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(TreeBlockMaterial material, DataOutputStream output) throws IOException {
        output.writeUTF(material.materialKey());
    }

    @Override
    public TreeBlockMaterial readNode(DataInputStream input) throws IOException {
        return new TreeBlockMaterial(input.readUTF());
    }
}
