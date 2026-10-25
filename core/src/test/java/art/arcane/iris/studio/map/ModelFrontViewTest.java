package art.arcane.iris.studio.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class ModelFrontViewTest {
    private static final String SEA_PICKLE = """
            [
              { "from": [6, 0, 6], "to": [10, 6, 10], "faces": {
                  "up":    { "uv": [4, 1, 8, 5], "texture": "#all" },
                  "south": { "uv": [0, 5, 4, 11], "texture": "#all" } } },
              { "from": [6, 5.95, 6], "to": [10, 5.95, 10], "faces": {
                  "up": { "uv": [8, 1, 12, 5], "texture": "#all" } } },
              { "from": [7.5, 5.2, 8], "to": [8.5, 8.7, 8],
                "rotation": { "origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": true },
                "faces": { "south": { "uv": [3, 0, 1, 5], "texture": "#all" } } }
            ]
            """;
    private static final String PRESSURE_PLATE = """
            [
              { "from": [1, 0, 1], "to": [15, 1, 15], "faces": {
                  "up":    { "uv": [1, 1, 15, 15], "texture": "#all" },
                  "south": { "uv": [1, 15, 15, 16], "texture": "#all" } } }
            ]
            """;
    private static final Map<String, String> TEXTURES = Map.of("all", "block/test");
    private static final int SIZE = 16;

    private final BufferedImage texture = everyTexelItsOwnColour();

    @Test
    public void bodyShowsItsOwnPartOfTheTexture() throws Exception {
        ModelFrontView front = draw(SEA_PICKLE);

        assertEquals(litAsFront(0, 5), front.image().getRGB(6, 7));
        assertEquals(litAsFront(3, 10), front.image().getRGB(9, 12));
    }

    @Test
    public void topShowsAboveTheFrontOverThePlaneJustUnderIt() throws Exception {
        ModelFrontView front = draw(SEA_PICKLE);

        assertEquals(texture.getRGB(5, 2), front.image().getRGB(7, 5));
    }

    @Test
    public void crossedPlaneIsMirroredWhereItsUvRunsBackwards() throws Exception {
        ModelFrontView front = draw(SEA_PICKLE);

        assertEquals(litAsFront(1, 1), front.image().getRGB(8, 4));
    }

    @Test
    public void spotsNoBoxCoversStayClear() throws Exception {
        ModelFrontView front = draw(SEA_PICKLE);

        assertEquals(0, front.image().getRGB(2, 12) >>> 24);
        assertEquals(0, front.image().getRGB(7, 2) >>> 24);
    }

    @Test
    public void smallShapeLeavesMostOfItsSpotOpen() {
        assertFalse(ModelFrontView.fillsMostOfItsSpotFromAbove(elements(SEA_PICKLE)));
    }

    @Test
    public void flatShapeFillsMostOfItsSpot() {
        assertTrue(ModelFrontView.fillsMostOfItsSpotFromAbove(elements(PRESSURE_PLATE)));
    }

    private int litAsFront(int textureX, int textureY) {
        return 0xFF000000 | BlockPreviewColors.shade(texture.getRGB(textureX, textureY), CubeFace.SOUTH.shade());
    }

    private ModelFrontView draw(String elements) throws Exception {
        ModelFrontView front = ModelFrontView.draw(elements(elements), TEXTURES, name -> texture);
        assertNotNull(front);
        return front;
    }

    private static JsonArray elements(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    private static BufferedImage everyTexelItsOwnColour() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setRGB(x, y, 0xFF000000 | x << 16 | y << 8 | 0x40);
            }
        }
        return image;
    }
}
