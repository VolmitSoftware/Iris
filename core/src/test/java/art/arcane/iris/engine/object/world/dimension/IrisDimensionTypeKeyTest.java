package art.arcane.iris.engine.object.world.dimension;

import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisDimensionTypeKeyTest {
    @Test
    public void dimensionTypeKeyUsesSanitizedSemanticPackKey() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("Overworld");

        assertEquals("overworld", dimension.getDimensionTypeKey());
    }

    @Test
    public void dimensionTypeKeySanitizesUnsafePackCharacters() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("Worlds/My Pack");

        assertEquals("worlds_my_pack", dimension.getDimensionTypeKey());
    }
}
