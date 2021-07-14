package com.volmit.iris.scaffold.engine;

import com.volmit.iris.generator.actuator.IrisTerrainActuator;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisPosition;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.generator.BlockPopulator;

import java.util.List;

public interface EngineCompound extends Listener, Hotloadable, DataProvider {
    IrisDimension getRootDimension();

    void generate(int x, int z, Hunk<BlockData> blocks, Hunk<BlockData> postblocks, Hunk<Biome> biomes);

    World getWorld();

    List<IrisPosition> getStrongholdPositions();

    void printMetrics(CommandSender sender);

    int getSize();

    default int getHeight() {
        // TODO: WARNING HEIGHT
        return 256;
    }

    Engine getEngine(int index);

    MultiBurst getBurster();

    EngineData getEngineMetadata();

    void saveEngineMetadata();

    KList<BlockPopulator> getPopulators();

    default Engine getEngineForHeight(int height) {
        if (getSize() == 1) {
            return getEngine(0);
        }

        int buf = 0;

        for (int i = 0; i < getSize(); i++) {
            Engine e = getEngine(i);
            buf += e.getHeight();

            if (buf >= height) {
                return e;
            }
        }

        return getEngine(getSize() - 1);
    }

    default void recycle() {
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).recycle();
        }
    }

    default void save() {
        saveEngineMetadata();
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).save();
        }
    }

    default void saveNOW() {
        saveEngineMetadata();
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).saveNow();
        }
    }

    IrisDataManager getData(int height);

    default IrisDataManager getData() {
        return getData(0);
    }

    default void close() {
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).close();
        }
    }

    boolean isFailing();

    int getThreadCount();

    boolean isStudio();

    void setStudio(boolean std);

    default void clean() {
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).clean();
        }
    }

    Engine getDefaultEngine();

    default KList<IrisBiome> getAllBiomes() {
        KMap<String, IrisBiome> v = new KMap<>();

        IrisDimension dim = getRootDimension();
        dim.getAllBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i));

        try {
            dim.getDimensionalComposite().forEach((m) -> getData().getDimensionLoader().load(m.getDimension()).getAllBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i)));
        } catch (Throwable e) {

        }

        return v.v();
    }

    void updateWorld(World world);

    default int getLowestBedrock() {
        int f = Integer.MAX_VALUE;

        for (int i = 0; i < getSize(); i++) {
            Engine e = getEngine(i);

            if (e.getDimension().isBedrock()) {
                int m = ((IrisTerrainActuator) e.getFramework().getTerrainActuator()).getLastBedrock();

                if (f > m) {
                    f = m;
                }
            }
        }

        return f;
    }
}
