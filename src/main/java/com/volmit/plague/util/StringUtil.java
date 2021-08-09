package com.volmit.plague.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

public class StringUtil
{
	public static byte[] utfToBytes(String str) throws IOException
	{
		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(boas);
		dos.writeUTF(str);
		dos.flush();

		return boas.toByteArray();
	}

	public static String bytesToUTF(byte[] data) throws IOException
	{
		DataInputStream dos = new DataInputStream(new ByteArrayInputStream(data));
		return dos.readUTF();
	}

	public static String worldToString(World w)
	{
		return w.getName();
	}

	public static World worldFromString(String w)
	{
		return Bukkit.getWorld(w);
	}

	public static String vectorToString(Vector v)
	{
		return vectorToString(v, true);
	}

	public static String vectorToString(Vector v, boolean accurate)
	{
		return (accurate ? v.getX() : v.getBlockX()) + ";" + (accurate ? v.getY() : v.getBlockY()) + ";" + (accurate ? v.getZ() : v.getBlockZ());
	}

	public static Vector vectorFromString(String v)
	{
		String[] a = v.split(";");

		if(a.length == 3)
		{
			return new Vector(Double.valueOf(a[0]), Double.valueOf(a[1]), Double.valueOf(a[2]));
		}

		return null;
	}

	public static String locationToString(Location l)
	{
		return locationToString(l, true, true);
	}

	public static String locationToString(Location l, boolean accurate, boolean direction)
	{
		return worldToString(l.getWorld()) + ";" + (accurate ? l.getX() : l.getBlockX()) + ";" + (accurate ? l.getY() : l.getBlockY()) + ";" + (accurate ? l.getZ() : l.getBlockZ()) + (direction ? ";" + l.getYaw() + ";" + l.getPitch() : "");
	}

	public static Location locationFromString(String l)
	{
		String[] a = l.split(";");
		World w = null;
		double x = 0;
		double y = 0;
		double z = 0;
		float yaw = 0f;
		float pitch = 0f;

		if(a.length >= 4)
		{
			w = worldFromString(a[0]);
			x = Double.valueOf(a[1]);
			y = Double.valueOf(a[2]);
			z = Double.valueOf(a[3]);

			if(a.length >= 6)
			{
				yaw = Float.valueOf(a[4]);
				pitch = Float.valueOf(a[5]);
			}
		}

		if(w != null)
		{
			return new Location(w, x, y, z, yaw, pitch);
		}

		return null;
	}
}
