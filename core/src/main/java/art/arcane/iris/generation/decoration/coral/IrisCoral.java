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

package art.arcane.iris.generation.decoration.coral;

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

@Snippet("coral")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A single procedurally generated coral reef structure. Iris bakes a pool of deterministic variants from these settings and scatters them across the seafloor at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file. Mirrors the procedural tree system but tuned for underwater reefs: structural blocks are waterlogged so the coral stays alive, and placement defaults to anchoring on the terrain beneath the water surface.")
@Data
public class IrisCoral implements IrisProceduralPlacement {
    private final transient AtomicCache<KList<IrisObject>> variantCache = new AtomicCache<>();

    @Description("A human readable name used in logs and as the variant load key.")
    private String name = "coral";

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance per chunk for this coral to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.4;

    @MinNumber(1)
    @Description("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("How many distinct variants to pre-bake for this coral. Higher means more variety at a small memory cost.")
    private int variants = 6;

    @Description("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Description("The placement mode used to anchor the coral to the seafloor.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Description("Rotate this coral's placement.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Description("Limit the max or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Description("Whether this coral may place on the terrain surface, under carvings, or both.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

    @Description("If true (default for coral), the coral anchors on the terrain height beneath the water, so it grows up from the seafloor rather than floating at the water surface.")
    private boolean underwater = true;

    @Description("Translate (offset) this placement along each axis; for example set a negative y to sink it into the seafloor.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Description("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Description("Settings for the vacuum place modes (VACUUM, VACUUM_HIGH, VACUUM_FAST, VACUUM_ORGANIC).")
    private IrisVacuumSettings vacuumSettings;

    @Description("If true (default), every resolved coral block that is waterloggable is forced waterlogged so the coral does not die in water (coral fans, sea pickles and kelp also waterlog). Set false to place dead/dry coral.")
    private boolean waterlogged = true;

    @Description("The overall silhouette this coral grows into (branching, fan, brain, pillar, tendril). Each form runs a different generation routine.")
    private IrisCoralForm form = IrisCoralForm.BRANCHING;

    @Required
    @Description("The structural coral block (the living wood of the reef), e.g. minecraft:tube_coral_block. Ignored when blockPalette is set.")
    @RegistryListBlockType
    private String block = "minecraft:tube_coral_block";

    @Description("A noise-driven palette for the structural coral block. When set this overrides the single block, letting the reef mix tube/brain/bubble/fire/horn coral_block tones across its body.")
    private IrisMaterialPalette blockPalette = null;

    @Description("Optional tip block placed at branch tips and at the very top of the structure, e.g. coral fans or minecraft:sea_pickle. Ignored when tipPalette is set. Null disables tips.")
    @RegistryListBlockType
    private String tipBlock = null;

    @Description("A noise-driven palette for the tip block. When set this overrides the single tipBlock, letting tips mix fan / pickle decorations.")
    private IrisMaterialPalette tipPalette = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance per eligible tip position to place a tip block (coral fan / sea pickle).")
    private double tipChance = 0.6;

    @MinNumber(1)
    @Description("Minimum overall height of the coral in blocks.")
    private int heightMin = 4;

    @MinNumber(1)
    @Description("Maximum overall height of the coral in blocks.")
    private int heightMax = 8;

    @MinNumber(0)
    @Description("Horizontal spread of the structure in blocks. For BRANCHING this is how far arms reach out, for BRAIN/PILLAR/FAN it widens the base footprint.")
    private double spread = 3;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Lateral noise wobble (0-1) applied to branches and tendrils so they are not ruler-straight. 0 is perfectly straight, 1 is heavily wandering.")
    private double sway = 0.5;

    @MinNumber(1)
    @MaxNumber(12)
    @Description("BRANCHING only: how many arms sprout from the central stalk.")
    private int branchCount = 4;

    @MinNumber(1)
    @Description("BRANCHING only: the length of each arm in blocks before reaching its tip.")
    private double branchLength = 3;

    @MinNumber(0)
    @MaxNumber(90)
    @Description("BRANCHING only: the upward elevation in degrees of each arm from horizontal. 90 is straight up, 0 is flat out.")
    private double branchElevation = 55;

    @Description("BRANCHING only: how the arms are distributed around the stalk by azimuth.")
    private IrisCoralBranchAzimuth branchAzimuth = IrisCoralBranchAzimuth.GOLDEN_ANGLE;

    @Description("BRANCHING only: if true, each arm splits once into a small fan of sub-arms for a bushier reef.")
    private boolean subBranches = true;

    @MinNumber(1)
    @MaxNumber(5)
    @Description("BRANCHING only: how many sub-arms each arm splits into when subBranches is true.")
    private int subBranchCount = 2;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("BRANCHING only: the length of each sub-arm as a fraction of its parent arm.")
    private double subBranchScale = 0.5;

    @MinNumber(0)
    @MaxNumber(4)
    @Description("BRANCHING and PILLAR only: the radius of the small coral tip cluster grown at each tip.")
    private int tipClusterRadius = 1;

    @MinNumber(1)
    @MaxNumber(8)
    @Description("BRAIN only: the horizontal radius of the brain-coral blob in blocks.")
    private int brainRadius = 3;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("BRAIN only: how strongly 3D value noise perturbs the ellipsoid surface, for a wrinkled organic blob. 0 is a smooth dome.")
    private double brainRoughness = 0.35;

    @MinNumber(1)
    @MaxNumber(12)
    @Description("PILLAR only: the radius of the stout vertical column in blocks.")
    private int pillarRadius = 1;

    @MinNumber(1)
    @MaxNumber(8)
    @Description("FAN only: the half-width of the upright fan plane in blocks (the disc widens toward its mid-height).")
    private int fanWidth = 3;

    @MinNumber(1)
    @MaxNumber(12)
    @Description("TENDRIL only: how many thin wavy stalks rise from the base.")
    private int tendrilCount = 4;

    public KList<IrisObject> getVariantObjects(IrisData data) {
        return variantCache.aquire(() -> {
            KList<IrisObject> baked = new KList<>();
            int count = Math.max(1, variants);

            for (int i = 0; i < count; i++) {
                IrisObject object = CoralGenerator.generate(this, i, new RNG(seed + (i * 7919L)), data);
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
