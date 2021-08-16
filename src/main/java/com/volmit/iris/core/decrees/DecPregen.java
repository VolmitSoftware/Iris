package com.volmit.iris.core.decrees;

import com.volmit.iris.Iris;
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.Position2;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

@Decree(name = "pregen", aliases = "pregenerate", description = "Pregenerate your Iris worlds!")
public class DecPregen implements DecreeExecutor {
    @Decree(description = "Pregenerate a world")
    public void start(
        @Param(description = "The world to pregen", contextual = true)
                World world,
        @Param(description = "The radius of the pregen in blocks", aliases = "size")
                int radius,
        @Param(aliases = "middle", description = "The center location of the pregen. Use \"me\" for your current location", defaultValue = "0,0")
                Vector center
    ) {
        try {
            IrisToolbelt.pregenerate(PregenTask
                    .builder()
                    .center(new Position2(center))
                    .width((radius >> 9 + 1) * 2)
                    .height((radius >> 9 + 1) * 2)
                    .build(), world);
            sender().sendMessage(C.GREEN + "Successfully started the pregeneration task!");
        } catch (Throwable e) {
            sender().sendMessage(C.RED + "Epic fail");
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    @Decree(description = "Stop the active pregeneration task", aliases = "x")
    public void stop(){
        if (PregeneratorJob.shutdownInstance()) {
            sender().sendMessage(C.GREEN + "Stopped pregeneration task");
        } else {
            sender().sendMessage(C.YELLOW + "No active pregeneration tasks to stop");
        }
    }

    @Decree(description = "Pause / continue the active pregeneration task", aliases = {"t", "resume", "unpause"})
    public void pause() {
        if (PregeneratorJob.pauseResume()) {
            sender().sendMessage(C.GREEN + "Paused/unpaused pregeneration task, now: " + (PregeneratorJob.isPaused() ? "Paused" : "Running") + ".");
        } else {
            sender().sendMessage(C.YELLOW + "No active pregeneration tasks to pause/unpause.");
        }
    }
}
