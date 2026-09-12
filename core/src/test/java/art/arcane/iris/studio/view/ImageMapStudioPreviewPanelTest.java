package art.arcane.iris.studio.view;

import art.arcane.iris.generation.image.CompiledIrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.image.IrisImageMapMaskSampler;
import art.arcane.iris.generation.image.IrisImage;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapMask;
import art.arcane.iris.generation.image.IrisImageMapMaskOperation;
import art.arcane.iris.generation.image.IrisImageMapOutOfBounds;
import art.arcane.iris.generation.image.IrisImageMapType;
import art.arcane.volmlib.util.collection.KMap;
import org.junit.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ImageMapStudioPreviewPanelTest {
    @Test
    public void rendersAnInspectedSourceWithoutACompiledSemanticType() {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xFFFF0000);
        ImageMapStudioPreviewPanel panel = new ImageMapStudioPreviewPanel();
        try {
            panel.setSource(source);

            BufferedImage rendered = panel.renderSourceSnapshot(64, 64);

            assertEquals(0xFFFF0000, rendered.getRGB(32, 32));
        } finally {
            panel.close();
        }
    }

    @Test
    public void rendersRuntimeInterpretedHeightDataWithoutAFrame() {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        source.getRaster().setSample(0, 0, 0, 0);
        source.getRaster().setSample(1, 0, 0, 255);
        IrisImageMap definition = new IrisImageMap()
                .setSource("test")
                .setType(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP)
                .setMinimumHeight(0D)
                .setMaximumHeight(255D);
        CompiledIrisImageMap compiled = CompiledIrisImageMap.compile(
                definition, new IrisImage(source, "png")
        );
        ImageMapStudioPreviewPanel panel = new ImageMapStudioPreviewPanel();
        try {
            panel.setPreview(
                    source, compiled, IrisImageMapMaskSampler.empty(), null,
                    IrisImageMapApplication.TERRAIN_HEIGHT, -64, (worldX, worldZ) -> 0D
            );

            BufferedImage interpreted = panel.renderInterpretedSnapshot(64, 32);

            assertNotEquals(interpreted.getRGB(4, 16), interpreted.getRGB(59, 16));
        } finally {
            panel.close();
        }
    }

    @Test
    public void blendsMaskedHeightAgainstTheProceduralRuntimeBaseline() {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        source.getRaster().setSample(0, 0, 0, 255);
        IrisImageMap definition = new IrisImageMap()
                .setSource("height")
                .setType(IrisImageMapType.GRAYSCALE_HEIGHT)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP)
                .setMinimumHeight(0D)
                .setMaximumHeight(100D);
        CompiledIrisImageMap compiled = CompiledIrisImageMap.compile(definition, new IrisImage(source, "png"));
        IrisImageMapMaskSampler maskSampler = maskSampler(128);
        ImageMapStudioPreviewPanel panel = new ImageMapStudioPreviewPanel();
        try {
            panel.setPreview(
                    source, compiled, maskSampler, null,
                    IrisImageMapApplication.TERRAIN_HEIGHT, -64, (worldX, worldZ) -> 20D
            );

            BufferedImage interpreted = panel.renderInterpretedSnapshot(64, 32);
            double weight = 128D / 255D;
            double expectedHeight = -64D + IrisImageMapRuntime.blendTerrainHeight(164D, 20D, weight);

            assertEquals(
                    ImageMapStudioModel.heightColor(expectedHeight, 0D, 100D),
                    interpreted.getRGB(32, 16)
            );
        } finally {
            panel.close();
        }
    }

    @Test
    public void usesTheRuntimeCategoricalMaskCutoff() {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xFFFF0000);
        IrisImageMap definition = new IrisImageMap()
                .setSource("biome")
                .setType(IrisImageMapType.COLOR_MAP)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP)
                .setColors(new KMap<>(Map.of("#FF0000", "iris:test")));
        CompiledIrisImageMap compiled = CompiledIrisImageMap.compile(definition, new IrisImage(source, "png"));
        ImageMapStudioPreviewPanel below = new ImageMapStudioPreviewPanel();
        ImageMapStudioPreviewPanel above = new ImageMapStudioPreviewPanel();
        try {
            below.setPreview(
                    source, compiled, maskSampler(127), null,
                    IrisImageMapApplication.BIOME, -64, null
            );
            above.setPreview(
                    source, compiled, maskSampler(128), null,
                    IrisImageMapApplication.BIOME, -64, null
            );

            assertEquals(new Color(12, 15, 22).getRGB(), below.renderInterpretedSnapshot(64, 32).getRGB(32, 16));
            assertEquals(
                    ImageMapStudioModel.targetColor("iris:test"),
                    above.renderInterpretedSnapshot(64, 32).getRGB(32, 16)
            );
        } finally {
            below.close();
            above.close();
        }
    }

    private static IrisImageMapMaskSampler maskSampler(int sample) {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        source.getRaster().setSample(0, 0, 0, sample);
        IrisImageMap definition = new IrisImageMap()
                .setSource("mask")
                .setType(IrisImageMapType.GRAYSCALE_MASK)
                .setOutOfBounds(IrisImageMapOutOfBounds.CLAMP);
        CompiledIrisImageMap compiled = CompiledIrisImageMap.compile(definition, new IrisImage(source, "png"));
        IrisImageMapMask mask = new IrisImageMapMask().setOperation(IrisImageMapMaskOperation.MULTIPLY);
        return new IrisImageMapMaskSampler(List.of(IrisImageMapMaskSampler.layer(compiled, mask)));
    }
}
