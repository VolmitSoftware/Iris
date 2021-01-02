package com.volmit.iris.util;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Field;

/**
 * Represents a virtual command. A chain of iterative processing through
 * subcommands.
 *
 * @author cyberpwn
 *
 */
public class VirtualCommand
{
	private ICommand command;
	private String tag;

	private KMap<KList<String>, VirtualCommand> children;

	public VirtualCommand(ICommand command)
	{
		this(command, "");
	}

	public VirtualCommand(ICommand command, String tag)
	{
		this.command = command;
		children = new KMap<KList<String>, VirtualCommand>();
		this.tag = tag;

		for(Field i : command.getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Command.class))
			{
				try
				{
					Command cc = i.getAnnotation(Command.class);
					ICommand cmd = (ICommand) i.getType().getConstructor().newInstance();
					new V(command, true, true).set(i.getName(), cmd);
					children.put(cmd.getAllNodes(), new VirtualCommand(cmd, cc.value().trim().isEmpty() ? tag : cc.value().trim()));
				}

				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	public String getTag()
	{
		return tag;
	}

	public ICommand getCommand()
	{
		return command;
	}

	public KMap<KList<String>, VirtualCommand> getChildren()
	{
		return children;
	}

	public boolean hit(CommandSender sender, KList<String> chain)
	{
		return hit(sender, chain, null);
	}

	public boolean hit(CommandSender sender, KList<String> chain, String label)
	{
		MortarSender vs = new MortarSender(sender);
		vs.setTag(tag);

		if(label != null)
		{
			vs.setCommand(label);
		}

		if(chain.isEmpty())
		{
			if(!checkPermissions(sender, command))
			{
				return true;
			}

			return command.handle(vs, new String[0]);
		}

		String nl = chain.get(0);

		for(KList<String> i : children.k())
		{
			for(String j : i)
			{
				if(j.equalsIgnoreCase(nl))
				{
					vs.setCommand(chain.get(0));
					VirtualCommand cmd = children.get(i);
					KList<String> c = chain.copy();
					c.remove(0);
					if(cmd.hit(sender, c, vs.getCommand()))
					{
						if(vs.isPlayer() && IrisSettings.get().getGeneral().isCommandSounds())
						{
							vs.player().getWorld().playSound(vs.player().getLocation(), Sound.ITEM_AXE_STRIP, 0.35f, 1.8f);
						}

						return true;
					}
				}
			}
		}

		if(!checkPermissions(sender, command))
		{
			return true;
		}

		return command.handle(vs, chain.toArray(new String[chain.size()]));
	}

	public KList<String> hitTab(CommandSender sender, KList<String> chain, String label)
	{
		MortarSender vs = new MortarSender(sender);
		vs.setTag(tag);

		if(label != null)
			vs.setCommand(label);

		if(chain.isEmpty())
		{
			if(!checkPermissions(sender, command))
			{
				return null;
			}

			return command.handleTab(vs, new String[0]);
		}

		String nl = chain.get(0);

		for(KList<String> i : children.k())
		{
			for(String j : i)
			{
				if(j.equalsIgnoreCase(nl))
				{
					vs.setCommand(chain.get(0));
					VirtualCommand cmd = children.get(i);
					KList<String> c = chain.copy();
					c.remove(0);
					KList<String> v = cmd.hitTab(sender, c, vs.getCommand());
					if(v != null)
					{
						return v;
					}
				}
			}
		}

		if(!checkPermissions(sender, command))
		{
			return null;
		}

		return command.handleTab(vs, chain.toArray(new String[chain.size()]));
	}

	private boolean checkPermissions(CommandSender sender, ICommand command2)
	{
		boolean failed = false;

		for(String i : command.getRequiredPermissions())
		{
			if(!sender.hasPermission(i))
			{
				failed = true;
				Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> sender.sendMessage("- " + C.WHITE + i), 0);
			}
		}

		if(failed)
		{
			sender.sendMessage("Insufficient Permissions");
			return false;
		}

		return true;
	}
}
