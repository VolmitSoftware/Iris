/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modules.objects.crystal;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;
import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.Snippet;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Required;

import java.util.Optional;

@Snippet("iris/objects/crystal")
@Desc("A single procedurally generated crystal cluster: a budding base blob with faceted, tapered shards radiating from it (geode / amethyst-style). Iris bakes a pool of deterministic variants from these settings and scatters them at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file. Built for caves.")
public final class Crystal {
    @Desc("A human readable name used in logs and as the variant load key.")
    private String name = "crystal";

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance per chunk for this crystal cluster to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.2;

    @MinNumber(1)
    @Desc("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("How many distinct variants to pre-bake for this crystal cluster. Higher means more variety at a small memory cost.")
    private int variants = 6;

    @Desc("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Desc("The placement mode used to anchor the crystal cluster to the terrain.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Desc("Rotate this crystal cluster's placement.")
    private ObjectRotation rotation = new ObjectRotation();

    @Desc("Limit the max or min height of placement.")
    private ObjectLimit clamp = new ObjectLimit();

    @Desc("Whether this crystal cluster may place on the terrain surface, under carvings, or both. Crystals are meant for caves, so this defaults to CARVING_ONLY.")
    private CarvingMode carvingSupport = CarvingMode.CARVING_ONLY;

    @Desc("If true, the crystal cluster anchors on the terrain height ignoring the water surface.")
    private boolean underwater = false;

    @Desc("Translate (offset) this placement along each axis; for example set a negative y to sink it into the ground.")
    private ObjectTranslate translate = new ObjectTranslate();

    @Desc("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private StiltSettings stiltSettings;

    @Desc("Settings for the vacuum place modes (VACUUM, VACUUM_HIGH, VACUUM_FAST, VACUUM_ORGANIC).")
    private VacuumSettings vacuumSettings;

    @Desc("The surface this crystal grows from (FLOOR shards up, CEILING shards down, WALL shards outward). This orients the baked geometry only.")
    private CrystalSurface growthSurface = CrystalSurface.FLOOR;

    @Required
    @Desc("The primary crystal shard block, e.g. minecraft:amethyst_block. Ignored when blockPalette is set.")
    @Ref(Reference.BLOCK)
    private String block = "minecraft:amethyst_block";

    @Desc("A noise-driven palette for the crystal shards (a prismatic mix, e.g. amethyst_block, calcite, tinted_glass). When set this overrides the single block, letting the shards mix blocks by noise. Palette wins.")
    private PaletteSpec blockPalette;

    @Desc("Optional block placed at the very tip of each shard for a different colored or sparkling point (e.g. minecraft:amethyst_cluster or glowstone). Ignored when tipPalette is set. If unset and glow is true, a light-emitting block is sprinkled among the tips instead.")
    @Ref(Reference.BLOCK)
    private String tipBlock = null;

    @Desc("A noise-driven palette for the shard tips. When set this overrides the single tipBlock. Palette wins.")
    private PaletteSpec tipPalette;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance per shard that its very tip is replaced by the tip block (or glow block).")
    private double tipChance = 0.6;

    @Desc("If true and no tipBlock/tipPalette is set, sprinkle a light-emitting block (the glowBlock) among the shard tips for a sparkling geode.")
    private boolean glow = false;

    @Desc("The light-emitting block sprinkled among the tips when glow is true and no tip block is configured.")
    @Ref(Reference.BLOCK)
    private String glowBlock = "minecraft:glowstone";

    @Desc("Optional block for the budding base blob the shards grow from, e.g. minecraft:budding_amethyst or calcite. Ignored when basePalette is set. If unset, the base is built from the primary shard block.")
    @Ref(Reference.BLOCK)
    private String baseBlock = "minecraft:budding_amethyst";

    @Desc("A noise-driven palette for the budding base blob. When set this overrides the single baseBlock. Palette wins.")
    private PaletteSpec basePalette;

    @MinNumber(0)
    @Desc("Radius in blocks of the budding base blob the shards radiate from. 0 places no base blob (shards spring from a single point).")
    private double baseRadius = 1.6;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How strongly value noise perturbs the surface of the base blob, for a lumpy organic budding base rather than a clean sphere.")
    private double baseNoise = 0.35;

    @MinNumber(1)
    @Desc("Minimum number of shards radiating from the base.")
    private int shardCountMin = 5;

    @MinNumber(1)
    @Desc("Maximum number of shards radiating from the base.")
    private int shardCountMax = 11;

    @MinNumber(1)
    @Desc("Minimum length in blocks of a shard, measured from the base out to its pointed tip.")
    private int shardLengthMin = 3;

    @MinNumber(1)
    @Desc("Maximum length in blocks of a shard, measured from the base out to its pointed tip.")
    private int shardLengthMax = 8;

    @MinNumber(0.5)
    @Desc("Starting radius in blocks of a shard at its thick base end. The shard tapers from this down to a single block at the tip.")
    private double shardBaseRadius = 1.4;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How aggressively each shard narrows from base to tip. 0 keeps a near-constant column; 1 tapers fully to a sharp point at the very tip. Each shard always ends in a single pointed block.")
    private double shardTaper = 0.85;

    @MinNumber(0)
    @MaxNumber(90)
    @Desc("The half-angle in degrees of the cone the shards fan within around the surface normal. 0 makes all shards parallel to the normal; larger values splay them outward into a starburst.")
    private double spreadAngle = 45;

    @Desc("How the shard azimuths are distributed around the normal (RANDOM for a chaotic clump, GOLDEN_ANGLE for an evenly spaced rosette).")
    private CrystalDistribution distribution = CrystalDistribution.GOLDEN_ANGLE;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Per-shard angular randomness (0-1) added on top of the chosen distribution so the cluster never looks mechanically regular. 0 is perfectly distributed; 1 is heavily jittered.")
    private double jitter = 0.25;

    public Crystal() {
    }

    public String name() {
        return name;
    }

    public double chance() {
        return chance;
    }

    public int density() {
        return density;
    }

    public int variants() {
        return variants;
    }

    public long seed() {
        return seed;
    }

    public ObjectPlaceMode mode() {
        return mode == null ? ObjectPlaceMode.CENTER_HEIGHT : mode;
    }

    public ObjectRotation rotation() {
        return rotation;
    }

    public ObjectLimit clamp() {
        return clamp;
    }

    public CarvingMode carvingSupport() {
        return carvingSupport == null ? CarvingMode.CARVING_ONLY : carvingSupport;
    }

    public boolean underwater() {
        return underwater;
    }

    public ObjectTranslate translate() {
        return translate;
    }

    /** Empty where the pack sets none. */
    public Optional<StiltSettings> stiltSettings() {
        return Optional.ofNullable(stiltSettings);
    }

    /** Empty where the pack sets none. */
    public Optional<VacuumSettings> vacuumSettings() {
        return Optional.ofNullable(vacuumSettings);
    }

    public CrystalSurface growthSurface() {
        return growthSurface == null ? CrystalSurface.FLOOR : growthSurface;
    }

    public String block() {
        return block;
    }

    /** Empty where the pack sets none. */
    public Optional<PaletteSpec> blockPalette() {
        return Optional.ofNullable(blockPalette);
    }

    /** Empty where the pack sets none. */
    public Optional<String> tipBlock() {
        return Optional.ofNullable(tipBlock);
    }

    /** Empty where the pack sets none. */
    public Optional<PaletteSpec> tipPalette() {
        return Optional.ofNullable(tipPalette);
    }

    public double tipChance() {
        return tipChance;
    }

    public boolean glow() {
        return glow;
    }

    public String glowBlock() {
        return glowBlock;
    }

    public String baseBlock() {
        return baseBlock;
    }

    /** Empty where the pack sets none. */
    public Optional<PaletteSpec> basePalette() {
        return Optional.ofNullable(basePalette);
    }

    public double baseRadius() {
        return baseRadius;
    }

    public double baseNoise() {
        return baseNoise;
    }

    public int shardCountMin() {
        return shardCountMin;
    }

    public int shardCountMax() {
        return shardCountMax;
    }

    public int shardLengthMin() {
        return shardLengthMin;
    }

    public int shardLengthMax() {
        return shardLengthMax;
    }

    public double shardBaseRadius() {
        return shardBaseRadius;
    }

    public double shardTaper() {
        return shardTaper;
    }

    public double spreadAngle() {
        return spreadAngle;
    }

    public CrystalDistribution distribution() {
        return distribution == null ? CrystalDistribution.GOLDEN_ANGLE : distribution;
    }

    public double jitter() {
        return jitter;
    }
}
