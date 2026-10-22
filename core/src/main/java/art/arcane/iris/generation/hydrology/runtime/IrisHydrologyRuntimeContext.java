package art.arcane.iris.generation.hydrology.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.hydrology.HydrologyCaveVoxelViewFactory;
import art.arcane.iris.generation.terrain.IrisDimension;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public record IrisHydrologyRuntimeContext(
        long seed,
        int worldHeight,
        IrisDimension dimension,
        IrisData data,
        IrisHydrologyNaturalSampleProvider naturalSampleProvider,
        IrisHydrologyNaturalHeightProvider naturalHeightProvider,
        IrisHydrologyNaturalHeightDescriber naturalHeightDescriber,
        IrisHydrologyNaturalOceanClassifier naturalOceanClassifier,
        HydrologyCaveVoxelViewFactory caveViewFactory,
        BooleanSupplier waitingForbidden
) {
    /**
     * {@code waitingForbidden} is true on a thread that must never wait for a cold hydrology plan,
     * the server thread above all: a sample taken there is answered as "no hydrology here" while the
     * tiles it needs are planned on the prefetch pool.
     */
    public IrisHydrologyRuntimeContext {
        if (worldHeight < 3) {
            throw new IllegalArgumentException("worldHeight must be at least three");
        }
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(data);
        Objects.requireNonNull(naturalSampleProvider);
        Objects.requireNonNull(naturalHeightProvider);
        Objects.requireNonNull(naturalHeightDescriber);
        Objects.requireNonNull(naturalOceanClassifier);
        Objects.requireNonNull(caveViewFactory);
        Objects.requireNonNull(waitingForbidden);
    }
}
