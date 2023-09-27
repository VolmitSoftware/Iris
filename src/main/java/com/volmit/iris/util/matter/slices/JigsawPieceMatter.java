package com.volmit.iris.util.matter.slices;

import com.volmit.iris.engine.object.IrisJigsawPiece;
import com.volmit.iris.util.matter.Sliced;

@Sliced
public class JigsawPieceMatter extends RegistryMatter<IrisJigsawPiece> {
    public JigsawPieceMatter() {
        this(1,1,1);
    }

    public JigsawPieceMatter(int width, int height, int depth) {
        super(width, height, depth, IrisJigsawPiece.class, new IrisJigsawPiece());
    }
}
