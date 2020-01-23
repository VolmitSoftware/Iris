package ninja.bytecode.iris.command;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.WandController;
import ninja.bytecode.iris.generator.genobject.GenObject;
import ninja.bytecode.iris.util.Cuboid;
import ninja.bytecode.iris.util.Cuboid.CuboidDirection;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.shuriken.format.Form;

public class CommandIsh implements CommandExecutor
{
	public void msg(CommandSender s, String msg)
	{
		s.sendMessage(ChatColor.DARK_PURPLE + "[" + ChatColor.GRAY + "Iris" + ChatColor.DARK_PURPLE + "]" + ChatColor.GRAY + ": " + msg);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if(args.length == 0)
		{
			msg(sender, "/ish wand - Get an Iris Wand");
			msg(sender, "/ish save <name> - Save Schematic");
			msg(sender, "/ish load <name> [cursor] - Paste Schematic");
			msg(sender, "/ish expand <amount> - Expand Cuboid in direction");
			msg(sender, "/ish shift <amount> - Shift Cuboid in direction");
			msg(sender, "/ish shrinkwrap - Shrink to blocks");
			msg(sender, "/ish xup - Shift up, Expand up, Contract in.");
			msg(sender, "/ish xvert - Expand up, Expand down, Contract in.");
			msg(sender, "/ish id - What id am i looking at");
		}

		if(args.length > 0)
		{
			if(sender instanceof Player)
			{
				Player p = (Player) sender;
				if(args[0].equalsIgnoreCase("wand"))
				{
					p.getInventory().addItem(WandController.createWand());
					p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1.55f);
				}

				if(args[0].equalsIgnoreCase("id"))
				{

					Block b = p.getTargetBlock(null, 64);
					msg(p, b.getType().getId() + ":" + b.getData() + " (" + b.getType().toString() + ":" + b.getData() + ")");
				}

				if(args[0].equalsIgnoreCase("save"))
				{
					GenObject s = WandController.createSchematic(p.getInventory().getItemInMainHand(), p.getLocation());
					File f = new File(Iris.instance.getDataFolder(), "schematics/" + args[1] + ".ish");
					f.getParentFile().mkdirs();
					try
					{
						FileOutputStream fos = new FileOutputStream(f);
						s.write(fos, true);
						msg(p, "Saved " + args[1] + " (" + Form.f(s.getSchematic().size()) + " Entries)");
						p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.45f);
					}

					catch(Throwable e1)
					{
						e1.printStackTrace();
					}
				}

				if(args[0].equalsIgnoreCase("load"))
				{
					GenObject s = new GenObject(1, 1, 1);
					File f = new File(Iris.instance.getDataFolder(), "schematics/" + args[1] + ".ish");
					if(!f.exists())
					{
						msg(p, "Not Found");
						return true;
					}

					try
					{
						FileInputStream fin = new FileInputStream(f);
						s.read(fin, true);

						boolean cursor = false;
						for(String i : args)
						{
							if(i.equalsIgnoreCase("cursor"))
							{
								cursor = true;
								break;
							}
						}

						Location at = p.getLocation();

						if(cursor)
						{
							at = p.getTargetBlock(null, 64).getLocation();
						}

						WandController.pasteSchematic(s, at);
						p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.25f);
						msg(p, "Pasted " + args[1] + " (" + Form.f(s.getSchematic().size()) + " Blocks Modified)");
					}

					catch(Throwable e1)
					{
						e1.printStackTrace();
					}
				}

				if(args[0].equalsIgnoreCase("xup"))
				{
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
				}

				if(args[0].equalsIgnoreCase("shrinkwrap") || args[0].equalsIgnoreCase("shrink"))
				{
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
				}

				if(args[0].equalsIgnoreCase("expand"))
				{
					int amt = Integer.valueOf(args[1]);
					Location[] b = WandController.getCuboid(p.getInventory().getItemInMainHand());
					Location a1 = b[0].clone();
					Location a2 = b[1].clone();
					Cuboid cursor = new Cuboid(a1, a2);
					Direction d = Direction.closest(p.getLocation().getDirection()).reverse();
					cursor = cursor.expand(d, amt);
					b[0] = cursor.getLowerNE();
					b[1] = cursor.getUpperSW();
					p.getInventory().setItemInMainHand(WandController.createWand(b[0], b[1]));
					p.updateInventory();
					p.playSound(p.getLocation(), Sound.ENTITY_ITEMFRAME_ROTATE_ITEM, 1f, 0.55f);
				}

				if(args[0].equalsIgnoreCase("shift"))
				{
					int amt = Integer.valueOf(args[1]);
					Location[] b = WandController.getCuboid(p.getInventory().getItemInMainHand());
					Location a1 = b[0].clone();
					Location a2 = b[1].clone();
					Direction d = Direction.closest(p.getLocation().getDirection()).reverse();
					a1.add(d.toVector().multiply(amt));
					a2.add(d.toVector().multiply(amt));
					Cuboid cursor = new Cuboid(a1, a2);
					b[0] = cursor.getLowerNE();
					b[1] = cursor.getUpperSW();
					p.getInventory().setItemInMainHand(WandController.createWand(b[0], b[1]));
					p.updateInventory();
					p.playSound(p.getLocation(), Sound.ENTITY_ITEMFRAME_ROTATE_ITEM, 1f, 0.55f);
				}

				if(args[0].equalsIgnoreCase("xvert"))
				{
					Location[] b = WandController.getCuboid(p.getInventory().getItemInMainHand());
					Location a1 = b[0].clone();
					Location a2 = b[1].clone();
					Location a1x = b[0].clone();
					Location a2x = b[1].clone();
					Cuboid cursor = new Cuboid(a1, a2);
					Cuboid cursorx = new Cuboid(a1, a2);

					while(!cursor.containsOnly(Material.AIR))
					{
						a1.add(new Vector(0, 1, 0));
						a2.add(new Vector(0, 1, 0));
						cursor = new Cuboid(a1, a2);
					}

					a1.add(new Vector(0, -1, 0));
					a2.add(new Vector(0, -1, 0));

					while(!cursorx.containsOnly(Material.AIR))
					{
						a1x.add(new Vector(0, -1, 0));
						a2x.add(new Vector(0, -1, 0));
						cursorx = new Cuboid(a1x, a2x);
					}

					a1x.add(new Vector(0, 1, 0));
					a2x.add(new Vector(0, 1, 0));
					b[0] = a1;
					b[1] = a2x;
					cursor = new Cuboid(b[0], b[1]);
					cursor = cursor.contract(CuboidDirection.North);
					cursor = cursor.contract(CuboidDirection.South);
					cursor = cursor.contract(CuboidDirection.East);
					cursor = cursor.contract(CuboidDirection.West);
					b[0] = cursor.getLowerNE();
					b[1] = cursor.getUpperSW();
					p.getInventory().setItemInMainHand(WandController.createWand(b[0], b[1]));
					p.updateInventory();
					p.playSound(p.getLocation(), Sound.ENTITY_ITEMFRAME_ROTATE_ITEM, 1f, 0.55f);
				}
			}
		}

		return false;
	}
}
