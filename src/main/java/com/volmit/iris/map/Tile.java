package com.volmit.iris.map;

import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.scaffold.engine.Engine;
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
     * @param type The type of render
     * @return True when rendered
     */
    public boolean render(Engine engine, RenderType type) {
        BufferedImage newImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        BiFunction<Integer, Integer, Integer> colorFunction = (integer, integer2) -> Color.black.getRGB();

        switch (type) {
            case BIOME, DECORATOR_LOAD, OBJECT_LOAD, LAYER_LOAD -> colorFunction = (x, z) -> engine.getFramework().getComplex().getTrueBiomeStream().get(x, z).getColor(engine, type).getRGB();
            case BIOME_LAND -> colorFunction = (x, z) -> engine.getFramework().getComplex().getLandBiomeStream().get(x, z).getColor(engine, type).getRGB();
            case BIOME_SEA -> colorFunction = (x, z) -> engine.getFramework().getComplex().getSeaBiomeStream().get(x, z).getColor(engine, type).getRGB();
            case REGION -> colorFunction = (x, z) -> engine.getFramework().getComplex().getRegionStream().get(x, z).getColor(engine.getFramework().getComplex(), type).getRGB();
            case CAVE_LAND -> colorFunction = (x, z) -> engine.getFramework().getComplex().getCaveBiomeStream().get(x, z).getColor(engine, type).getRGB();
            case HEIGHT -> colorFunction = (x, z) -> Color.getHSBColor(engine.getFramework().getComplex().getHeightStream().get(x, z).floatValue(), 100, 100).getRGB();
        }

        for (int i = 0; i < 128; i++) {
            for (int j = 0; j < 128; j++) {
                newImage.setRGB(i, j, colorFunction.apply(translate(x, i), translate(y, j)));
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
