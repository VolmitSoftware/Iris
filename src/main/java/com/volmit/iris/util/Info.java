package com.volmit.iris.util;

import org.bukkit.Bukkit;

public class Info
{
	public static String getPortIP()
	{
		return Bukkit.getPort() + Bukkit.getIp();
	}
}
