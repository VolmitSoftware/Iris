package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.jigsaw.PlannedStructure;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Desc("Static Jigsaw Structure Placement")
@Accessors(chain = true, fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class IrisStaticStructurePlacement implements IrisStaticPlacement {
    @Required
    @Desc("The X coordinate to spawn the structure at")
    private int x = 0;
    @Required
    @Desc("The Y coordinate to spawn the structure at")
    private int y = 0;
    @Required
    @Desc("The Z coordinate to spawn the structure at")
    private int z = 0;
    @Required
    @ArrayType(min = 1, type = String.class)
    @RegistryListResource(IrisJigsawStructure.class)
    @Desc("The structures to place")
    private KList<String> structures;

    public int maxDimension(IrisData data) {
        return data.getJigsawStructureLoader().loadAll(structures)
                .stream()
                .mapToInt(IrisJigsawStructure::getMaxDimension)
                .max()
                .orElse(0);
    }

    @Override
    public void place(MantleWriter writer, RNG rng, IrisData data) {
        IrisJigsawStructure jigsaw = null;
        while (jigsaw == null && !structures.isEmpty()) {
            String loadKey = structures.popRandom(rng);
            jigsaw = data.getJigsawStructureLoader().load(loadKey);

            if (jigsaw == null)
                Iris.error("Jigsaw structure not found " + loadKey);
        }
        if (jigsaw == null) {
            Iris.error("No jigsaw structure found for " + structures);
            return;
        }

        new PlannedStructure(jigsaw, new IrisPosition(x, y, z), rng, false)
                .place(writer, writer.getMantle(), writer.getEngine());
    }
}
