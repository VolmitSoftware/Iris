package ninja.bytecode.iris.command;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import ninja.bytecode.iris.controller.WandController;
import ninja.bytecode.iris.util.Cuboid;
import ninja.bytecode.iris.util.Cuboid.CuboidDirection;

public class CommandSelectionXUp extends MortarCommand
{
	public CommandSelectionXUp()
	{
		super("expandup", "xup");
		setDescription("Expand Up & Trim In");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!sender.isPlayer())
		{
			sender.sendMessage("Players Only");
			return true;
		}

		Player p = sender.player();

		if(!WandController.isWand(p))
		{
			sender.sendMessage("Ready your Wand.");
			return true;
		}

		Location[] b = WandController.getCuboid(p.getInventory().getItemInMainHand());
		b[0].add(new Vector(0, 1, 0));
		b[1].add(new Vector(0, 1, 0));
		Location a1 = b[0].clone();
		Location a2 = b[1].clone();
		Cuboid cursor = new Cuboid(a1, a2);

		while(!cursor.containsOnly(Material.AIR))
		{
			a1.add(new Vector(0, 1, 0));
			a2.add(new Vector(0, 1, 0));
			cursor = new Cuboid(a1, a2);
		}

		a1.add(new Vector(0, -1, 0));
		a2.add(new Vector(0, -1, 0));
		b[0] = a1;
		a2 = b[1];
		cursor = new Cuboid(a1, a2);
		cursor = cursor.contract(CuboidDirection.North);
		cursor = cursor.contract(CuboidDirection.South);
		cursor = cursor.contract(CuboidDirection.East);
		cursor = cursor.contract(CuboidDirection.West);
		b[0] = cursor.getLowerNE();
		b[1] = cursor.getUpperSW();
		p.getInventory().setItemInMainHand(WandController.createWand(b[0], b[1]));
		p.updateInventory();
		p.playSound(p.getLocation(), Sound.ENTITY_ITEMFRAME_ROTATE_ITEM, 1f, 0.55f);

		return true;
	}

}
