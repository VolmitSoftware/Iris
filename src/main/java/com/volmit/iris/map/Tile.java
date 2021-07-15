package com.volmit.iris.map;

import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.scaffold.stream.ProceduralStream;
import lombok.Getter;
import lombok.Setter;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.function.BiFunction;

public class Tile {

    @Getter
    private short x;
    @Getter
    private short y;

    @Getter
    private BufferedImage image;

    private Set<Integer> biomes;
    private Set<Integer> regions;



    @Getter
    @Setter
    private boolean dirty;

    @Getter
    private boolean rendering;

    public Tile(short x, short y) {
        this.x = x;
        this.y = y;
    }

    public boolean hasBiome(int biome) {
        return biomes.contains(biome);
    }

    public boolean hasRegion(int region) {
        return regions.contains(region);
    }

    /**
     * Render the tile
     * @param complex The world complex
     * @param type The type of render
     * @return True when rendered
     */
    public boolean render(IrisComplex complex, RenderType type) {
        BufferedImage newImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);

        BiFunction<Integer, Integer, Integer> getColor;
        if (type == RenderType.BIOME_LAND) {
            getColor = (x, z) -> complex.getLandBiomeStream().get(x, z).getColor().getRGB();
        } else if (type == RenderType.REGION) {
            getColor = (x, z) -> complex.getRegionStream().get(x, z).getColor(complex).getRGB();
        } else if (type == RenderType.HEIGHT) {
            getColor = (x, z) -> Color.getHSBColor(complex.getHeightStream().get(x, z).floatValue(), 100, 100).getRGB();
        } else {
            getColor = (x, z) -> complex.getCaveBiomeStream().get(x, z).getColor().getRGB();
        }

        for (int i = 0; i < 128; i++) {
            for (int j = 0; j < 128; j++) {
                newImage.setRGB(i, j, getColor.apply(translate(x, i), translate(y, j)));
            }
        }
        image = newImage;
        rendering = false;
        dirty = false;
        return true;
    }

    public static int translate(int section, int pixel) {
        return (section << 9) | (pixel << 2) | 2;
    }


}
