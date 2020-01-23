package ninja.bytecode.iris.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import mortar.api.sched.SR;
import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import mortar.lang.collection.FinalInteger;
import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.genobject.GenObject;
import ninja.bytecode.iris.generator.genobject.GenObjectGroup;
import ninja.bytecode.iris.generator.genobject.PlacedObject;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.format.Form;

public class CommandWhatObject extends MortarCommand
{
	private KMap<String, GenObject> goc;
	private KMap<String, GenObjectGroup> gog;

	public CommandWhatObject()
	{
		super("object", "o");
		setDescription("WAYLA For Objects");
		goc = new KMap<>();
		gog = new KMap<>();
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		World world = null;

		if(sender.isPlayer() && sender.player().getWorld().getGenerator() instanceof IrisGenerator)
		{
			world = sender.player().getWorld();
		}

		else
		{
			sender.sendMessage("Console / Non-Iris World.");
			return true;
		}

		Player p = sender.player();
		IrisGenerator generator = (IrisGenerator) world.getGenerator();
		Location l = p.getTargetBlock(null, 32).getLocation();
		PlacedObject po = generator.nearest(l, 12);

		if(po != null)
		{
			if(!goc.containsKey(po.getF()))
			{
				String root = po.getF().split("\\Q:\\E")[0];
				String n = po.getF().split("\\Q:\\E")[1];
				GenObjectGroup gg = generator.getDimension().getObjectGroup(root);
				gog.put(root, gg);

				for(GenObject i : gg.getSchematics())
				{
					if(i.getName().equals(n))
					{
						goc.put(po.getF(), i);
						break;
					}
				}

				if(!goc.containsKey(po.getF()))
				{
					goc.put(po.getF(), new GenObject(0, 0, 0));
				}
			}

			GenObjectGroup ggg = gog.get(po.getF().split("\\Q:\\E")[0]);
			GenObject g = goc.get(po.getF());

			if(g != null)
			{
				Location point = new Location(l.getWorld(), po.getX(), po.getY(), po.getZ());
				IrisBiome biome = generator.getBiome((int) generator.getOffsetX(po.getX(), po.getZ()), (int) generator.getOffsetZ(po.getX(), po.getZ()));
				String gg = po.getF().split("\\Q:\\E")[0];

				p.sendMessage(C.DARK_GREEN + C.BOLD.toString() + gg + C.GRAY + "/" + C.RESET + C.ITALIC + C.GRAY + g.getName() + C.RESET + C.WHITE + " (1 of " + Form.f(generator.getDimension().getObjectGroup(gg).size()) + " variants)");

				if(biome.getSchematicGroups().containsKey(gg))
				{
					String f = "";
					double percent = biome.getSchematicGroups().get(gg);

					if(percent > 1D)
					{
						f = (int) percent + " + " + Form.pc(percent - (int) percent, percent - (int) percent >= 0.01 ? 0 : 3);
					}

					else
					{
						f = Form.pc(percent, percent >= 0.01 ? 0 : 3);
					}

					p.sendMessage(C.GOLD + "Spawn Chance in " + C.YELLOW + biome.getName() + C.RESET + ": " + C.BOLD + C.WHITE + f);
				}

				try
				{
					int a = 0;
					int b = 0;
					double c = 0;

					for(GenObject i : ggg.getSchematics())
					{
						a += i.getSuccesses();
						b += i.getPlaces();
					}

					c = ((double) a / (double) b);
					p.sendMessage(C.GRAY + "Grp: " + C.DARK_AQUA + Form.f(a) + C.GRAY + " of " + C.AQUA + Form.f(b) + C.GRAY + " placements (" + C.DARK_AQUA + Form.pc(c, 0) + C.GRAY + ")");
				}

				catch(Throwable e)
				{
					e.printStackTrace();
				}

				p.sendMessage(C.GRAY + "Var: " + C.DARK_AQUA + Form.f(g.getSuccesses()) + C.GRAY + " of " + C.AQUA + Form.f(g.getPlaces()) + C.GRAY + " placements (" + C.DARK_AQUA + Form.pc(g.getSuccess(), 0) + C.GRAY + ")");

				for(String i : ggg.getFlags())
				{
					p.sendMessage(C.GRAY + "- " + C.DARK_PURPLE + i);
				}

				FinalInteger fi = new FinalInteger(125);

				new SR()
				{
					@Override
					public void run()
					{
						if(point.distanceSquared(p.getLocation()) > 64 * 64)
						{
							cancel();
						}

						fi.sub(1);
						Iris.wand().draw(new Location[] {point.clone().add(g.getW() / 2, g.getH() / 2, g.getD() / 2), point.clone().subtract(g.getW() / 2, g.getH() / 2, g.getD() / 2)
						}, p);

						if(fi.get() <= 0)
						{
							cancel();
						}
					}
				};

			}
		}

		return true;
	}

}
