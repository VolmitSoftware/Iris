package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.GenerationFindCatalog;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.volmlib.util.collection.KList;

public class ReachableBiomeHandler extends RegistrantHandler<IrisBiome> {
    public ReachableBiomeHandler() {
        super(IrisBiome.class, true);
    }

    @Override
    public KList<IrisBiome> getPossibilities() {
        Engine activeEngine = engine();
        return activeEngine == null
                ? super.getPossibilities()
                : GenerationFindCatalog.biomes(activeEngine);
    }

    @Override
    public String getRandomDefault() {
        return "biome";
    }
}
