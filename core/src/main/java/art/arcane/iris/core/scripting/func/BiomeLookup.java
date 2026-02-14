package art.arcane.iris.core.scripting.func;

import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.volmlib.util.documentation.BlockCoordinates;

@FunctionalInterface
public interface BiomeLookup {
    @BlockCoordinates
    IrisBiome at(int x, int z);
}
