package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.GenerationFindCatalog;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;

public class ReachableRegionHandler extends RegistrantHandler<IrisRegion> {
    public ReachableRegionHandler() {
        super(IrisRegion.class, true);
    }

    @Override
    public KList<IrisRegion> getPossibilities() {
        Engine activeEngine = engine();
        return activeEngine == null ? super.getPossibilities() : GenerationFindCatalog.regions(activeEngine);
    }

    @Override
    public String getRandomDefault() {
        return "region";
    }
}
