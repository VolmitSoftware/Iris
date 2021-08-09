package com.volmit.plague.api;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import com.volmit.plague.util.C;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlagueSender implements CommandSender
{
	private final CommandSender s;
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
	public PlagueSender(CommandSender s)
	{
		tag = "";
		this.s = s;
	}
	
	public PlagueSender(CommandSender s, String tag)
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
	public boolean isPermissionSet(@NotNull String name)
	{
		return s.isPermissionSet(name);
	}

	@Override
	public boolean isPermissionSet(@NotNull Permission perm)
	{
		return s.isPermissionSet(perm);
	}

	@Override
	public boolean hasPermission(@NotNull String name)
	{
		return s.hasPermission(name);
	}

	@Override
	public boolean hasPermission(@NotNull Permission perm)
	{
		return s.hasPermission(perm);
	}

	@Override
	public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value)
	{
		return s.addAttachment(plugin, name, value);
	}

	@Override
	public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin)
	{
		return s.addAttachment(plugin);
	}

	@Override
	public PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value, int ticks)
	{
		return s.addAttachment(plugin, name, value, ticks);
	}

	@Override
	public PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks)
	{
		return s.addAttachment(plugin, ticks);
	}

	@Override
	public void removeAttachment(@NotNull PermissionAttachment attachment)
	{
		s.removeAttachment(attachment);
	}

	@Override
	public void recalculatePermissions()
	{
		s.recalculatePermissions();
	}

	@Override
	public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions()
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

	@Override
	public void sendMessage(@NotNull String message)
	{
		s.sendMessage(C.translateAlternateColorCodes('&', getTag()) + message);
	}

	@Override
	public void sendMessage(String[] messages)
	{
		for (String str : messages)
			s.sendMessage(C.translateAlternateColorCodes('&', getTag() + str));
	}

	@Override
	public void sendMessage(@Nullable UUID sender, @NotNull String message) {
		s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
	}

	@Override
	public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {
		sendMessage(messages);
	}

	@Override
	public @NotNull Server getServer()
	{
		return s.getServer();
	}

	@Override
	public @NotNull String getName()
	{
		return s.getName();
	}

	@Override
	public @NotNull Spigot spigot()
	{
		return s.spigot();
	}
}
