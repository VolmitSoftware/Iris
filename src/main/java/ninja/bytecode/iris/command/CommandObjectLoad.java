package ninja.bytecode.iris.command;

import java.io.File;
import java.io.FileInputStream;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.WandController;
import ninja.bytecode.iris.generator.genobject.GenObject;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.shuriken.format.Form;

public class CommandObjectLoad extends MortarCommand
{
	public CommandObjectLoad()
	{
		super("load", "l");
		setDescription("Load & Paste an object");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!sender.isPlayer())
		{
			sender.sendMessage("Players Only");
			return true;
		}

		if(args.length < 1)
		{
			sender.sendMessage("/iris object load <name>");
			return true;
		}

		Player p = sender.player();

		GenObject s = new GenObject(1, 1, 1);
		File f = new File(Iris.instance.getDataFolder(), "schematics/" + args[0] + ".ish");
		if(!f.exists())
		{
			sender.sendMessage("Can't find " + args[0]);
			return true;
		}

		try
		{
			FileInputStream fin = new FileInputStream(f);
			s.read(fin, true);

			boolean cursor = false;
			Direction df = null;
			Direction dt = null;

			for(String i : args)
			{
				if(i.equalsIgnoreCase("cursor"))
				{
					cursor = true;
					break;
				}

				if(i.startsWith("from:"))
				{
					df = Direction.valueOf(i.split("\\Q:\\E")[1].toUpperCase().substring(0, 1));
				}

				if(i.startsWith("to:"))
				{
					dt = Direction.valueOf(i.split("\\Q:\\E")[1].toUpperCase().substring(0, 1));
				}
			}

			if(dt != null && df != null)
			{
				sender.sendMessage("Rotating " + C.WHITE + df + C.GRAY + " to " + C.WHITE + dt);
				s.rotate(df, dt);
			}

			Location at = p.getLocation();

			if(cursor)
			{
				at = p.getTargetBlock(null, 64).getLocation();
			}

			WandController.pasteSchematic(s, at);
			p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.25f);
			sender.sendMessage("Pasted " + args[0] + " (" + Form.f(s.getSchematic().size()) + " Blocks Modified)");
		}

		catch(Throwable e1)
		{
			e1.printStackTrace();
		}

		return true;
	}

}
