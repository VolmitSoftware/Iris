package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.NoiseView;
import com.volmit.iris.gen.IrisChunkGenerator;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisMap extends MortarCommand {
	public CommandIrisMap() {
		super("map", "render");
		setDescription("Render a map (gui outside of mc)");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args) {
		IrisChunkGenerator g = Iris.proj.getCurrentProject();
		NoiseView.launch(g);
		sender.sendMessage("Opening Map!");
		return true;
	}

	@Override
	protected String getArgsUsage() {
		return "";
	}
}
