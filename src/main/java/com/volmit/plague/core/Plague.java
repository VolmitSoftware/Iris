package com.volmit.plague.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.volmit.plague.api.PlagueSender;
import com.volmit.plague.api.command.KickCommand;
import com.volmit.plague.api.command.PlagueCommand;
import com.volmit.plague.util.C;



public class Plague extends JavaPlugin implements Listener
{
	public void onEnable()
	{
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("test").setExecutor(new CommandExecutor()
		{
			@Override
			public boolean onCommand(CommandSender s, Command arg1, String arg2, String[] arg3)
			{
				PlagueSender sender = new PlagueSender(s);
				KickCommand kc = new KickCommand();
				kc.handle(sender, PlagueCommand.enhanceArgs(arg3));
				return true;
			}
		});
	}

	public void onDisable()
	{
		HandlerList.unregisterAll((Plugin) this);
	}
}
