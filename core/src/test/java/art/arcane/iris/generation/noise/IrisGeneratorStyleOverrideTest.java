package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.image.IrisImage;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapOutOfBounds;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisGeneratorStyleOverrideTest {
    @Test
    public void authoredExpressionAndImageMapOverrideEvenAnExplicitFlatBaseStyle() {
        for (NoiseStyle base : new NoiseStyle[]{null, NoiseStyle.FLAT, NoiseStyle.SIMPLEX}) {
            IrisGeneratorStyle expression = new IrisGeneratorStyle(base).setExpression("height");
            IrisGeneratorStyle imageMap = new IrisGeneratorStyle(base).setImageMap("height");
            assertFalse(expression.isFlat());
            assertFalse(imageMap.isFlat());
            assertFalse(new IrisStyledRange().setStyle(expression).isFlat());
            assertFalse(new IrisShapedGeneratorStyle().setMin(1).setMax(2).setGenerator(imageMap).isFlat());
        }
        assertTrue(new IrisGeneratorStyle(NoiseStyle.FLAT).isFlat());
        assertTrue(new IrisGeneratorStyle(null).isFlat());
    }

    @Test
    public void expressionBackedRangeSamplesTheAuthoredExpressionInsteadOfTheMidpoint() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisExpression> expressions = mock(ResourceLoader.class);
        when(data.getExpressionLoader()).thenReturn(expressions);
        when(expressions.load("height")).thenReturn(new IrisExpression().setExpression("x / 4 + 0.125"));
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.FLAT).setExpression("height");
        IrisStyledRange range = new IrisStyledRange().setMin(2D).setMax(14D).setStyle(style);
        RNG rng = new RNG(78264193L);
        double first = style.create(rng, data).fitDouble(2D, 14D, 0D, 0D);
        double second = style.create(rng, data).fitDouble(2D, 14D, 1D, 0D);

        assertNotEquals(8D, first, 0D);
        assertNotEquals(first, second, 0D);
        assertEquals(first, range.get(rng, 0D, 0D, data), 0D);
        assertEquals(second, range.get(rng, 1D, 0D, data), 0D);
    }

    @Test
    public void imageBackedRangeSamplesPixelsInsteadOfTheMidpoint() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisImageMap> maps = mock(ResourceLoader.class);
        ResourceLoader<IrisImage> images = mock(ResourceLoader.class);
        when(data.getImageMapLoader()).thenReturn(maps);
        when(data.getImageLoader()).thenReturn(images);
        BufferedImage pixels = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        pixels.getRaster().setSample(1, 0, 0, 255);
        when(maps.load("height")).thenReturn(new IrisImageMap().setSource("height")
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP));
        when(images.load("height")).thenReturn(new IrisImage(pixels));
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.FLAT).setImageMap("height");
        IrisStyledRange range = new IrisStyledRange().setMin(2D).setMax(14D).setStyle(style);
        RNG rng = new RNG(78264193L);

        assertEquals(2D, range.get(rng, 0D, 0D, data), 0D);
        assertEquals(14D, range.get(rng, 1D, 0D, data), 0D);
    }
}
