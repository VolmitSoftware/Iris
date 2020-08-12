package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioVerify extends MortarCommand {
	public CommandIrisStudioVerify() {
		super("verify", "check", "v");
		requiresPermission(Iris.perm.studio);
		setDescription("Check project for warnings and issues");
		setCategory("Studio");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args) {
		if (args.length != 1) {
			sender.sendMessage("/iris std verify <DIMENSION> (file name without .json)");
		}

		sender.hr();
		KList<String> mm = Iris.proj.analyze(Iris.instance.getDataFile("packs", args[0]));
		mm.forEach((m) -> sender.sendMessage(m));
		int e = 0;
		int w = 0;

		for (String i : mm) {
			if (i.contains("ERROR")) {
				e++;
			}

			if (i.contains("WARN")) {
				w++;
			}
		}

		sender.sendMessage(w + " Warning(s), " + e + " Error(s)");

		sender.hr();
		return true;
	}

	@Override
	protected String getArgsUsage() {
		return "[dimension]";
	}
}
