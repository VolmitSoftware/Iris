package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisCavePools;
import art.arcane.iris.generation.hydrology.config.IrisFluidProfile;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyDeformation;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyDrainage;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyLakes;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPonds;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyTier;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyUnderground;
import art.arcane.iris.generation.hydrology.config.IrisPondDams;
import art.arcane.iris.generation.hydrology.config.IrisPondNatural;
import art.arcane.iris.generation.hydrology.config.IrisPondSeeded;
import art.arcane.iris.generation.hydrology.config.IrisPondShore;
import art.arcane.iris.generation.hydrology.config.IrisSeaInlets;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HydrologySettings {
    private static final Gson PALETTE_JSON = new Gson();
    private static final int MAX_BANK_BAND = 64;
    public static final float MAX_HALF_WIDTH = 64f;
    private static final double MIN_INLET_HALF_WIDTH = 1D;
    private static final double MAX_INLET_HALF_WIDTH = 16D;
    private static final int MIN_INLET_DEPTH = 2;
    private static final int MAX_INLET_DEPTH = 16;
    private static final int MAX_INLET_HEADROOM = 32;
    private static final int MAX_INLET_CHAMBER = 32;
    private static final int INLET_LAST_STEP = (int) Math.ceil(WindowPlanner.CELL * Math.sqrt(2D));
    private static final int INLET_SHELL = SeaInletRasterizer.SHELL;
    public static final int MAX_CAVE_POOL_RADIUS = 15;

    private final long seed;
    private final int seaLevel;
    private final int minY;
    private final int maxY;
    private final boolean enabled;
    private final Drainage drainage;
    private final List<HydrologyTier> tiers;
    private final Ponds ponds;
    private final Lakes lakes;
    private final Deformation deformation;
    private final Underground underground;
    private final Map<String, IrisMaterialPalette> fluids;
    private final int prefetchRings;
    private final boolean persist;
    private final long fingerprint;

    private HydrologySettings(IrisHydrology config, long seed, int seaLevel, int minY, int maxY) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.minY = minY;
        this.maxY = maxY;
        this.enabled = config.isEnabled();
        this.drainage = drainage(config.getDrainage());
        this.tiers = tiers(config.getTiers());
        this.ponds = ponds(config.getPonds());
        this.lakes = lakes(config.getLakes());
        this.deformation = deformation(config.getDeformation());
        this.underground = underground(config.getUnderground());
        this.fluids = fluids(config.getFluids());
        this.prefetchRings = config.getPrefetchRings();
        this.persist = config.isPersist();
        requireFluidReferences();
        this.fingerprint = computeFingerprint();
    }

    public static HydrologySettings compile(IrisHydrology config, long seed, int seaLevel, int minY, int maxY) {
        return new HydrologySettings(Objects.requireNonNull(config, "config"), seed, seaLevel, minY, maxY);
    }

    public long fingerprint() {
        return fingerprint;
    }

    public List<HydrologyTier> tiers() {
        return tiers;
    }

    public HydrologyTier majorTier() {
        return tiers.get(tiers.size() - 1);
    }

    public HydrologyTier tierFor(float catchment) {
        for (int index = tiers.size() - 1; index > 0; index--) {
            HydrologyTier tier = tiers.get(index);
            if (catchment >= tier.minCatchment()) {
                return tier;
            }
        }
        return tiers.get(0);
    }

    public long seed() {
        return seed;
    }

    public int seaLevel() {
        return seaLevel;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public boolean enabled() {
        return enabled;
    }

    public Drainage drainage() {
        return drainage;
    }

    public Ponds ponds() {
        return ponds;
    }

    public Lakes lakes() {
        return lakes;
    }

    public Deformation deformation() {
        return deformation;
    }

    public Underground underground() {
        return underground;
    }

    public Map<String, IrisMaterialPalette> fluids() {
        return fluids;
    }

    public int prefetchRings() {
        return prefetchRings;
    }

    public boolean persist() {
        return persist;
    }

    private static Drainage drainage(IrisHydrologyDrainage config) {
        return new Drainage((float) config.getFillEpsilon(), config.getBreachDepth(), config.isWetnessThinning(),
                (float) config.getMinimumHeadKeep(), (float) config.getHeadRelief(), (float) config.getSaturationCatchment());
    }

    private static List<HydrologyTier> tiers(List<IrisHydrologyTier> configs) {
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("Hydrology needs at least one tier");
        }
        List<HydrologyTier> compiled = new ArrayList<>(configs.size());
        for (int index = 0; index < configs.size(); index++) {
            compiled.add(tier(configs.get(index), "tiers[" + index + "]"));
        }
        compiled.sort(Comparator.comparingDouble(HydrologyTier::minCatchment));
        requireDistinctCatchments(compiled);
        return List.copyOf(compiled);
    }

    private static void requireDistinctCatchments(List<HydrologyTier> sorted) {
        for (int index = 1; index < sorted.size(); index++) {
            HydrologyTier lower = sorted.get(index - 1);
            HydrologyTier upper = sorted.get(index);
            if (lower.minCatchment() == upper.minCatchment()) {
                throw new IllegalArgumentException("Hydrology tiers '" + lower.name() + "' and '" + upper.name()
                        + "' share minCatchment " + upper.minCatchment() + "; every tier needs its own minCatchment");
            }
        }
    }

    private static HydrologyTier tier(IrisHydrologyTier config, String field) {
        FloatRange halfWidth = floatRange(config.getHalfWidth(), field + ".halfWidth", RangeFloor.POSITIVE);
        if (halfWidth.max() > MAX_HALF_WIDTH) {
            throw new IllegalArgumentException("Hydrology range " + field + ".halfWidth must not go above " + MAX_HALF_WIDTH + ", got "
                    + config.getHalfWidth());
        }
        if (config.getBankBand() < 0 || config.getBankBand() > MAX_BANK_BAND) {
            throw new IllegalArgumentException("Hydrology " + field + ".bankBand must stay within 0.." + MAX_BANK_BAND + ", got "
                    + config.getBankBand());
        }
        return new HydrologyTier(config.getName(), (float) config.getMinCatchment(), halfWidth.min(), halfWidth.max(),
                (float) config.getWidthFactor(), (float) config.getDepthRatio(), config.getInset(), config.isCascades(),
                config.getWaterfallMinimumDrop(), config.getBankBand(), config.getBankStyle(), config.isDeltas(),
                materials(config.getMaterials(), field + ".materials"), config.getFluid(),
                decorators(config.getDecorators(), field + ".decorators"));
    }

    private static Ponds ponds(IrisHydrologyPonds config) {
        IrisPondDams dams = config.getDams();
        return new Ponds(naturalPonds(config.getNatural()), seededPonds(config.getSeeded()),
                new PondDams(dams.isEnabled(), dams.getMaxRaise()),
                intRange(config.getFreeboard(), "ponds.freeboard", RangeFloor.NON_NEGATIVE),
                config.getClimateDrawdown(), pondShore(config.getShore()), materials(config.getMaterials(), "ponds.materials"), config.getFluid(),
                decorators(config.getDecorators(), "ponds.decorators"));
    }

    private static NaturalPonds naturalPonds(IrisPondNatural config) {
        requireAreaOrder(config.getMinArea(), config.getMaxArea(), "ponds.natural");
        return new NaturalPonds(config.isEnabled(), (float) config.getKeep(), config.getMinArea(), config.getMaxArea(),
                (float) config.getMinDepth());
    }

    private static SeededPonds seededPonds(IrisPondSeeded config) {
        return new SeededPonds(config.isEnabled(), (float) config.getDensity(),
                intRange(config.getRadius(), "ponds.seeded.radius", RangeFloor.POSITIVE),
                intRange(config.getDepth(), "ponds.seeded.depth", RangeFloor.POSITIVE),
                (float) config.getMaxSlope(), config.isAlongChannels(),
                intRange(config.getChannelSpacing(), "ponds.seeded.channelSpacing", RangeFloor.POSITIVE));
    }

    private static PondShore pondShore(IrisPondShore config) {
        return new PondShore((float) config.getRoundness(), config.getShelf(), config.getTaper(), config.getMaxDepth());
    }

    private static Lakes lakes(IrisHydrologyLakes config) {
        requireAreaOrder(config.getMinArea(), config.getMaxArea(), "lakes");
        return new Lakes(config.isEnabled(), config.getMinArea(), config.getMaxArea());
    }

    private static Deformation deformation(IrisHydrologyDeformation config) {
        return new Deformation(config.getMaxFill(), (float) config.getBankSlope(), (float) config.getBankSteepFactor(),
                (float) config.getBankNoise(), config.isCaveMask());
    }

    private Underground underground(IrisHydrologyUnderground config) {
        return new Underground(seaInlets(config.getSeaInlets()), cavePools(config.getCavePools()));
    }

    private SeaInlets seaInlets(IrisSeaInlets config) {
        SeaInlets inlets = new SeaInlets(config.isEnabled(), config.getSpacing(), config.getMinimumCliffHeight(),
                intRange(config.getLength(), "underground.seaInlets.length", RangeFloor.POSITIVE),
                floatRange(config.getHalfWidth(), "underground.seaInlets.halfWidth", RangeFloor.POSITIVE),
                intRange(config.getDepth(), "underground.seaInlets.depth", RangeFloor.POSITIVE),
                intRange(config.getHeadroom(), "underground.seaInlets.headroom", RangeFloor.NON_NEGATIVE),
                config.getRoofClearance(),
                intRange(config.getChamberRadius(), "underground.seaInlets.chamberRadius", RangeFloor.POSITIVE));
        requireWithin(inlets.halfWidth().min(), MIN_INLET_HALF_WIDTH, MAX_INLET_HALF_WIDTH, "underground.seaInlets.halfWidth", config.getHalfWidth());
        requireWithin(inlets.halfWidth().max(), MIN_INLET_HALF_WIDTH, MAX_INLET_HALF_WIDTH, "underground.seaInlets.halfWidth", config.getHalfWidth());
        requireWithin(inlets.depth().min(), MIN_INLET_DEPTH, MAX_INLET_DEPTH, "underground.seaInlets.depth", config.getDepth());
        requireWithin(inlets.depth().max(), MIN_INLET_DEPTH, MAX_INLET_DEPTH, "underground.seaInlets.depth", config.getDepth());
        requireWithin(inlets.headroom().max(), 0, MAX_INLET_HEADROOM, "underground.seaInlets.headroom", config.getHeadroom());
        requireWithin(inlets.chamberRadius().max(), 1, MAX_INLET_CHAMBER, "underground.seaInlets.chamberRadius", config.getChamberRadius());
        if (inlets.enabled() && seaLevel > minY && seaLevel - inlets.depth().max() - INLET_SHELL - 1 <= minY) {
            throw new IllegalArgumentException("Hydrology underground.seaInlets.depth max " + inlets.depth().max()
                    + " puts the inlet floor and its seal below the world bottom " + minY + " (sea level " + seaLevel + ")");
        }
        long extent = (long) inlets.length().max() + INLET_LAST_STEP + Math.max((long) Math.ceil(inlets.halfWidth().max()),
                inlets.chamberRadius().max()) + 1L + INLET_SHELL;
        if (extent > WindowKey.SIZE) {
            throw new IllegalArgumentException("Hydrology underground.seaInlets reach " + extent + " blocks from the coast (length max "
                    + inlets.length().max() + " + " + INLET_LAST_STEP + " for the last step + the wider of halfWidth max and chamberRadius max"
                    + " + " + (1 + INLET_SHELL) + " for the seal); an inlet must stay within " + WindowKey.SIZE + " blocks");
        }
        return inlets;
    }

    private static void requireWithin(double value, double min, double max, String field, List<? extends Number> written) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Hydrology range " + field + " must stay within " + min + ".." + max + ", got " + written);
        }
    }

    private static CavePools cavePools(IrisCavePools config) {
        requireAreaOrder(config.getMinArea(), config.getMaxArea(), "underground.cavePools");
        if (config.isEnabled() && (config.getRadius() < 1 || config.getRadius() > MAX_CAVE_POOL_RADIUS)) {
            throw new IllegalArgumentException("Hydrology underground.cavePools.radius must stay within 1.." + MAX_CAVE_POOL_RADIUS
                    + ", got " + config.getRadius());
        }
        if (config.isEnabled() && config.getMaxDepth() < 1) {
            throw new IllegalArgumentException("Hydrology underground.cavePools.maxDepth must be at least 1, got " + config.getMaxDepth());
        }
        return new CavePools(config.isEnabled(), (float) config.getDensity(), config.getMinArea(), config.getMaxArea(),
                config.getMaxDepth(), config.getRadius(),
                intRange(config.getFreeboard(), "underground.cavePools.freeboard", RangeFloor.NON_NEGATIVE),
                config.getFluid());
    }

    private static Map<String, IrisMaterialPalette> fluids(List<IrisFluidProfile> profiles) {
        Map<String, IrisMaterialPalette> compiled = new LinkedHashMap<>();
        for (int index = 0; index < profiles.size(); index++) {
            IrisFluidProfile profile = profiles.get(index);
            String field = "fluids[" + index + "]";
            if (compiled.containsKey(profile.getName())) {
                throw new IllegalArgumentException("Duplicate hydrology fluid profile '" + profile.getName() + "' in " + field + ".name");
            }
            compiled.put(profile.getName(), requirePalette(profile, field + ".palette"));
        }
        return Collections.unmodifiableMap(compiled);
    }

    private static IrisMaterialPalette requirePalette(IrisFluidProfile profile, String field) {
        IrisMaterialPalette palette = profile.getPalette();
        if (palette == null || unwritten(palette)) {
            throw new IllegalArgumentException("Hydrology fluid profile '" + profile.getName() + "' has no blocks in " + field
                    + ": its \"palette\" list is missing, misspelt or empty; write it as a palette object such as"
                    + " {\"palette\": [{\"block\": \"minecraft:water\"}]}");
        }
        return copy(palette, IrisMaterialPalette.class);
    }

    private static IrisHydrologyMaterials materials(IrisHydrologyMaterials config, String field) {
        if (config == null) {
            return null;
        }
        requireWrittenPalette(config.getBed(), field + ".bed");
        requireWrittenPalette(config.getShore(), field + ".shore");
        requireWrittenPalette(config.getBank(), field + ".bank");
        return copy(config, IrisHydrologyMaterials.class);
    }

    private static List<IrisDecorator> decorators(List<IrisDecorator> configs, String field) {
        requireDecorators(configs, "Hydrology " + field);
        if (configs == null) {
            return List.of();
        }
        List<IrisDecorator> compiled = new ArrayList<>(configs.size());
        for (IrisDecorator decorator : configs) {
            compiled.add(copy(decorator, IrisDecorator.class));
        }
        return List.copyOf(compiled);
    }

    public static void requireDecorators(List<IrisDecorator> decorators, String path) {
        if (decorators == null) {
            return;
        }
        for (int index = 0; index < decorators.size(); index++) {
            requireDecorator(decorators.get(index), path + "[" + index + "]");
        }
    }

    private static void requireDecorator(IrisDecorator decorator, String path) {
        if (decorator == null) {
            throw new IllegalArgumentException(path + " is empty; write it as a decorator object");
        }
        IrisDecorationPart part = decorator.getPartOf();
        boolean water = part == IrisDecorationPart.SEA_FLOOR || part == IrisDecorationPart.SEA_SURFACE || part == IrisDecorationPart.SHORE_LINE;
        if (!water) {
            throw new IllegalArgumentException(path + ".partOf is " + part + "; water decorators grow on SEA_FLOOR (the bed under the water),"
                    + " SEA_SURFACE (still water) or SHORE_LINE (dry ground beside the water)");
        }
        if (decorator.getPalette() == null || decorator.getPalette().isEmpty()) {
            throw new IllegalArgumentException(path + ".palette has no blocks: its list is empty;"
                    + " write it as [{\"block\": \"minecraft:seagrass\"}]");
        }
        if (new IrisDecorator().getPalette().equals(decorator.getPalette())) {
            throw new IllegalArgumentException(path + ".palette is missing or misspelt, so it would grow the default 'grass';"
                    + " write it as [{\"block\": \"minecraft:seagrass\"}], naming grass as minecraft:short_grass");
        }
        requireBlockNames(decorator.getPalette(), path + ".palette");
        requireBlockNames(decorator.getTopPalette(), path + ".topPalette");
        requireBlockNames(decorator.getWhitelist(), path + ".whitelist");
        requireBlockNames(decorator.getBlacklist(), path + ".blacklist");
        if (decorator.getForceBlock() != null) {
            throw new IllegalArgumentException(path + ".forceBlock is not allowed: it would replace the ground that holds the water");
        }
        if (!(decorator.getChance() >= 0D && decorator.getChance() <= 1D)) {
            throw new IllegalArgumentException(path + ".chance must stay within 0..1, got " + decorator.getChance());
        }
        if (decorator.getStackMin() < 1 || decorator.getStackMin() > decorator.getStackMax()) {
            throw new IllegalArgumentException(path + " needs 1 <= stackMin <= stackMax, got stackMin " + decorator.getStackMin()
                    + " and stackMax " + decorator.getStackMax());
        }
    }

    private static void requireBlockNames(List<IrisBlockData> blocks, String path) {
        if (blocks == null) {
            return;
        }
        for (int index = 0; index < blocks.size(); index++) {
            IrisBlockData block = blocks.get(index);
            if (block == null || block.getBlock() == null || block.getBlock().isBlank()) {
                throw new IllegalArgumentException(path + "[" + index + "] names no block");
            }
        }
    }

    private static void requireWrittenPalette(IrisMaterialPalette palette, String field) {
        if (palette != null && unwritten(palette)) {
            throw new IllegalArgumentException("Hydrology " + field + " has no blocks: its \"palette\" list is missing, misspelt or empty;"
                    + " write it as {\"palette\": [{\"block\": \"minecraft:sand\"}]} or leave it unset to keep the biome's layers");
        }
    }

    private static boolean unwritten(IrisMaterialPalette palette) {
        return palette.getPalette() == null || palette.getPalette().isEmpty() || palette.isFallbackPalette();
    }

    private static <T> T copy(T value, Class<T> type) {
        return PALETTE_JSON.fromJson(PALETTE_JSON.toJson(value), type);
    }

    private static void requireAreaOrder(int minArea, int maxArea, String field) {
        if (minArea > maxArea) {
            throw new IllegalArgumentException("Hydrology " + field + ".minArea " + minArea + " is above " + field + ".maxArea " + maxArea);
        }
    }

    private static FloatRange floatRange(List<Double> values, String field, RangeFloor floor) {
        requirePair(values, field, floor);
        return new FloatRange(values.get(0).floatValue(), values.get(1).floatValue());
    }

    private static IntRange intRange(List<Integer> values, String field, RangeFloor floor) {
        requirePair(values, field, floor);
        return new IntRange(values.get(0), values.get(1));
    }

    private static void requirePair(List<? extends Number> values, String field, RangeFloor floor) {
        if (values == null || values.size() != 2 || values.get(0) == null || values.get(1) == null) {
            throw new IllegalArgumentException("Hydrology range " + field + " must be written as [min, max], got " + values);
        }
        double min = values.get(0).doubleValue();
        if (min > values.get(1).doubleValue()) {
            throw new IllegalArgumentException("Hydrology range " + field + " has its min above its max: " + values);
        }
        boolean belowFloor = switch (floor) {
            case NON_NEGATIVE -> min < 0D;
            case POSITIVE -> min <= 0D;
        };
        if (belowFloor) {
            throw new IllegalArgumentException("Hydrology range " + field + " must " + floor.requirement + ", got " + values);
        }
    }

    private void requireFluidReferences() {
        for (HydrologyTier tier : tiers) {
            requireFluid(tier.fluid(), "the fluid of tier '" + tier.name() + "'");
        }
        requireFluid(ponds.fluid(), "ponds.fluid");
        requireFluid(underground.cavePools().fluid(), "underground.cavePools.fluid");
    }

    private void requireFluid(String name, String field) {
        if (!fluids.containsKey(name)) {
            throw new IllegalArgumentException("Unknown hydrology fluid profile '" + name + "' in " + field + "; fluids defines " + fluids.keySet());
        }
    }

    private long computeFingerprint() {
        Fingerprint hash = new Fingerprint().add(seed).add(seaLevel).add(minY).add(maxY).add(enabled)
                .add(prefetchRings).add(persist);
        addDrainage(hash);
        addTiers(hash);
        addPonds(hash);
        addLakesAndDeformation(hash);
        addUnderground(hash);
        addFluids(hash);
        return hash.value();
    }

    private void addDrainage(Fingerprint hash) {
        hash.add(drainage.fillEpsilon()).add(drainage.breachDepth()).add(drainage.wetnessThinning())
                .add(drainage.minimumHeadKeep()).add(drainage.headRelief()).add(drainage.saturationCatchment());
    }

    private void addTiers(Fingerprint hash) {
        hash.add(tiers.size());
        for (HydrologyTier tier : tiers) {
            hash.add(tier.name()).add(tier.minCatchment()).add(tier.minHalfWidth()).add(tier.maxHalfWidth())
                    .add(tier.widthFactor()).add(tier.depthRatio()).add(tier.inset()).add(tier.cascades())
                    .add(tier.waterfallMinimumDrop()).add(tier.bankBand()).add(tier.bankStyle()).add(tier.deltas())
                    .add(tier.materials()).add(tier.fluid());
        }
    }

    private void addPonds(Fingerprint hash) {
        NaturalPonds natural = ponds.natural();
        SeededPonds seeded = ponds.seeded();
        PondShore shore = ponds.shore();
        hash.add(natural.enabled()).add(natural.keep()).add(natural.minArea()).add(natural.maxArea())
                .add(natural.minDepth())
                .add(seeded.enabled()).add(seeded.density()).add(seeded.radius()).add(seeded.depth())
                .add(seeded.maxSlope()).add(seeded.alongChannels()).add(seeded.channelSpacing())
                .add(ponds.dams().enabled()).add(ponds.dams().maxRaise())
                .add(ponds.freeboard()).add(ponds.climateDrawdown())
                .add(shore.roundness()).add(shore.shelf()).add(shore.taper()).add(shore.maxDepth())
                .add(ponds.materials()).add(ponds.fluid());
    }

    private void addLakesAndDeformation(Fingerprint hash) {
        hash.add(lakes.enabled()).add(lakes.minArea()).add(lakes.maxArea())
                .add(deformation.maxFill()).add(deformation.bankSlope()).add(deformation.bankSteepFactor())
                .add(deformation.bankNoise()).add(deformation.caveMask());
    }

    private void addUnderground(Fingerprint hash) {
        SeaInlets inlets = underground.seaInlets();
        CavePools pools = underground.cavePools();
        hash.add(inlets.enabled()).add(inlets.spacing()).add(inlets.minimumCliffHeight()).add(inlets.length())
                .add(inlets.halfWidth()).add(inlets.depth()).add(inlets.headroom()).add(inlets.roofClearance())
                .add(inlets.chamberRadius())
                .add(pools.enabled()).add(pools.density()).add(pools.minArea()).add(pools.maxArea())
                .add(pools.maxDepth()).add(pools.radius()).add(pools.freeboard()).add(pools.fluid());
    }

    private void addFluids(Fingerprint hash) {
        hash.add(fluids.size());
        for (Map.Entry<String, IrisMaterialPalette> fluid : fluids.entrySet()) {
            hash.add(fluid.getKey()).add(fluid.getValue());
        }
    }

    public record FloatRange(float min, float max) {
    }

    public record IntRange(int min, int max) {
    }

    public record Drainage(float fillEpsilon, int breachDepth, boolean wetnessThinning, float minimumHeadKeep, float headRelief,
                           float saturationCatchment) {
    }

    public record NaturalPonds(boolean enabled, float keep, int minArea, int maxArea, float minDepth) {
    }

    public record SeededPonds(boolean enabled, float density, IntRange radius, IntRange depth, float maxSlope,
                              boolean alongChannels, IntRange channelSpacing) {
    }

    public record PondDams(boolean enabled, int maxRaise) {
    }

    public record PondShore(float roundness, int shelf, int taper, int maxDepth) {
    }

    public record Ponds(NaturalPonds natural, SeededPonds seeded, PondDams dams, IntRange freeboard,
                        int climateDrawdown, PondShore shore, IrisHydrologyMaterials materials, String fluid,
                        List<IrisDecorator> decorators) {
    }

    public record Lakes(boolean enabled, int minArea, int maxArea) {
    }

    public record Deformation(int maxFill, float bankSlope, float bankSteepFactor, float bankNoise, boolean caveMask) {
    }

    public record SeaInlets(boolean enabled, int spacing, int minimumCliffHeight, IntRange length, FloatRange halfWidth,
                            IntRange depth, IntRange headroom, int roofClearance, IntRange chamberRadius) {
    }

    public record CavePools(boolean enabled, float density, int minArea, int maxArea, int maxDepth, int radius,
                            IntRange freeboard, String fluid) {
    }

    public record Underground(SeaInlets seaInlets, CavePools cavePools) {
    }

    private enum RangeFloor {
        NON_NEGATIVE("not go below 0"),
        POSITIVE("stay above 0");

        private final String requirement;

        RangeFloor(String requirement) {
            this.requirement = requirement;
        }
    }

    private static final class Fingerprint {
        private static final long SEED = 0x48594452534554L;
        private static final long GOLDEN = 0x9e3779b97f4a7c15L;
        private static final long ABSENT = 0x5bd1e9955bd1e995L;
        private static final long PRESENT = 0x27d4eb2f165667c5L;
        private static final long FNV_OFFSET = 0xcbf29ce484222325L;
        private static final long FNV_PRIME = 0x100000001b3L;

        private long hash = SEED;

        private Fingerprint add(long value) {
            hash = avalanche(hash ^ avalanche(value + GOLDEN));
            return this;
        }

        private Fingerprint add(float value) {
            return add(Float.floatToIntBits(value));
        }

        private Fingerprint add(boolean value) {
            return add(value ? 1L : 0L);
        }

        private Fingerprint add(String value) {
            if (value == null) {
                return add(ABSENT);
            }
            long text = FNV_OFFSET;
            for (int index = 0; index < value.length(); index++) {
                text = (text ^ value.charAt(index)) * FNV_PRIME;
            }
            return add(value.length()).add(text);
        }

        private Fingerprint add(Enum<?> value) {
            return add(value == null ? null : value.name());
        }

        private Fingerprint add(IntRange range) {
            return add(range.min()).add(range.max());
        }

        private Fingerprint add(FloatRange range) {
            return add(range.min()).add(range.max());
        }

        private Fingerprint add(IrisMaterialPalette palette) {
            return add(palette == null ? null : PALETTE_JSON.toJson(palette));
        }

        private Fingerprint add(IrisHydrologyMaterials materials) {
            if (materials == null) {
                return add(ABSENT);
            }
            return add(PRESENT).add(materials.getBed()).add(materials.getShore()).add(materials.getShoreDepth())
                    .add(materials.getBank()).add(materials.isAllowGravityBlocks());
        }

        private long value() {
            return hash;
        }

        private static long avalanche(long value) {
            long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
            mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
            return mixed ^ (mixed >>> 31);
        }
    }
}
