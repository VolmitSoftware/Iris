package com.volmit.vanilla;

import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import java.util.Objects;
import java.util.Optional;

/** How the host fits the terrain around a structure before its pieces are placed, as packs write it:
 * {@code {"mode": "FORCE_CARVE", "horizontalPadding": 4, "shape": "ERODED"}}. The host carries the work out; a module
 * only describes it. A value the host creates with the no-argument constructor and reads the JSON into.
 */
@Desc("Terrain integration applied after a structure graph is assembled and before its pieces are placed.")
public final class StructureTerrain {
    private static final double DEFAULT_EROSION_STRENGTH = 0.8D;
    private static final double DEFAULT_EROSION_FREQUENCY = 0.07D;
    private static final double DEFAULT_LOBE_STRENGTH = 0.85D;

    /** What the host does to the terrain around the structure. */
    @Desc("Controls how terrain is integrated with a structure.")
    public enum Mode {
        @Desc("Replays the registered structure's own authored terrain adaptation.")
        SOURCE,
        @Desc("Disables all terrain integration.")
        PRESERVE,
        @Desc("Clears one straight box around the assembled pieces, expanded by the paddings.")
        BORE,
        @Desc("Clears the padded envelope in the configured carve shape.")
        FORCE_CARVE,
        @Desc("Raises surface terrain under rigid foundations with a 12-block falloff without lowering existing ground.")
        VACUUM,
        @Desc("Fills the padded piece volume with solid blocks before any piece is placed.")
        ENCASE
    }

    /** The shape {@link Mode#FORCE_CARVE} clears. */
    @Desc("Controls the shape of the expanded terrain carve around a structure.")
    public enum Shape {
        @Desc("The full expanded box with straight walls, ceiling and floor.")
        BOX,
        @Desc("A smooth rounded volume without erosion noise.")
        ROUNDED,
        @Desc("A rounded volume whose outer boundary is eroded by noise.")
        ERODED
    }

    @Desc("Terrain operation.")
    private Mode mode = Mode.SOURCE;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Horizontal clearance around the assembled pieces for BORE, FORCE_CARVE, and ENCASE.")
    private int horizontalPadding = 0;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Air clearance above the assembled pieces.")
    private int ceilingPadding = 0;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Terrain cleared below the assembled pieces. Zero preserves their supporting floor.")
    private int floorPadding = 0;

    @Desc("Shape used by FORCE_CARVE.")
    private Shape shape = Shape.BOX;

    @Desc("Block palette used by ENCASE. When unset, the host picks the dimension's own stone.")
    private PaletteSpec encasePalette = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Boundary erosion strength for ERODED force carving.")
    private double erosionStrength = DEFAULT_EROSION_STRENGTH;

    @MinNumber(0.001)
    @MaxNumber(1)
    @Desc("Boundary noise frequency for ERODED force carving.")
    private double erosionFrequency = DEFAULT_EROSION_FREQUENCY;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Low frequency lobe wavelength for ERODED force carving. Zero derives it from the erosion frequency.")
    private double lobeFrequency = 0D;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Fraction of the horizontal padding the lobes may remove for ERODED force carving.")
    private double lobeStrength = DEFAULT_LOBE_STRENGTH;

    private StructureTerrain() {
    }

    private StructureTerrain(StructureTerrain source) {
        mode = source.mode();
        horizontalPadding = source.horizontalPadding;
        ceilingPadding = source.ceilingPadding;
        floorPadding = source.floorPadding;
        shape = source.shape();
        encasePalette = source.encasePalette;
        erosionStrength = source.erosionStrength;
        erosionFrequency = source.erosionFrequency;
        lobeFrequency = source.lobeFrequency;
        lobeStrength = source.lobeStrength;
    }

    /** Terrain fitting in {@code mode} with every other option at its default. */
    public static StructureTerrain of(Mode mode) {
        StructureTerrain terrain = new StructureTerrain();
        terrain.mode = Objects.requireNonNull(mode, "mode");
        return terrain;
    }

