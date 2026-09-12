package art.arcane.iris.pack.loading;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageResourceLoaderTest {
    @Test
    public void preflightsDimensionsBeforeImageDecode() {
        assertTrue(ImageResourceLoader.supportedDimensions(1, 1));
        assertTrue(ImageResourceLoader.supportedDimensions(16_384, 1_024));
        assertFalse(ImageResourceLoader.supportedDimensions(16_384, 1_025));
        assertFalse(ImageResourceLoader.supportedDimensions(16_385, 1));
        assertFalse(ImageResourceLoader.supportedDimensions(0, 1));
    }
}
