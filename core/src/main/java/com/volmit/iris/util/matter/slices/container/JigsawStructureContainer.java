package com.volmit.iris.util.matter.slices.container;

import com.volmit.iris.engine.object.IrisJigsawStructure;

public class JigsawStructureContainer extends RegistrantContainer<IrisJigsawStructure> {

    public JigsawStructureContainer(String loadKey) {
        super(IrisJigsawStructure.class, loadKey);
    }

    public static JigsawStructureContainer toContainer(IrisJigsawStructure structure) {
        return new JigsawStructureContainer(structure.getLoadKey());
    }
}
