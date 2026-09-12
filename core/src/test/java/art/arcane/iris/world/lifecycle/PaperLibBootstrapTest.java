package art.arcane.iris.world.lifecycle;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PaperLibBootstrapTest {
    @Test
    public void isModernVersionSchemeAcceptsMajorVersionAboveOne() {
        assertTrue(PaperLibBootstrap.isModernVersionScheme("26.2-R0.1-SNAPSHOT"));
        assertTrue(PaperLibBootstrap.isModernVersionScheme("26.2"));
        assertTrue(PaperLibBootstrap.isModernVersionScheme("26.2.build.2614-stable"));
        assertTrue(PaperLibBootstrap.isModernVersionScheme("27.0.0-R0.1-SNAPSHOT"));
    }

    @Test
    public void isModernVersionSchemeRejectsLegacyScheme() {
        assertFalse(PaperLibBootstrap.isModernVersionScheme("1.21.4-R0.1-SNAPSHOT"));
        assertFalse(PaperLibBootstrap.isModernVersionScheme("1.13.2-R0.1-SNAPSHOT"));
    }

    @Test
    public void isModernVersionSchemeRejectsUnparsableInput() {
        assertFalse(PaperLibBootstrap.isModernVersionScheme(null));
        assertFalse(PaperLibBootstrap.isModernVersionScheme(""));
        assertFalse(PaperLibBootstrap.isModernVersionScheme("unknown"));
        assertFalse(PaperLibBootstrap.isModernVersionScheme("-R0.1-SNAPSHOT"));
    }
}
