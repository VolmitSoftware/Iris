package com.volmit.iris.manager.command.what;

import com.volmit.iris.util.*;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;

public class CommandIrisWhatBlock extends MortarCommand
{
	public CommandIrisWhatBlock()
	{
		super("block", "l", "bl");
		setDescription("Get the block data for looking.");
		requiresPermission(Iris.perm.studio);
		setCategory("Wut");
		setDescription("WAILA, WAWLA etc");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			BlockData bd;
			Player p = sender.player();
			try
			{
				bd = p.getTargetBlockExact(128, FluidCollisionMode.NEVER).getBlockData();
			}
			catch (NullPointerException e)
			{
				sender.sendMessage("Please look at any block, not at the sky");
				bd = null;
			}

			if(bd != null) {
				sender.sendMessage("Material: " + C.GREEN + bd.getMaterial().name());
				sender.sendMessage("Full: " + C.WHITE + bd.getAsString(true));

				if (B.isStorage(bd)) {
					sender.sendMessage(C.YELLOW + "* Storage Block (Loot Capable)");
				}

				if (B.isLit(bd)) {
					sender.sendMessage(C.YELLOW + "* Lit Block (Light Capable)");
				}

				if (B.isFoliage(bd)) {
					sender.sendMessage(C.YELLOW + "* Foliage Block");
				}

				if (B.isDecorant(bd)) {
					sender.sendMessage(C.YELLOW + "* Decorant Block");
				}

				if (B.isFluid(bd)) {
					sender.sendMessage(C.YELLOW + "* Fluid Block");
				}

				if (B.isFoliagePlantable(bd)) {
					sender.sendMessage(C.YELLOW + "* Plantable Foliage Block");
				}

				if (B.isSolid(bd)) {
					sender.sendMessage(C.YELLOW + "* Solid Block");
				}
			}
		}

		else
		{
			sender.sendMessage("Players only.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
