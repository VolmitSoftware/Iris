package com.volmit.iris.manager.command.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.manager.edit.JigsawEditor;
import com.volmit.iris.object.IrisJigsawPiece;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

import java.io.File;

public class CommandIrisJigsawEdit extends MortarCommand
{
	public CommandIrisJigsawEdit()
	{
		super("edit", "e", "*");
		requiresPermission(Iris.perm);
		setCategory("Jigsaw");
		setDescription("Edit an existing Jigsaw piece");
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

		if(args.length < 1)
		{
			sender.sendMessage(getArgsUsage());
			return true;
		}

		IrisJigsawPiece piece = IrisDataManager.loadAnyJigsawPiece(args[0]);

		if(piece != null)
		{
			File dest = piece.getLoadFile();
			new JigsawEditor(sender.player(), piece, IrisDataManager.loadAnyObject(piece.getObject()), dest);
			return true;
		}

		sender.sendMessage("Failed to find existing jigsaw piece: " + args[0]);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<name>";
	}
}
