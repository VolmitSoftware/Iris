package ninja.bytecode.iris.util;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import net.minecraft.server.v1_12_R1.BiomeBase;
import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.IBlockData;
import ninja.bytecode.shuriken.format.Form;

public class RealBiome
{
	private Biome b;
	private double temperature;
	private double height;
	private double humidity;
	private MB surface;
	private MB dirt;
	
	public RealBiome(Biome b)
	{
		this.b = b;
		BiomeBase base = BiomeBase.a(b.ordinal());
		surface = toMB(base.q);
		dirt = toMB(base.r);
		temperature = base.getTemperature();
		humidity = base.getHumidity();
		height = base.j();
	}
	
	public String toString()
	{
		return Form.capitalizeWords(b.toString().toLowerCase().replaceAll("\\Q_\\E", " ")) + " Temp: " + temperature + " Humidity: " + humidity + " Height: " + height + " Surf: " + Form.capitalizeWords(surface.material.toString().replaceAll("_", " ").toLowerCase())+ " Dirt: " + Form.capitalizeWords(dirt.material.toString().replaceAll("_", " ").toLowerCase());
	}
	
	@SuppressWarnings("deprecation")
	public MB toMB(IBlockData d)
	{
		int i = Block.getCombinedId(d);
		int j = i & 4095;
		int k = i >> 12 & 15;
		return new MB(Material.getMaterial(j), k);
	}
}