package art.arcane.iris.engine.object.matter;

import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.volmlib.util.function.Function3;

@Desc("WHERE THINGS PLACE")
public enum IrisMatterPlacementLocation {
    SURFACE((e, x, z) -> e.getHeight(x, z, true)),
    SURFACE_ON_FLUID((e, x, z) -> e.getHeight(x, z, false)),
    BEDROCK((e, x, z) -> 0),
    SKY((e, x, z) -> e.getHeight());

    private final Function3<IrisEngine, Integer, Integer, Integer> computer;

    IrisMatterPlacementLocation(Function3<IrisEngine, Integer, Integer, Integer> computer) {
        this.computer = computer;
    }

    public int at(IrisEngine engine, int x, int z) {
        return computer.apply(engine, x, z);
    }
}
