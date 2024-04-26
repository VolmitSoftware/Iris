package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.nbt.mca.Chunk;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.World;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class IrisWorldDump {
    public static int Failed = 0;
    public static int Success = 0;
    private KList<MCAFile> mcaList;
    private World world;
    private File MCADirectory;
    private AtomicInteger processed;
    private AtomicInteger totalToProcess;
    private Engine engine = null;
    private Boolean IrisWorld;
    private VolmitSender sender;

    public IrisWorldDump(World world, VolmitSender sender) {
        this.world = world;
        this.sender = sender;
        this.MCADirectory = new File(world.getWorldFolder(), "region");
        if (Runtime.getRuntime().maxMemory() < 1.5 * estimateMemoryUsage()) {
            sender.sendMessage(C.YELLOW + "Not enough memory!");
            sender.sendMessage(C.YELLOW + "- Process amount: " + Form.memSize(Runtime.getRuntime().maxMemory()));
            sender.sendMessage(C.YELLOW + "- Required amount: " + Form.memSize(estimateMemoryUsage()));
            //return;
        }
        sender.sendMessage("Initializing IrisWorldDump...");

        this.mcaList = new KList<>(getMcaFiles());
        this.processed = new AtomicInteger(0);
        this.totalToProcess = new AtomicInteger(0);

        try {
            this.engine = IrisToolbelt.access(world).getEngine();
            this.IrisWorld = true;
        } catch (Exception e) {
            this.IrisWorld = false;
        }
    }

    public void dump() {
        for (MCAFile mca : mcaList) {
            AtomicReferenceArray<Chunk> chunks = new AtomicReferenceArray<>(1024);
            for (int i = 0; i < chunks.length(); i++) {
                chunks.set(i, mca.getChunks().get(i));
            }
            for (int i = 0; i < chunks.length(); i++) {
                Chunk chunk = chunks.get(i);
                if (chunk != null) {
                    int CHUNK_HEIGHT = (world.getMaxHeight() - world.getMinHeight());
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < CHUNK_HEIGHT; y++) {
                              //  CompoundTag tag = chunk.getBlockStateAt(x,y,z);
                            }
                        }
                    }
                }
            }
        }
    }


    private long estimateMemoryUsage() {
        long size = 0;
        for (File mca : MCADirectory.listFiles()) {
            size =+ mca.length();
        }
        return size;
    }

    private List<MCAFile> getMcaFiles() {
        List<MCAFile> mcaFiles = new ArrayList<>();
        int l = 0;
        int f = 0;
        for (File mca : MCADirectory.listFiles()) {
          //  net.minecraft.world.level.chunk.PalettedContainer
                //    take a look at the classes `net.minecraft.world.level.chunk.PalettedContainer` and `net.minecraft.world.level.chunk.storage.ChunkSerializer`
            if (mca.getName().endsWith(".mca")) {
                try {
                    mcaFiles.add(MCAUtil.read(mca));
                    l++;
                } catch (Exception e) {
                    f++;
                    Iris.error("Failed to read mca file: " + mca.getName(), e);
                    e.printStackTrace(); // todo: debug line
                }
            }
        }
        sender.sendMessage("Loaded: " + l + " MCA Regions");
        if (f > 0) {
            sender.sendMessage(C.RED +"Failed " + C.GRAY + "to load: " + f + " MCA Regions");
        }
        Iris.info("Successfull: " + Form.f(Success));
        Iris.info("Failed: " + Form.f(Failed));
        return mcaFiles;
    }

}
