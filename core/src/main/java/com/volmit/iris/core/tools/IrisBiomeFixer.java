package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.misc.E;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.ChronoLatch;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IrisBiomeFixer {
    /**
     * Do a pregen style approach irritate across a certain region and set everything to the correct biome again.
     * Have 2 modes ( all, surface-only ) surface-only gets the underground caves from a different world
     */

    private World world;
    private int radius;
    private ChronoLatch latch;


    public IrisBiomeFixer(World world, int radius) {
        this.world = world;
        this.radius = radius;
        this.latch = new ChronoLatch(3000);


    }



}
