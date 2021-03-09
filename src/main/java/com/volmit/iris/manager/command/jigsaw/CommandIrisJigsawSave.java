package com.volmit.iris.manager.command.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.edit.JigsawEditor;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisJigsawSave extends MortarCommand
{
	public CommandIrisJigsawSave()
	{
		super("save");
		requiresPermission(Iris.perm);
		setCategory("Jigsaw");
		setDescription("Save a currently open piece");
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

		JigsawEditor editor = JigsawEditor.editors.get(sender.player());

		if(editor == null)
		{
			sender.sendMessage("You don't have any pieces open to save!");
			return true;
		}

		editor.close();
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
