package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.After;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectGlobalScaleTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final Gson gson = new Gson();

    @After
    public void clearCache() {
        IrisObjectScale.invalidate(null);
    }

    @Test
    public void defaultFactorPreservesObjectAndRandomSequence() {
        IrisObject origin = new IrisObject(8, 8, 8);
        IrisObjectPlacement placement = gson.fromJson("{}", IrisObjectPlacement.class);
        RNG actual = new RNG(731L);
        RNG expected = new RNG(731L);

        assertNull(placement.getScale());
        assertSame(origin, placement.scaleObject(actual, origin, new IrisDimension()));
        assertSame(origin, placement.scaleObject(actual, origin, null));
        assertEquals(expected.nextLong(), actual.nextLong());
        assertFalse(gson.toJson(placement).contains("\"scale\""));
        assertNull(placement.toPlacement("trees/oak").getScale());
    }

    @Test
    public void dimensionFactorsUseSeparateCachedVariantsWithoutChangingSourceOrRandomness() {
        IrisObject origin = new IrisObject(8, 8, 8);
        IrisObjectPlacement placement = new IrisObjectPlacement();
        IrisDimension large = new IrisDimension().setAllObjectScaleFactor(2D);
        IrisDimension small = new IrisDimension().setAllObjectScaleFactor(0.5D);
        RNG actual = new RNG(731L);
        RNG expected = new RNG(731L);

        IrisObject doubled = placement.scaleObject(actual, origin, large);
        IrisObject halved = placement.scaleObject(actual, origin, small);

        assertEquals(16, doubled.getW());
        assertEquals(4, halved.getW());
        assertEquals(8, origin.getW());
        assertNotSame(origin, doubled);
        assertNotSame(doubled, halved);
        assertSame(doubled, placement.scaleObject(actual, origin, large));
        assertSame(halved, placement.scaleObject(actual, origin, small));
        assertEquals(expected.nextLong(), actual.nextLong());
    }

    @Test
    public void globalAndExplicitFactorsScaleOccupiedCubeBoundsExactly() {
        IrisObject origin = new IrisObject(4, 4, 4);
        PlatformBlockState stone = mock(PlatformBlockState.class);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    origin.setUnsigned(x, y, z, stone);
                }
            }
        }
        IrisObjectPlacement inherited = new IrisObjectPlacement();
        IrisDimension doubled = new IrisDimension().setAllObjectScaleFactor(2D);
        assertOccupiedCube(inherited.scaleObject(new RNG(1), origin, doubled), 8);
        assertOccupiedCube(inherited.scaleObject(new RNG(1), origin,
                new IrisDimension().setAllObjectScaleFactor(0.5D)), 2);
        assertOccupiedCube(new IrisObjectPlacement().setScale(new IrisObjectScale().setSize(0.5D))
                .scaleObject(new RNG(1), origin, doubled), 2);
        assertOccupiedCube(new IrisObjectPlacement().setScale(new IrisObjectScale())
                .scaleObject(new RNG(1), origin, doubled), 4);
        assertOccupiedCube(origin, 4);
    }

    @Test
    public void disabledVariationRangeReportsTheUnscaledFootprint() {
        IrisObject origin = new IrisObject(4, 4, 4);
        IrisObjectScale scale = new IrisObjectScale().setMinimumScale(0.25D)
                .setMaximumScale(0.25D).setVariations(0);
        assertSame(origin, scale.get(new RNG(1), origin));
        assertEquals(1D, scale.getMaxScale(), 0D);
    }

    @Test
    public void explicitUnitAndEmptyScaleOverrideDimensionFactorAcrossJsonCopies() {
        IrisObject origin = new IrisObject(8, 8, 8);
        IrisDimension dimension = new IrisDimension().setAllObjectScaleFactor(2D);
        for (String json : new String[]{"{\"scale\":{}}", "{\"scale\":{\"size\":1.0}}"}) {
            IrisObjectPlacement placement = gson.fromJson(json, IrisObjectPlacement.class);
            IrisObjectPlacement copy = gson.fromJson(gson.toJson(placement.toPlacement("tree")), IrisObjectPlacement.class);

            assertSame(origin, copy.scaleObject(new RNG(1), origin, dimension));
            assertEquals(1D, copy.getMaximumScale(dimension), 0D);
        }
    }

    @Test
    public void referencedScaleSnippetRemainsAnExplicitOverride() throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path snippets = Files.createDirectories(root.resolve("snippet/object-scale"));
        Files.writeString(snippets.resolve("unit.json"), "{}");
        Files.writeString(snippets.resolve("half.json"), "{\"size\":0.5}");
        IrisData data = mock(IrisData.class, CALLS_REAL_METHODS);
        when(data.getDataFolder()).thenReturn(root.toFile());
        Gson loaderGson = new GsonBuilder().registerTypeAdapterFactory(data).create();
        IrisDimension dimension = new IrisDimension().setAllObjectScaleFactor(2D);
        IrisObject origin = new IrisObject(4, 4, 4);
        IrisObjectPlacement unit = loaderGson.fromJson(
                "{\"scale\":\"snippet/object-scale/unit\"}", IrisObjectPlacement.class);
        IrisObjectPlacement half = loaderGson.fromJson(
                "{\"scale\":\"snippet/object-scale/half\"}", IrisObjectPlacement.class);

        assertNotNull(unit.getScale());
        assertSame(origin, unit.scaleObject(new RNG(1), origin, dimension));
        assertEquals(2, half.scaleObject(new RNG(1), origin, dimension).getW());
        IrisObjectPlacement copied = loaderGson.fromJson(loaderGson.toJson(half.toPlacement("tree")),
                IrisObjectPlacement.class);
        assertEquals(0.5D, copied.getMaximumScale(dimension), 0D);
    }

    @Test
    public void explicitRangeKeepsItsExistingSelectionSequenceWithoutMultiplying() {
        IrisObject origin = new IrisObject(8, 8, 8);
        IrisObjectScale authored = new IrisObjectScale().setMinimumScale(0.5D)
                .setMaximumScale(1.5D).setVariations(4);
        IrisObjectPlacement placement = new IrisObjectPlacement().setScale(authored);
        IrisDimension dimension = new IrisDimension().setAllObjectScaleFactor(3D);
        RNG actual = new RNG(1337);
        RNG expected = new RNG(1337);

        for (int index = 0; index < 12; index++) {
            assertSame(authored.get(expected, origin), placement.scaleObject(actual, origin, dimension));
        }
        assertEquals(1.5D, placement.getMaximumScale(dimension), 0D);
        assertEquals(expected.nextLong(), actual.nextLong());
    }

    @Test
    public void explicitOppositeFactorWinsAndDirectSettersRejectUnsafeValues() {
        IrisObject origin = new IrisObject(8, 8, 8);
        IrisObjectPlacement placement = new IrisObjectPlacement().setScale(new IrisObjectScale().setSize(0.5D));
        assertSame(IrisObjectScale.getFixed(origin, 0.5D), placement.scaleObject(new RNG(1), origin,
                new IrisDimension().setAllObjectScaleFactor(2D)));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                0D, -1D, 0.009D, 50.001D}) {
            assertThrows(IllegalArgumentException.class, () -> new IrisDimension().setAllObjectScaleFactor(invalid));
            assertThrows(IllegalArgumentException.class, () -> new IrisObjectScale().setSize(invalid));
            assertThrows(IllegalArgumentException.class, () -> new IrisStaticObject().setScale(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> gson.fromJson(
                "{\"allObjectScaleFactor\":0}", IrisDimension.class).getAllObjectScaleFactor());
    }

    private static void assertOccupiedCube(IrisObject object, int width) {
        assertEquals(width, object.getW());
        assertEquals(width, object.getH());
        assertEquals(width, object.getD());
        assertEquals(width * width * width, object.getBlocks().size());
        int minimum = -(width / 2);
        for (int x = minimum; x < minimum + width; x++) {
            for (int y = minimum; y < minimum + width; y++) {
                for (int z = minimum; z < minimum + width; z++) {
                    assertNotNull(object.getBlocks().get(new IrisBlockVector(x, y, z)));
                }
            }
        }
        assertEquals(minimum, object.getAABB().min().getX());
        assertEquals(minimum + width - 1, object.getAABB().max().getX());
    }

}
