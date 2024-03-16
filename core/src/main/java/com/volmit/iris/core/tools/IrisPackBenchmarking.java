package com.volmit.iris.core.tools;


import com.volmit.iris.Iris;
import com.volmit.iris.core.pregenerator.IrisPregenerator;
import com.volmit.iris.core.pregenerator.LazyPregenerator;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.util.exceptions.IrisException;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.Position2;

import com.volmit.iris.util.scheduling.J;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.volmit.iris.core.commands.CommandIris.BenchDimension;


public class IrisPackBenchmarking {
    public static IrisPackBenchmarking instance;
     long totalChunks;
     long generatedChunks;
     double elapsedTimeNs;

    public void runBenchmark() {

    }
     public void createBenchmark(){
        try {
            IrisToolbelt.createWorld()
                    .dimension(BenchDimension)
                    .name("Benchmark")
                    .seed(1337)
                    .studio(false)
                    .benchmark(true)
                    .create();
        } catch (IrisException e) {
            throw new RuntimeException(e);
        }
    }
     public void startBenchmark(){
        int x = 0;
        int z = 0;
            IrisToolbelt.pregenerate(PregenTask
                    .builder()
                    .center(new Position2(x, z))
                    .width(5)
                    .height(5)
                    .build(), Bukkit.getWorld("Benchmark")
            );
    }
}