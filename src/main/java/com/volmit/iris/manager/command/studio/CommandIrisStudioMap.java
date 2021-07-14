package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.map.MapVision;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.FakeEngine;
import com.volmit.iris.util.FakeWorld;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.World;

import java.io.File;

public class CommandIrisStudioMap extends MortarCommand
{
    public CommandIrisStudioMap()
    {
        super("map", "render");
        setDescription("Render a map (gui outside of mc)");
        requiresPermission(Iris.perm.studio);
        setCategory("World");
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(MortarSender sender, String[] args)
    {
        if(!IrisSettings.get().isStudio())
        {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        if(!IrisSettings.get().isUseServerLaunchedGuis())
        {
            sender.sendMessage("To use Iris Guis, please enable serverLaunchedGuis in Iris/settings.json");
            return true;
        }

        IrisComplex complex;

        if (args.length > 0) {
            String type = "";
            long seed = 1337;
            for(String i : args)
            {
                if (i.contains("=")) {
                    type = i.startsWith("type=") ? i.split("\\Q=\\E")[1] : type;
                    seed = i.startsWith("seed=") ? Long.valueOf(i.split("\\Q=\\E")[1]) : seed;
                } else {
                    if (type.equals("")) {
                        type = i;
                    } else if (seed == 1337) {
                        seed = Long.valueOf(i);
                    }
                }
            }

            if (type.equals("")) {
                sender.sendMessage("Open this in a studio world or do /iris studio map [pack]");
                return true;
            }

            IrisDimension dim = IrisDataManager.loadAnyDimension(type);

            if (dim == null) {
                sender.sendMessage("Can't find dimension: " + type);
                return true;
            }

            if (dim.getEnvironment() == null) {
                dim.setEnvironment(World.Environment.NORMAL);
            }

            //Setup the fake world and engine objects so we can get an IrisComplex for the terrain they will
            //generate without actually generating any of it
            sender.sendMessage("Preparing map...");
            FakeWorld world = new FakeWorld(dim.getName(), 0, 256, seed, new File(dim.getName()), dim.getEnvironment());
            FakeEngine engine = new FakeEngine(dim, world);
            complex = new IrisComplex(engine, true);
            sender.sendMessage("Opening Map!");
        } else if (Iris.proj.isProjectOpen()) {
            IrisAccess g = Iris.proj.getActiveProject().getActiveProvider();
            complex = g.getCompound().getDefaultEngine().getFramework().getComplex();
            sender.sendMessage("Opening map for existing studio world!");
        } else {
            sender.sendMessage("Open this in a studio world or do /iris studio map [pack]");
            return true;
        }

        MapVision map = new MapVision(complex);
        map.open();
        return true;
    }

    @Override
    protected String getArgsUsage()
    {
        return "[pack] [seed=1337]";
    }
}
