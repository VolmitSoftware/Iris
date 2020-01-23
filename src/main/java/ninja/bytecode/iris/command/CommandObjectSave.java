package ninja.bytecode.iris.command;

import java.io.File;
import java.io.FileOutputStream;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.WandController;
import ninja.bytecode.iris.generator.genobject.GenObject;
import ninja.bytecode.shuriken.format.Form;

public class CommandObjectSave extends MortarCommand
{
	public CommandObjectSave()
	{
		super("save", "s");
		setDescription("Save an object");
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
			sender.sendMessage("/iris object save <name>");
			return true;
		}

		Player p = sender.player();
		GenObject s = WandController.createSchematic(p.getInventory().getItemInMainHand(), p.getLocation());

		if(s == null)
		{
			sender.sendMessage("Hold your wand while using this command.");
			return true;
		}

		File f = new File(Iris.instance.getDataFolder(), "schematics/" + args[0] + ".ish");
		f.getParentFile().mkdirs();
		try
		{
			FileOutputStream fos = new FileOutputStream(f);
			s.write(fos, true);
			p.sendMessage("Saved " + args[1] + " (" + Form.f(s.getSchematic().size()) + " Entries)");
			p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.45f);
		}

		catch(Throwable e1)
		{
			p.sendMessage("Failed. Check the console!");
			e1.printStackTrace();
		}

		return true;
	}

}
