/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.decoration.crystal;

import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.decoration.IrisStiltSettings;
import art.arcane.iris.generation.decoration.IrisVacuumSettings;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectLimit;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.structure.object.ObjectPlaceMode;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("crystal")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A single procedurally generated crystal cluster: a budding base blob with faceted, tapered shards radiating from it (geode / amethyst-style). Iris bakes a pool of deterministic variants from these settings and scatters them at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file. Built for caves.")
@Data
public class IrisCrystal implements IrisProceduralPlacement {
    private final transient AtomicCache<KList<IrisObject>> variantCache = new AtomicCache<>();

    @Description("A human readable name used in logs and as the variant load key.")
    private String name = "crystal";

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance per chunk for this crystal cluster to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.2;

    @MinNumber(1)
    @Description("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("How many distinct variants to pre-bake for this crystal cluster. Higher means more variety at a small memory cost.")
    private int variants = 6;

    @Description("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Description("The placement mode used to anchor the crystal cluster to the terrain.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Description("Rotate this crystal cluster's placement.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Description("Limit the max or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Description("Whether this crystal cluster may place on the terrain surface, under carvings, or both. Crystals are meant for caves, so this defaults to CARVING_ONLY.")
    private CarvingMode carvingSupport = CarvingMode.CARVING_ONLY;

    @Description("If true, the crystal cluster anchors on the terrain height ignoring the water surface.")
    private boolean underwater = false;

    @Description("Translate (offset) this placement along each axis; for example set a negative y to sink it into the ground.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Description("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Description("Settings for the vacuum place modes (VACUUM, VACUUM_HIGH, VACUUM_FAST, VACUUM_ORGANIC).")
    private IrisVacuumSettings vacuumSettings;

    @Description("The surface this crystal grows from (FLOOR shards up, CEILING shards down, WALL shards outward). This orients the baked geometry only.")
    private IrisCrystalSurface growthSurface = IrisCrystalSurface.FLOOR;

    @Required
    @Description("The primary crystal shard block, e.g. minecraft:amethyst_block. Ignored when blockPalette is set.")
    @RegistryListBlockType
    private String block = "minecraft:amethyst_block";

    @Description("A noise-driven palette for the crystal shards (a prismatic mix, e.g. amethyst_block, calcite, tinted_glass). When set this overrides the single block, letting the shards mix blocks by noise. Palette wins.")
    private IrisMaterialPalette blockPalette = null;

    @Description("Optional block placed at the very tip of each shard for a different colored or sparkling point (e.g. minecraft:amethyst_cluster or glowstone). Ignored when tipPalette is set. If unset and glow is true, a light-emitting block is sprinkled among the tips instead.")
    @RegistryListBlockType
    private String tipBlock = null;

    @Description("A noise-driven palette for the shard tips. When set this overrides the single tipBlock. Palette wins.")
    private IrisMaterialPalette tipPalette = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance per shard that its very tip is replaced by the tip block (or glow block).")
    private double tipChance = 0.6;

    @Description("If true and no tipBlock/tipPalette is set, sprinkle a light-emitting block (the glowBlock) among the shard tips for a sparkling geode.")
    private boolean glow = false;

    @Description("The light-emitting block sprinkled among the tips when glow is true and no tip block is configured.")
    @RegistryListBlockType
    private String glowBlock = "minecraft:glowstone";

    @Description("Optional block for the budding base blob the shards grow from, e.g. minecraft:budding_amethyst or calcite. Ignored when basePalette is set. If unset, the base is built from the primary shard block.")
    @RegistryListBlockType
    private String baseBlock = "minecraft:budding_amethyst";

    @Description("A noise-driven palette for the budding base blob. When set this overrides the single baseBlock. Palette wins.")
    private IrisMaterialPalette basePalette = null;

    @MinNumber(0)
    @Description("Radius in blocks of the budding base blob the shards radiate from. 0 places no base blob (shards spring from a single point).")
    private double baseRadius = 1.6;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("How strongly value noise perturbs the surface of the base blob, for a lumpy organic budding base rather than a clean sphere.")
    private double baseNoise = 0.35;

    @MinNumber(1)
    @Description("Minimum number of shards radiating from the base.")
    private int shardCountMin = 5;

    @MinNumber(1)
    @Description("Maximum number of shards radiating from the base.")
    private int shardCountMax = 11;

    @MinNumber(1)
    @Description("Minimum length in blocks of a shard, measured from the base out to its pointed tip.")
    private int shardLengthMin = 3;

    @MinNumber(1)
    @Description("Maximum length in blocks of a shard, measured from the base out to its pointed tip.")
    private int shardLengthMax = 8;

    @MinNumber(0.5)
    @Description("Starting radius in blocks of a shard at its thick base end. The shard tapers from this down to a single block at the tip.")
    private double shardBaseRadius = 1.4;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("How aggressively each shard narrows from base to tip. 0 keeps a near-constant column; 1 tapers fully to a sharp point at the very tip. Each shard always ends in a single pointed block.")
    private double shardTaper = 0.85;

    @MinNumber(0)
    @MaxNumber(90)
    @Description("The half-angle in degrees of the cone the shards fan within around the surface normal. 0 makes all shards parallel to the normal; larger values splay them outward into a starburst.")
    private double spreadAngle = 45;

    @Description("How the shard azimuths are distributed around the normal (RANDOM for a chaotic clump, GOLDEN_ANGLE for an evenly spaced rosette).")
    private IrisCrystalDistribution distribution = IrisCrystalDistribution.GOLDEN_ANGLE;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Per-shard angular randomness (0-1) added on top of the chosen distribution so the cluster never looks mechanically regular. 0 is perfectly distributed; 1 is heavily jittered.")
    private double jitter = 0.25;

    public KList<IrisObject> getVariantObjects(IrisData data) {
        return variantCache.aquire(() -> {
            KList<IrisObject> baked = new KList<>();
            int count = Math.max(1, variants);

            for (int i = 0; i < count; i++) {
                IrisObject object = CrystalGenerator.generate(this, i, new RNG(seed + (i * 7919L)), data);
                if (object == null || object.getBlocks().isEmpty()) {
                    continue;
                }
                object.setLoadKey("procedural/" + name + "#" + i);
                object.setLoader(data);
                baked.add(object);
            }

            return baked;
        });
    }

    public IrisObject getVariantObject(IrisData data, RNG rng) {
        KList<IrisObject> baked = getVariantObjects(data);
        if (baked == null || baked.isEmpty()) {
            return null;
        }
        return baked.get(rng.i(baked.size()));
    }

    public IrisObjectPlacement asPlacement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(mode);
        placement.setRotation(rotation);
        placement.setClamp(clamp);
        placement.setCarvingSupport(carvingSupport);
        placement.setUnderwater(underwater);
        placement.setTranslate(translate);
        placement.setStiltSettings(stiltSettings);
        placement.setVacuumSettings(vacuumSettings);
        placement.setChance(chance);
        placement.setDensity(density);
        return placement;
    }

    public boolean isPlausible() {
        return false;
    }
}
