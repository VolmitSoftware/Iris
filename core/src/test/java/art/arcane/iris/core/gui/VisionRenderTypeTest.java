package art.arcane.iris.core.gui;

import art.arcane.iris.core.localization.IrisMessages;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.volmlib.util.localization.MessageKey;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class VisionRenderTypeTest {
    @Test
    public void everyRenderTypeHasALocalizedVisionLabel() {
        for (RenderType type : RenderType.values()) {
            MessageKey key = VisionGUI.modeKey(type);
            assertNotNull(type.name(), key);
            assertTrueCatalogMember(key);
        }
    }

    @Test
    public void teleportCoordinatesFloorAcrossTheNegativeOrigin() {
        assertEquals(-1, VisionGUI.floorWorldCoordinate(-0.01D));
        assertEquals(0, VisionGUI.floorWorldCoordinate(0.99D));
        assertEquals(1, VisionGUI.floorWorldCoordinate(1D));
    }

    private static void assertTrueCatalogMember(MessageKey key) {
        assertSame(key.id(), key, IrisMessages.catalog().key(key.id()));
    }
}
