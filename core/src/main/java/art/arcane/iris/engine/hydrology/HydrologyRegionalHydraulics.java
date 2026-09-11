package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.SurfaceBankSupport;

import java.util.Arrays;

final class HydrologyRegionalHydraulics {
    private static final int MAXIMUM_WIDTH_CANDIDATES = 8;
    private static final int MAXIMUM_ADDITIONAL_SAMPLES = 2048;
    private static final int MAXIMUM_BANK_RADIUS = 96;

    private final HydrologyPlannerSettings settings;
    private final SurfaceBankSupport bankSupport;
    private final double minimumHalfWidth;
    private final double maximumProbedHalfWidth;
    private final double[] candidateHalfWidths;

    HydrologyRegionalHydraulics(HydrologyPlannerSettings settings) {
        this.settings = settings;
        this.bankSupport = new SurfaceBankSupport(settings.surface(), settings.seaLevel());
        HydrologyPlannerSettings.Surface surface = settings.surface();
        this.minimumHalfWidth = surface.minimumWidth() / 2D;
        double authored = surface.maximumWidth() / 2D;
        double expanded = surface.maximumWidth();
        this.maximumProbedHalfWidth = Math.min(expanded, Math.max(minimumHalfWidth,
                (MAXIMUM_BANK_RADIUS - 2D) / (1D + surface.banks().roughness())));
        this.candidateHalfWidths = candidates(authored, midpoint(minimumHalfWidth, authored),
                maximumProbedHalfWidth, midpoint(minimumHalfWidth, maximumProbedHalfWidth));
    }

    int minimumHead(HydrologyTerrainSample terrain) {
        return minimumHead(terrain, terrain.surfacePolicy().maximumIncision(settings.surface().maximumIncision()));
    }

    int inletMinimumHead(HydrologyTerrainSample terrain) {
        return minimumHead(terrain, Math.max(terrain.surfacePolicy().maximumIncision(settings.surface().maximumIncision()),
                settings.surface().banks().inlet().maximumIncision()));
    }

    int maximumHead(HydrologyTerrainSample terrain) {
        return Math.max(settings.seaLevel(), terrain.naturalHeight() - settings.surface().banks().sink());
    }

    int supportedHead(HeadStation station, HydrologyTerrainSampler sampler) {
        int centerHead = maximumHead(station.terrain());
        if (centerHead >= station.incomingHead()) {
            return station.incomingHead();
        }
        int supported = support(station, sampler, minimumHalfWidth, centerHead);
        if (supported >= station.incomingHead()) {
            return station.incomingHead();
        }
        SampleBudget budget = new SampleBudget(sampler);
        double localMinimum = effectiveHalfWidth(settings.surface().minimumWidth(), station.terrain().widthMultiplier());
        double localMaximum = effectiveHalfWidth(settings.surface().maximumWidth(), station.terrain().widthMultiplier());
        if (additional(localMaximum)) {
            supported = support(station, budget, localMaximum, supported);
        }
        if (supported < station.incomingHead() && localMinimum != localMaximum && additional(localMinimum)) {
            supported = support(station, budget, localMinimum, supported);
        }
        double localMiddle = midpoint(localMinimum, localMaximum);
        if (supported < station.incomingHead() && localMiddle != localMinimum && localMiddle != localMaximum && additional(localMiddle)) {
            supported = support(station, budget, localMiddle, supported);
        }
        for (double halfWidth : candidateHalfWidths) {
            if (supported >= station.incomingHead() || budget.remaining == 0) {
                break;
            }
            supported = support(station, budget, halfWidth, supported);
        }
        return Math.min(station.incomingHead(), supported);
    }

    private int support(HeadStation station, HydrologyTerrainSampler sampler, double halfWidth, int supportedHead) {
        SurfaceBankSupport.Station crossSection = new SurfaceBankSupport.Station(station.x(), station.z(),
                station.tangentX(), station.tangentZ(), halfWidth);
        SurfaceBankSupport.CrossSection support = bankSupport.crossSection(sampler, crossSection, true);
        if (support.blocked() || support.minimum() - settings.surface().banks().sink() <= supportedHead) {
            return supportedHead;
        }
        SurfaceBankSupport.Perimeter perimeter = bankSupport.perimeter(sampler, crossSection, support.minimum());
        if (!perimeter.complete()) {
            return supportedHead;
        }
        int supported = perimeter.minimum() - settings.surface().banks().sink();
        return Math.max(supportedHead, supported);
    }

    private double effectiveHalfWidth(int width, double multiplier) {
        return Math.max(settings.surface().minimumWidth(), Math.min(settings.surface().maximumWidth() * 2D, width * multiplier)) / 2D;
    }

    private boolean additional(double halfWidth) {
        if (halfWidth <= minimumHalfWidth || halfWidth > maximumProbedHalfWidth) {
            return false;
        }
        for (double candidate : candidateHalfWidths) {
            if (candidate == halfWidth) {
                return false;
            }
        }
        return true;
    }

    private double[] candidates(double... widths) {
        double[] distinct = new double[MAXIMUM_WIDTH_CANDIDATES - 4];
        int size = 0;
        for (double width : widths) {
            if (!Double.isFinite(width) || width <= minimumHalfWidth || width > maximumProbedHalfWidth) {
                continue;
            }
            boolean duplicate = false;
            for (int index = 0; index < size; index++) {
                duplicate |= distinct[index] == width;
            }
            if (!duplicate && size < distinct.length) {
                distinct[size++] = width;
            }
        }
        return Arrays.copyOf(distinct, size);
    }

    private static double midpoint(double first, double second) {
        return first + (second - first) / 2D;
    }

    private int minimumHead(HydrologyTerrainSample terrain, int incision) {
        int permitted = Math.min(incision, (int) StrictMath.floor(incision * terrain.incisionMultiplier()));
        int depth = (int) StrictMath.round(Math.max(1D, Math.min(settings.surface().maximumDepth() * 2D,
                settings.surface().minimumDepth() * terrain.depthMultiplier())));
        return Math.max(settings.seaLevel(), terrain.naturalHeight() - permitted + depth);
    }

    record HeadStation(int x, int z, double tangentX, double tangentZ, HydrologyTerrainSample terrain, int incomingHead) {
    }

    private static final class SampleBudget implements HydrologyTerrainSampler {
        private final HydrologyTerrainSampler sampler;
        private int remaining = MAXIMUM_ADDITIONAL_SAMPLES;

        private SampleBudget(HydrologyTerrainSampler sampler) {
            this.sampler = sampler;
        }

        @Override
        public HydrologyTerrainSample sample(int blockX, int blockZ) {
            if (remaining == 0) {
                return null;
            }
            remaining--;
            return sampler.sample(blockX, blockZ);
        }
    }
}
