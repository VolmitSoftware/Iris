package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.C;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CommandIrisStudioOpen extends MortarCommand
{
	public CommandIrisStudioOpen()
	{
		super("open", "o");
		requiresPermission(Iris.perm.studio);
		setDescription("Create a new temporary world to design a dimension.");
		setCategory("Studio");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
			return true;
		}
		
		if(args.length < 1)
		{
			sender.sendMessage("/iris std open <DIMENSION> (file name without .json)");
			return true;
		}

		if(sender.isPlayer())
		{
			sender.player().setGameMode(GameMode.SPECTATOR);
			sender.player().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2000, 10));
			sender.player().addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 2000, 10));
			sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.BLUE + "Creating studio world. Please wait..."));
		}

		Iris.proj.open(sender, args[0]);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension]";
	}
}
