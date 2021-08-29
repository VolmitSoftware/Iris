package com.volmit.iris.core.commands;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.json.JSONObject;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

@Decree(name = "what", origin = DecreeOrigin.PLAYER, studio = true, description = "Iris What?")
public class CommandWhat implements DecreeExecutor {
    @Decree(description = "What is in my hand?", origin = DecreeOrigin.PLAYER)
    public void hand() {
        try {
            BlockData bd = player().getInventory().getItemInMainHand().getType().createBlockData();
            if (!bd.getMaterial().equals(Material.AIR)) {
                sender().sendMessage("Material: " + C.GREEN + bd.getMaterial().name());
                sender().sendMessage("Full: " + C.WHITE + bd.getAsString(true));
            } else {
                sender().sendMessage("Please hold a block/item");
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            Material bd = player().getInventory().getItemInMainHand().getType();
            if (!bd.equals(Material.AIR)) {
                sender().sendMessage("Material: " + C.GREEN + bd.name());
            } else {
                sender().sendMessage("Please hold a block/item");
            }
        }
    }

    @Decree(description = "What biome am i in?", origin = DecreeOrigin.PLAYER)
    public void biome() {
        try {
            IrisBiome b = engine().getBiome(player().getLocation().getBlockX(), player().getLocation().getBlockY(), player().getLocation().getBlockZ());
            sender().sendMessage("IBiome: " + b.getLoadKey() + " (" + b.getDerivative().name() + ")");

        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage("Non-Iris Biome: " + player().getLocation().getBlock().getBiome().name());

            if (player().getLocation().getBlock().getBiome().equals(Biome.CUSTOM)) {
                try {
                    sender().sendMessage("Data Pack Biome: " + INMS.get().getTrueBiomeBaseKey(player().getLocation()) + " (ID: " + INMS.get().getTrueBiomeBaseId(INMS.get().getTrueBiomeBase(player().getLocation())) + ")");
                } catch (Throwable ee) {
                    Iris.reportError(ee);
                }
            }
        }
    }

    @Decree(description = "What block am i looking at?", origin = DecreeOrigin.PLAYER)
    public void block() {
        BlockData bd;
        try {
            bd = player().getTargetBlockExact(128, FluidCollisionMode.NEVER).getBlockData();
        } catch (NullPointerException e) {
            Iris.reportError(e);
            sender().sendMessage("Please look at any block, not at the sky");
            bd = null;
        }

        if (bd != null) {
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

    @Decree(description = "What features am i near?", origin = DecreeOrigin.PLAYER)
    public void features() {
        Chunk c = player().getLocation().getChunk();

        if (IrisToolbelt.isIrisWorld(c.getWorld())) {
            int m = 1;
            for (IrisFeaturePositional i : IrisToolbelt.access(c.getWorld()).getEngine().getMantle().forEachFeature(c)) {
                sender().sendMessage("#" + m++ + " " + new JSONObject(new Gson().toJson(i)).toString(4));
            }
        } else {
            sender().sendMessage("Iris worlds only.");
        }
    }
}
