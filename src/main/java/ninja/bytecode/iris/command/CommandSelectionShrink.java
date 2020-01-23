package ninja.bytecode.iris.command;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import ninja.bytecode.iris.controller.WandController;
import ninja.bytecode.iris.util.Cuboid;
import ninja.bytecode.iris.util.Cuboid.CuboidDirection;

public class CommandSelectionShrink extends MortarCommand
{
	public CommandSelectionShrink()
	{
		super("shrinkwrap", "shrink");
		setDescription("Match blocks boundary");
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
		Location a1 = b[0].clone();
		Location a2 = b[1].clone();
		Cuboid cursor = new Cuboid(a1, a2);
		cursor = cursor.contract(CuboidDirection.North);
		cursor = cursor.contract(CuboidDirection.South);
		cursor = cursor.contract(CuboidDirection.East);
		cursor = cursor.contract(CuboidDirection.West);
		cursor = cursor.contract(CuboidDirection.Up);
		cursor = cursor.contract(CuboidDirection.Down);
		b[0] = cursor.getLowerNE();
		b[1] = cursor.getUpperSW();
		p.getInventory().setItemInMainHand(WandController.createWand(b[0], b[1]));
		p.updateInventory();
		p.playSound(p.getLocation(), Sound.ENTITY_ITEMFRAME_ROTATE_ITEM, 1f, 0.55f);
		
		return true;
	}

}
