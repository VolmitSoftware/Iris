package com.volmit.iris.manager.command.world;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;

import java.util.concurrent.atomic.AtomicInteger;

public class CommandIrisFix extends MortarCommand
{
	public CommandIrisFix()
	{
		super("fix");
		requiresPermission(Iris.perm.studio);
		setDescription("Fix nearby chunks");
		setCategory("Studio");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		try
		{
			IrisAccess a = IrisWorlds.access(sender.player().getWorld());
			if(a.getCompound().getSize() > 1)
			{
				sender.sendMessage("Cant fix engine composite worlds!");
				return true;
			}

			int viewDistance = args.length > 0 ? Integer.valueOf(args[0]) : -1;
			if(viewDistance <=1)
			{
				J.a(() -> {
					int fixed = a.getCompound().getDefaultEngine().getFramework().getEngineParallax().repairChunk(sender.player().getLocation().getChunk());
					sender.sendMessage("Fixed " + Form.f(fixed) + " blocks!");
				});
			}

			else
			{
				AtomicInteger v = new AtomicInteger();
				J.a(() -> {
					new Spiraler(viewDistance, viewDistance, (x,z) -> v.set(v.get() + a.getCompound().getDefaultEngine().getFramework().getEngineParallax().repairChunk(sender.player().getWorld().getChunkAt(x, z)))).drain();
					sender.sendMessage("Fixed " + Form.f(v.get()) + " blocks in " + (viewDistance * viewDistance) + " chunks!");
				});
			}
		}

		catch(Throwable e)
		{
			sender.sendMessage("Not a valid Iris World (or bad argument)");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[view-distance]";
	}
}
