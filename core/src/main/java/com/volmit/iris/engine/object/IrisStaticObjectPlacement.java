package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisBlockData;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterStructurePOI;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Desc("Static Object Placement")
@Accessors(chain = true, fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class IrisStaticObjectPlacement implements IrisStaticPlacement {
    @Required
    @Desc("The X coordinate to spawn the object at")
    private int x = 0;
    @Required
    @Desc("The Y coordinate to spawn the object at\nuse a value <0 to allow the placement modes to function")
    private int y = 0;
    @Required
    @Desc("The Z coordinate to spawn the object at")
    private int z = 0;
    @Required
    @Desc("The object placement to use")
    private IrisObjectPlacement placement;

    @Override
    public void place(MantleWriter writer, RNG rng, IrisData irisData) {
        IrisObject v = placement.getScale().get(rng, placement.getObject(() -> irisData, rng));
        if (v == null) return;

        v.place(x, y, z, writer, placement, rng, irisData);
        int id = rng.i(0, Integer.MAX_VALUE);
        v.place(x, y, z, writer, placement, rng, (b, data) -> {
            writer.setData(b.getX(), b.getY(), b.getZ(), v.getLoadKey() + "@" + id);
            if (placement.isDolphinTarget() && placement.isUnderwater() && B.isStorageChest(data)) {
                writer.setData(b.getX(), b.getY(), b.getZ(), MatterStructurePOI.BURIED_TREASURE);
            }
            if (data instanceof IrisBlockData d) {
                writer.setData(b.getX(), b.getY(), b.getZ(), d.getCustom());
            }
        }, null, irisData);
    }
}
