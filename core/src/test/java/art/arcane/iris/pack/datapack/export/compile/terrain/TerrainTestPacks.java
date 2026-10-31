package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.testsupport.CompatTestPlatform;

import java.io.File;
import java.io.IOException;

final class TerrainTestPacks {
    static final String DIMENSION = "main";
    static final String TERRAIN3D = """
            {"amplitude": 24, "horizontalScale": 64, "verticalScale": 16, "crackDepth": 12, "crackWidth": 3,
             "crackScale": 48, "minimumSlope": 0.1, "slopeFade": 0.3, "fluidClearance": 4, "fluidFade": 12}""";

    private TerrainTestPacks() {
    }

    static File twoBiomes(File pack, String generatorA, String generatorB) throws IOException {
        CompatTestPlatform.write(pack, "dimensions/" + DIMENSION + ".json", "{\"regions\":[\"region\"]}");
        CompatTestPlatform.write(pack, "regions/region.json", "{\"landBiomes\":[\"a\",\"b\"]}");
        CompatTestPlatform.write(pack, "biomes/a.json", "{\"name\":\"A\",\"generators\":[" + generatorA + "]}");
        CompatTestPlatform.write(pack, "biomes/b.json", "{\"name\":\"B\",\"generators\":[" + generatorB + "]}");
        return pack;
    }

    static void biome(File pack, String key, String json) throws IOException {
        CompatTestPlatform.write(pack, "biomes/" + key + ".json", json);
    }

    static void generator(File pack, String key, String json) throws IOException {
        CompatTestPlatform.write(pack, "generators/" + key + ".json", json);
    }

    static String singleLayer(String function, double radius, String style) {
        return "{\"interpolator\":{\"function\":\"" + function + "\",\"horizontalScale\":" + radius + "},"
                + "\"composite\":[{\"style\":{\"style\":\"" + style + "\"},\"seed\":3}]}";
    }

    static String twoLayers(String function, double radius) {
        return "{\"interpolator\":{\"function\":\"" + function + "\",\"horizontalScale\":" + radius + "},"
                + "\"composite\":[{\"style\":{\"style\":\"SIMPLEX\"},\"seed\":3},{\"style\":{\"style\":\"PERLIN\"},\"seed\":5,\"opacity\":0.5}]}";
    }

    static String link(String generator, int min, int max) {
        return "{\"generator\":\"" + generator + "\",\"min\":" + min + ",\"max\":" + max + "}";
    }
}
