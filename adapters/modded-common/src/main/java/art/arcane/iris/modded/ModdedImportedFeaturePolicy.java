package art.arcane.iris.modded;

import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.nativegen.ImportedFeaturePolicy;

final class ModdedImportedFeaturePolicy extends ImportedFeaturePolicy {
    ModdedImportedFeaturePolicy(Engine engine) {
        super(engine);
    }

    @Override
    protected boolean includeVanillaDerivative() {
        return false;
    }

    @Override
    protected String customBiomeKey(IrisBiomeCustom biome) {
        return ModdedWorldgenIds.biomeRef(engine, biome.getId());
    }
}