    /** This fitting with clearance around the pieces: sideways, above and below, in blocks. */
    public StructureTerrain withPadding(int horizontal, int ceiling, int floor) {
        StructureTerrain terrain = new StructureTerrain(this);
        terrain.horizontalPadding = horizontal;
        terrain.ceilingPadding = ceiling;
        terrain.floorPadding = floor;
        return terrain;
    }

    public StructureTerrain withShape(Shape shape) {
        StructureTerrain terrain = new StructureTerrain(this);
        terrain.shape = Objects.requireNonNull(shape, "shape");
        return terrain;
    }

    public StructureTerrain withEncasePalette(PaletteSpec palette) {
        StructureTerrain terrain = new StructureTerrain(this);
        terrain.encasePalette = Objects.requireNonNull(palette, "palette");
        return terrain;
    }

    /** This fitting with the erosion of an {@link Shape#ERODED} boundary: its strength and noise frequency. */
    public StructureTerrain withErosion(double strength, double frequency) {
        StructureTerrain terrain = new StructureTerrain(this);
        terrain.erosionStrength = strength;
        terrain.erosionFrequency = frequency;
        return terrain;
    }

    /** This fitting with the low frequency lobes of an {@link Shape#ERODED} boundary. */
    public StructureTerrain withLobes(double frequency, double strength) {
        StructureTerrain terrain = new StructureTerrain(this);
        terrain.lobeFrequency = frequency;
        terrain.lobeStrength = strength;
        return terrain;
    }

    /** The terrain operation; {@link Mode#SOURCE} where the pack wrote none or one the host does not know. */
    public Mode mode() {
        return mode == null ? Mode.SOURCE : mode;
    }

    public int horizontalPadding() {
        return horizontalPadding;
    }

    public int ceilingPadding() {
        return ceilingPadding;
    }

    public int floorPadding() {
        return floorPadding;
    }

    /** The carve shape; {@link Shape#BOX} where the pack wrote none or one the host does not know. */
    public Shape shape() {
        return shape == null ? Shape.BOX : shape;
    }

    /** The blocks {@link Mode#ENCASE} fills with; empty for the dimension's own stone. */
    public Optional<PaletteSpec> encasePalette() {
        return Optional.ofNullable(encasePalette);
    }

    /** As the pack wrote it; the host clamps it to 0..1. */
    public double erosionStrength() {
        return erosionStrength;
    }

    /** As the pack wrote it; the host clamps it to 0.001..1. */
    public double erosionFrequency() {
        return erosionFrequency;
    }

    /** As the pack wrote it; zero or less lets the host derive it from the erosion frequency. */
    public double lobeFrequency() {
        return lobeFrequency;
    }

    /** As the pack wrote it; the host clamps it to 0..1. */
    public double lobeStrength() {
        return lobeStrength;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StructureTerrain that && mode() == that.mode()
                && horizontalPadding == that.horizontalPadding && ceilingPadding == that.ceilingPadding
                && floorPadding == that.floorPadding && shape() == that.shape()
                && Objects.equals(encasePalette, that.encasePalette)
                && Double.compare(erosionStrength, that.erosionStrength) == 0
                && Double.compare(erosionFrequency, that.erosionFrequency) == 0
                && Double.compare(lobeFrequency, that.lobeFrequency) == 0
                && Double.compare(lobeStrength, that.lobeStrength) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode(), horizontalPadding, ceilingPadding, floorPadding, shape(), encasePalette,
                erosionStrength, erosionFrequency, lobeFrequency, lobeStrength);
    }

    @Override
    public String toString() {
        return "StructureTerrain[" + mode() + ", padding " + horizontalPadding + "/" + ceilingPadding + "/" + floorPadding
                + ", " + shape() + "]";
    }
}
