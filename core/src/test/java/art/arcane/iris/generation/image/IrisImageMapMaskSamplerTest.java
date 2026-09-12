package art.arcane.iris.generation.image;

import org.junit.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class IrisImageMapMaskSamplerTest {
    private static final double EPSILON = 0.000001D;

    @Test
    public void appliesEveryOperationInDeclaredOrder() {
        CompiledIrisImageMap half = compile(128);
        CompiledIrisImageMap quarter = compile(64);

        assertEquals(128D / 255D, sampler(layer(half, IrisImageMapMaskOperation.MULTIPLY)).sample(0D, 0D), EPSILON);
        assertEquals(128D / 255D, sampler(layer(half, IrisImageMapMaskOperation.MINIMUM)).sample(0D, 0D), EPSILON);
        assertEquals(128D / 255D, sampler(
                layer(half, IrisImageMapMaskOperation.SUBTRACT),
                layer(half, IrisImageMapMaskOperation.MAXIMUM)
        ).sample(0D, 0D), EPSILON);
        assertEquals(191D / 255D, sampler(
                layer(half, IrisImageMapMaskOperation.SUBTRACT),
                layer(quarter, IrisImageMapMaskOperation.ADD)
        ).sample(0D, 0D), EPSILON);
        assertEquals(127D / 255D, sampler(layer(half, IrisImageMapMaskOperation.SUBTRACT)).sample(0D, 0D), EPSILON);

        double subtractThenMultiply = sampler(
                layer(quarter, IrisImageMapMaskOperation.SUBTRACT),
                layer(half, IrisImageMapMaskOperation.MULTIPLY)
        ).sample(0D, 0D);
        double multiplyThenSubtract = sampler(
                layer(half, IrisImageMapMaskOperation.MULTIPLY),
                layer(quarter, IrisImageMapMaskOperation.SUBTRACT)
        ).sample(0D, 0D);
        assertEquals((1D - (64D / 255D)) * (128D / 255D), subtractThenMultiply, EPSILON);
        assertEquals((128D - 64D) / 255D, multiplyThenSubtract, EPSILON);
    }

    @Test
    public void appliesInversionThresholdFalloffAndClamping() {
        CompiledIrisImageMap quarter = compile(64);
        CompiledIrisImageMap threeQuarters = compile(192);

        assertEquals(191D / 255D, sampler(layer(
                quarter, IrisImageMapMaskOperation.MULTIPLY, true, 0D, 0D
        )).sample(0D, 0D), EPSILON);
        assertEquals(0D, sampler(layer(
                quarter, IrisImageMapMaskOperation.MULTIPLY, false, 0.5D, 0D
        )).sample(0D, 0D), EPSILON);
        assertEquals(1D, sampler(layer(
                threeQuarters, IrisImageMapMaskOperation.MULTIPLY, false, 0.5D, 0D
        )).sample(0D, 0D), EPSILON);
        assertEquals(((128D / 255D) - 0.25D) / 0.5D, sampler(layer(
                compile(128), IrisImageMapMaskOperation.MULTIPLY, false, 0.25D, 0.5D
        )).sample(0D, 0D), EPSILON);
        assertEquals(1D, sampler(layer(
                threeQuarters, IrisImageMapMaskOperation.ADD
        )).sample(0D, 0D), EPSILON);
        assertEquals(0D, sampler(layer(
                threeQuarters, IrisImageMapMaskOperation.SUBTRACT
        ), layer(
                threeQuarters, IrisImageMapMaskOperation.SUBTRACT
        )).sample(0D, 0D), EPSILON);
    }

    @Test
    public void rejectsInvalidLayerDefinitionsAndMismatchedLists() {
        CompiledIrisImageMap compiled = compile(128);
        IrisImageMapMask invalid = new IrisImageMapMask().setMap("mask").setThreshold(Double.NaN);

        assertThrows(IrisImageMapValidationException.class,
                () -> IrisImageMapMaskSampler.layer(compiled, invalid));
        assertThrows(IllegalArgumentException.class,
                () -> IrisImageMapMaskSampler.of(List.of(compiled), List.of()));
    }

    private static IrisImageMapMaskSampler sampler(IrisImageMapMaskSampler.Layer... layers) {
        return new IrisImageMapMaskSampler(List.of(layers));
    }

    private static IrisImageMapMaskSampler.Layer layer(
            CompiledIrisImageMap compiled,
            IrisImageMapMaskOperation operation
    ) {
        return layer(compiled, operation, false, 0D, 0D);
    }

    private static IrisImageMapMaskSampler.Layer layer(
            CompiledIrisImageMap compiled,
            IrisImageMapMaskOperation operation,
            boolean inverted,
            double threshold,
            double falloff
    ) {
        return IrisImageMapMaskSampler.layer(compiled, new IrisImageMapMask()
                .setMap("mask")
                .setOperation(operation)
                .setInverted(inverted)
                .setThreshold(threshold)
                .setFalloff(falloff));
    }

    private static CompiledIrisImageMap compile(int value) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(0, 0, 0, value);
        IrisImageMap definition = new IrisImageMap()
                .setSource("mask")
                .setType(IrisImageMapType.GRAYSCALE_MASK)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP);
        return CompiledIrisImageMap.compile(definition, new IrisImage(image, "png"));
    }
}
