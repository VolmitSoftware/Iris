package com.volmit.iris.core.decrees;

import com.volmit.iris.Iris;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.format.C;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

@Decree(name = "what", aliases = "?", description = "Get information about the world around you", origin = DecreeOrigin.PLAYER)
public class DecWhat implements DecreeExecutor {

    @Decree(description = "Get information about the block you're looking at")
    public void block(){

        Block b = player().getTargetBlockExact(128, FluidCollisionMode.NEVER);

        if (b == null) {
            sender().sendMessage("Please look at any block, not at the sky");
            return;
        }

        BlockData bd = b.getBlockData();

        sender().sendMessage("Material: " + C.GREEN + bd.getMaterial().name());
        sender().sendMessage("Full: " + C.WHITE + bd.getAsString(true));

        if (B.isStorage(bd)) {
            sender().sendMessage(C.YELLOW + "* Storage Block (Loot Capable)");
        }

        if (B.isLit(bd)) {
            sender().sendMessage(C.YELLOW + "* Lit Block (Light Capable)");
        }

        if (B.isFoliage(bd)) {
            sender().sendMessage(C.YELLOW + "* Foliage Block");
        }

        if (B.isDecorant(bd)) {
            sender().sendMessage(C.YELLOW + "* Decorant Block");
        }

        if (B.isFluid(bd)) {
            sender().sendMessage(C.YELLOW + "* Fluid Block");
        }

        if (B.isFoliagePlantable(bd)) {
            sender().sendMessage(C.YELLOW + "* Plantable Foliage Block");
        }

        if (B.isSolid(bd)) {
            sender().sendMessage(C.YELLOW + "* Solid Block");
        }
    }
}
