package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.manager.gui.NoiseExplorer;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.RNG;

public class CommandIrisStudioExplorerGenerator extends MortarCommand
{
	public CommandIrisStudioExplorerGenerator()
	{
		super("generator", "gen", "g");
		setDescription("Preview created noise noises generators");
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

		if (args.length == 0)
		{
			sender.sendMessage("Specify a generator to preview");
			return true;
		}

		IrisGenerator generator;
		long seed = 12345;

		if (Iris.proj.isProjectOpen())
		{
			generator = Iris.proj.getActiveProject().getActiveProvider().getData().getGeneratorLoader().load(args[0]);
			seed = Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().getSeed();
		}
		else
		{
			generator = IrisDataManager.loadAnyGenerator(args[0]);
		}

		if (generator != null)
		{
			long finalSeed = seed;
			NoiseExplorer.launch((x, z) ->
					generator.getHeight(x, z, new RNG(finalSeed).nextParallelRNG(3245).lmax()), "Gen: " + generator.getLoadKey());

			sender.sendMessage("Opening Noise Explorer for gen " + generator.getLoadKey() + " (" + generator.getLoader().getDataFolder().getName() + ")");
			return true;
		}
		else
		{
			sender.sendMessage("Invalid Generator");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[generator]";
	}
}
