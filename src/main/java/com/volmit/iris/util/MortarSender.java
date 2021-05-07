package com.volmit.iris.util;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a volume sender. A command sender with extra crap in it
 *
 * @author cyberpwn
 *
 */
public class MortarSender implements CommandSender
{
	private CommandSender s;
	private String tag;

	@Getter
	@Setter
	private String command;

	/**
	 * Wrap a command sender
	 *
	 * @param s
	 *            the command sender
	 */
	public MortarSender(CommandSender s)
	{
		tag = "";
		this.s = s;
	}

	public MortarSender(CommandSender s, String tag)
	{
		this.tag = tag;
		this.s = s;
	}

	/**
	 * Set a command tag (prefix for sendMessage)
	 *
	 * @param tag
	 *            the tag
	 */
	public void setTag(String tag)
	{
		this.tag = tag;
	}

	/**
	 * Get the command tag
	 *
	 * @return the command tag
	 */
	public String getTag()
	{
		return tag;
	}

	/**
	 * Is this sender a player?
	 *
	 * @return true if it is
	 */
	public boolean isPlayer()
	{
		return getS() instanceof Player;
	}

	/**
	 * Force cast to player (be sure to check first)
	 *
	 * @return a casted player
	 */
	public Player player()
	{
		return (Player) getS();
	}

	/**
	 * Get the origin sender this object is wrapping
	 *
	 * @return the command sender
	 */
	public CommandSender getS()
	{
		return s;
	}

	@Override
	public boolean isPermissionSet(String name)
	{
		return s.isPermissionSet(name);
	}

	@Override
	public boolean isPermissionSet(Permission perm)
	{
		return s.isPermissionSet(perm);
	}

	@Override
	public boolean hasPermission(String name)
	{
		return s.hasPermission(name);
	}

	@Override
	public boolean hasPermission(Permission perm)
	{
		return s.hasPermission(perm);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value)
	{
		return s.addAttachment(plugin, name, value);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin plugin)
	{
		return s.addAttachment(plugin);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks)
	{
		return s.addAttachment(plugin, name, value, ticks);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin plugin, int ticks)
	{
		return s.addAttachment(plugin, ticks);
	}

	@Override
	public void removeAttachment(PermissionAttachment attachment)
	{
		s.removeAttachment(attachment);
	}

	@Override
	public void recalculatePermissions()
	{
		s.recalculatePermissions();
	}

	@Override
	public Set<PermissionAttachmentInfo> getEffectivePermissions()
	{
		return s.getEffectivePermissions();
	}

	@Override
	public boolean isOp()
	{
		return s.isOp();
	}

	@Override
	public void setOp(boolean value)
	{
		s.setOp(value);
	}

	public void hr()
	{
		s.sendMessage("========================================================");
	}

	@Override
	public void sendMessage(String message)
	{
		s.sendMessage(C.translateAlternateColorCodes('&', getTag()) + message);
	}

	@Override
	public void sendMessage(String[] messages)
	{
		for(String str : messages)
			s.sendMessage(C.translateAlternateColorCodes('&', getTag() + str));
	}

	@Override
	public void sendMessage(@Nullable UUID uuid, @NotNull String message) {
		sendMessage(message);
	}

	@Override
	public void sendMessage(@Nullable UUID uuid, @NotNull String[] messages) {
		sendMessage(messages);
	}

	@Override
	public Server getServer()
	{
		return s.getServer();
	}

	@Override
	public String getName()
	{
		return s.getName();
	}

	@Override
	public Spigot spigot()
	{
		return s.spigot();
	}
}
