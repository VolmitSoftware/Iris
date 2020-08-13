package com.volmit.iris.command;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.IrisChunkGenerator;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisHotload extends MortarCommand {
	public CommandIrisHotload() {
		super("hotload", "hot", "h", "reload");
		setDescription("Force a hotload");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args) {
		if (sender.isPlayer()) {
			Player p = sender.player();
			World world = p.getWorld();

			if (!(world.getGenerator() instanceof IrisChunkGenerator)) {
				sender.sendMessage("You must be in an iris world.");
				return true;
			}

			IrisChunkGenerator g = (IrisChunkGenerator) world.getGenerator();
			g.onHotload();
			sender.sendMessage("Hotloaded!");
			return true;
		}

		else {
			sender.sendMessage("Players only.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage() {
		return "";
	}
}
