package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.Chunk;
import org.bukkit.block.data.BlockData;

public class CommandIrisStudioFix extends MortarCommand
{
	public CommandIrisStudioFix()
	{
		super("fix");
		requiresPermission(Iris.perm.studio);
		setDescription("Go to the spawn of the currently open studio world.");
		setCategory("Studio");
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

		if(!Iris.proj.isProjectOpen())
		{
			sender.sendMessage("There is not a studio currently loaded.");
			return true;
		}

		try
		{
			Chunk c = sender.player().getLocation().getChunk();
			int cx = c.getX() * 16;
			int cz = c.getZ() * 16;
			Hunk<BlockData> bd = Hunk.viewBlocks(c);
			Iris.proj.getActiveProject().getActiveProvider().getCompound().getDefaultEngine().getFramework().getEngineParallax().insertParallax(cx, cz, bd);
			sender.sendMessage("Done!");
		}

		catch(Throwable e)
		{
			sender.sendMessage("Failed to teleport to the studio world. Try re-opening the project.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
