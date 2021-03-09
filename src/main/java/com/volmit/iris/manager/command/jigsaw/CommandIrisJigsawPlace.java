package com.volmit.iris.manager.command.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisJigsawStructure;
import com.volmit.iris.object.IrisPosition;
import com.volmit.iris.scaffold.jigsaw.PlannedStructure;
import com.volmit.iris.util.*;

public class CommandIrisJigsawPlace extends MortarCommand
{
	public CommandIrisJigsawPlace()
	{
		super("place", "paste");
		requiresPermission(Iris.perm);
		setCategory("Jigsaw");
		setDescription("Place a jigsaw structure");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio Jigsaw, please enable studio in Iris/settings.json");
			return true;
		}
		
		if(!sender.isPlayer())
		{
			sender.sendMessage("Ingame only");
			return true;
		}

		if(args.length == 0){
			sender.sendMessage("You have to specify a jigsaw structure!");
			return true;
		}

		IrisJigsawStructure str = IrisDataManager.loadAnyJigsawStructure(args[0]);

		if(str != null)
		{
			PrecisionStopwatch p = PrecisionStopwatch.start();
			PlannedStructure ps = new PlannedStructure(str, new IrisPosition(sender.player().getLocation()), new RNG());
			sender.sendMessage("Generated " + ps.getPieces().size() + " pieces in " + Form.duration(p.getMilliseconds(), 2));
			ps.place(sender.player().getWorld());
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<name>";
	}
}
