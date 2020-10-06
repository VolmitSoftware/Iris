package com.volmit.iris.manager;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.UUID;

import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.object.InterpolationMethod;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeGeneratorLink;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisInterpolator;
import com.volmit.iris.object.IrisNoiseGenerator;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.J;
import com.volmit.iris.util.JSONArray;
import com.volmit.iris.util.JSONException;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.MortarSender;

import lombok.Data;

@Data
public class ProjectManager
{
	public static final String workspaceName = "packs";
	private KMap<String, String> cacheListing = null;
	private IrisProject activeProject;

	public ProjectManager()
	{
		if(IrisSettings.get().isStudio())
		{
			J.a(() ->
			{
				File ignore = getWorkspaceFile(".gitignore");

				if(!ignore.exists())
				{
					File m = Iris.getCached("Pack Ignore (.gitignore)", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/packignore.ignore");
					if(m != null)
					{
						try
						{
							IO.copyFile(m, ignore);
						}

						catch(IOException e)
						{

						}
					}
				}
			});
		}
	}

	public IrisDimension installIntoWorld(MortarSender sender, String type, File folder)
	{
		sender.sendMessage("Looking for Package: " + type);
		File iris = new File(folder, "iris");
		IrisDimension dim = Iris.globaldata.getDimensionLoader().load(type);

		if(dim == null)
		{
			for(File i : Iris.proj.getWorkspaceFolder().listFiles())
			{
				if(i.isFile() && i.getName().equals(type + ".iris"))
				{
					sender.sendMessage("Found " + type + ".iris in " + ProjectManager.workspaceName + " folder");
					ZipUtil.unpack(i, iris);
					break;
				}
			}
		}

		else
		{
			sender.sendMessage("Found " + type + " dimension in " + ProjectManager.workspaceName + " folder. Repackaging");
			ZipUtil.unpack(Iris.proj.compilePackage(sender, type, true, true), iris);
		}

		File dimf = new File(iris, "dimensions/" + type + ".json");

		if(!dimf.exists() || !dimf.isFile())
		{
			Iris.globaldata.dump();
			Iris.globaldata.preferFolder(null);
			Iris.proj.downloadSearch(sender, type, false);
			File downloaded = Iris.proj.getWorkspaceFolder(type);

			for(File i : downloaded.listFiles())
			{
				if(i.isFile())
				{
					try
					{
						FileUtils.copyFile(i, new File(iris, i.getName()));
					}

					catch(IOException e)
					{
						e.printStackTrace();
					}
				}

				else
				{
					try
					{
						FileUtils.copyDirectory(i, new File(iris, i.getName()));
					}

					catch(IOException e)
					{
						e.printStackTrace();
					}
				}
			}

			IO.delete(downloaded);
		}

		if(!dimf.exists() || !dimf.isFile())
		{
			sender.sendMessage("Can't find the " + dimf.getName() + " in the dimensions folder of this pack! Failed!");
			return null;
		}

		IrisDataManager dm = new IrisDataManager(folder);
		dim = dm.getDimensionLoader().load(type);

		if(dim == null)
		{
			sender.sendMessage("Can't load the dimension! Failed!");
			return null;
		}

		sender.sendMessage(folder.getName() + " type installed. ");
		return dim;
	}

	public void downloadSearch(MortarSender sender, String key, boolean trim)
	{
		String repo = getListing(false).get(key);

		if(repo == null)
		{
			sender.sendMessage("Couldn't find the pack '" + key + "' in the iris repo listing.");
			return;
		}

		sender.sendMessage("Found '" + key + "' in the Iris Listing as " + repo);
		try
		{
			download(sender, repo, trim);
		}
		catch(JsonSyntaxException | IOException e)
		{
			sender.sendMessage("Failed to download '" + key + "'.");
		}
	}

	public void download(MortarSender sender, String repo, boolean trim) throws JsonSyntaxException, IOException
	{
		String url = "https://codeload.github.com/" + repo + "/zip/master";
		sender.sendMessage("Downloading " + url);
		File zip = Iris.getNonCachedFile("pack-" + trim + "-" + repo, url);
		File temp = Iris.getTemp();
		File work = new File(temp, "dl-" + UUID.randomUUID());
		File packs = getWorkspaceFolder();
		sender.sendMessage("Unpacking " + repo);
		ZipUtil.unpack(zip, work);
		File dir = work.listFiles().length == 1 && work.listFiles()[0].isDirectory() ? work.listFiles()[0] : null;

		if(dir == null)
		{
			sender.sendMessage("Invalid Format. Missing root folder or too many folders!");
			return;
		}

		File dimensions = new File(dir, "dimensions");

		if(!(dimensions.exists() && dimensions.isDirectory()))
		{
			sender.sendMessage("Invalid Format. Missing dimensions folder");
			return;
		}

		if(dimensions.listFiles().length != 1)
		{
			sender.sendMessage("Dimensions folder must have 1 file in it");
			return;
		}

		File dim = dimensions.listFiles()[0];

		if(!dim.isFile())
		{
			sender.sendMessage("Invalid dimension (folder) in dimensions folder");
			return;
		}

		String key = dim.getName().split("\\Q.\\E")[0];
		IrisDimension d = new Gson().fromJson(IO.readAll(dim), IrisDimension.class);
		sender.sendMessage("Importing " + d.getName() + " (" + key + ")");
		Iris.globaldata.dump();
		Iris.globaldata.preferFolder(null);

		if(Iris.globaldata.getDimensionLoader().load(key) != null)
		{
			sender.sendMessage("Another dimension in the packs folder is already using the key " + key + " IMPORT FAILED!");
			return;
		}

		File packEntry = new File(packs, key);

		if(packEntry.exists() && packEntry.listFiles().length > 0)
		{
			sender.sendMessage("Another pack is using the key " + key + ". IMPORT FAILED!");
			return;
		}

		FileUtils.copyDirectory(dir, packEntry);

		if(trim)
		{
			sender.sendMessage("Trimming " + key);
			File cp = compilePackage(sender, key, false, false);
			IO.delete(packEntry);
			packEntry.mkdirs();
			ZipUtil.unpack(cp, packEntry);
		}

		sender.sendMessage("Successfully Aquired " + d.getName());
		Iris.globaldata.dump();
		Iris.globaldata.preferFolder(null);
	}

	public KMap<String, String> getListing(boolean cached)
	{
		if(cached && cacheListing != null)
		{
			return cacheListing;
		}

		JSONArray a = new JSONArray();

		if(cached)
		{
			a = new JSONArray(Iris.getCached("cachedlisting", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/listing.json"));
		}

		else
		{
			a = new JSONArray(Iris.getNonCached(!cached + "listing", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/listing.json"));
		}

		KMap<String, String> l = new KMap<>();

		for(int i = 0; i < a.length(); i++)
		{
			try
			{
				String m = a.getString(i).trim();
				String[] v = m.split("\\Q \\E");
				l.put(v[0], v[1]);
			}

			catch(Throwable e)
			{

			}
		}

		return l;
	}

	public boolean isProjectOpen()
	{
		return activeProject != null && activeProject.isOpen();
	}

	public void open(MortarSender sender, String dimm)
	{
		open(sender, dimm, () ->
		{
		});
	}

	public void open(MortarSender sender, String dimm, Runnable onDone)
	{
		if(isProjectOpen())
		{
			close();
		}

		IrisProject project = new IrisProject(new File(getWorkspaceFolder(), dimm));
		activeProject = project;
		project.open(sender, onDone);
	}

	public File getWorkspaceFolder(String... sub)
	{
		return Iris.instance.getDataFolderList(workspaceName, sub);
	}

	public File getWorkspaceFile(String... sub)
	{
		return Iris.instance.getDataFileList(workspaceName, sub);
	}

	public void close()
	{
		if(isProjectOpen())
		{
			activeProject.close();
			activeProject = null;
		}
	}

	public File compilePackage(MortarSender sender, String d, boolean obfuscate, boolean minify)
	{
		return new IrisProject(new File(getWorkspaceFolder(), d)).compilePackage(sender, obfuscate, minify);
	}

	public void createFrom(String existingPack, String newName)
	{
		File importPack = getWorkspaceFolder(existingPack);
		File newPack = getWorkspaceFolder(newName);

		if(importPack.listFiles().length == 0)
		{
			Iris.warn("Couldn't find the pack to create a new dimension from.");
			return;
		}

		try
		{
			FileUtils.copyDirectory(importPack, newPack, new FileFilter()
			{
				@Override
				public boolean accept(File pathname)
				{
					return !pathname.getAbsolutePath().contains(".git");
				}
			}, false);
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}

		new File(importPack, existingPack + ".code-workspace").delete();
		File dimFile = new File(importPack, "dimensions/" + existingPack + ".json");
		File newDimFile = new File(newPack, "dimensions/" + newName + ".json");

		try
		{
			FileUtils.copyFile(dimFile, newDimFile);
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}

		new File(newPack, "dimensions/" + existingPack + ".json").delete();

		try
		{
			JSONObject json = new JSONObject(IO.readAll(newDimFile));

			if(json.has("name"))
			{
				json.put("name", Form.capitalizeWords(newName.replaceAll("\\Q-\\E", " ")));
				IO.writeAll(newDimFile, json.toString(4));
			}
		}

		catch(JSONException | IOException e)
		{
			e.printStackTrace();
		}

		try
		{
			IrisProject p = new IrisProject(getWorkspaceFolder(newName));
			JSONObject ws = p.createCodeWorkspaceConfig();
			IO.writeAll(getWorkspaceFile(newName, newName + ".code-workspace"), ws.toString(0));
		}

		catch(JSONException | IOException e)
		{
			e.printStackTrace();
		}
	}

	public void create(MortarSender sender, String s, String downloadable)
	{
		boolean shouldDelete = false;
		File importPack = getWorkspaceFolder(downloadable);

		if(importPack.listFiles().length == 0)
		{
			downloadSearch(sender, downloadable, false);

			if(importPack.listFiles().length > 0)
			{
				shouldDelete = true;
			}
		}

		if(importPack.listFiles().length == 0)
		{
			sender.sendMessage("Couldn't find the pack to create a new dimension from.");
			return;
		}

		File importDimensionFile = new File(importPack, "dimensions/" + downloadable + ".json");

		if(!importDimensionFile.exists())
		{
			sender.sendMessage("Missing Imported Dimension File");
			return;
		}

		sender.sendMessage("Importing " + downloadable + " into new Project " + s);
		createFrom(downloadable, s);
		if(shouldDelete)
		{
			importPack.delete();
		}
		open(sender, s);
	}

	public void create(MortarSender sender, String s)
	{
		if(generate(sender, s))
		{
			Iris.proj.open(sender, s);
		}
	}

	private boolean generate(MortarSender sender, String s)
	{
		IrisDimension dimension = new IrisDimension();
		dimension.setLoadKey(s);
		dimension.setName(Form.capitalizeWords(s.replaceAll("\\Q-\\E", " ")));

		if(getWorkspaceFile(dimension.getLoadKey(), "dimensions", dimension.getLoadKey() + ".json").exists())
		{
			sender.sendMessage("Project Already Exists! Open it instead!");
			return false;
		}
		sender.sendMessage("Creating New Project \"" + dimension.getName() + "\"...");
		IrisRegion exampleRegion = new IrisRegion();
		exampleRegion.setName("Example Region");
		exampleRegion.setLoadKey("example-region");
		IrisBiome exampleLand1 = new IrisBiome();
		exampleLand1.setName("Example Land 1");
		exampleLand1.setLoadKey("land-1");
		IrisBiome exampleShore1 = new IrisBiome();
		exampleShore1.setName("Example Shore");
		exampleShore1.setLoadKey("shore");
		IrisBiome exampleOcean1 = new IrisBiome();
		exampleOcean1.setName("Example Sea");
		exampleOcean1.setLoadKey("sea");
		IrisBiome exampleLand2 = new IrisBiome();
		exampleLand2.setName("Example Land 2");
		exampleLand2.setLoadKey("land-2");
		exampleLand2.setRarity(4);
		dimension.setSeaZoom(1);
		dimension.setLandZoom(1.5);
		IrisGenerator gen = new IrisGenerator();
		IrisNoiseGenerator gg = new IrisNoiseGenerator(true);
		gen.getComposite().add(gg);
		IrisInterpolator it = new IrisInterpolator();
		it.setFunction(InterpolationMethod.BILINEAR_STARCAST_9);
		it.setHorizontalScale(9);
		gen.setInterpolator(it);
		gen.setLoadKey("example-generator");
		IrisBiomeGeneratorLink b1 = new IrisBiomeGeneratorLink();
		b1.setGenerator(gen.getLoadKey());
		b1.setMin(3);
		b1.setMax(7);
		IrisBiomeGeneratorLink b2 = new IrisBiomeGeneratorLink();
		b2.setGenerator(gen.getLoadKey());
		b2.setMin(12);
		b2.setMax(35);
		IrisBiomeGeneratorLink b3 = new IrisBiomeGeneratorLink();
		b3.setGenerator(gen.getLoadKey());
		b3.setMin(-1);
		b3.setMax(1);
		IrisBiomeGeneratorLink b4 = new IrisBiomeGeneratorLink();
		b4.setGenerator(gen.getLoadKey());
		b4.setMin(-5);
		b4.setMax(-38);
		exampleLand1.getGenerators().clear();
		exampleLand1.getGenerators().add(b1);
		exampleLand2.getGenerators().clear();
		exampleLand2.getGenerators().add(b2);
		exampleShore1.getGenerators().clear();
		exampleShore1.getGenerators().add(b3);
		exampleOcean1.getGenerators().clear();
		exampleOcean1.getGenerators().add(b4);
		exampleRegion.getLandBiomes().add(exampleLand1.getLoadKey());
		exampleRegion.getLandBiomes().add(exampleLand2.getLoadKey());
		exampleRegion.getShoreBiomes().add(exampleShore1.getLoadKey());
		exampleRegion.getSeaBiomes().add(exampleOcean1.getLoadKey());
		dimension.getRegions().add(exampleRegion.getLoadKey());
		IrisProject project = new IrisProject(getWorkspaceFolder(dimension.getLoadKey()));
		try
		{
			JSONObject ws = project.createCodeWorkspaceConfig();
			IO.writeAll(getWorkspaceFile(dimension.getLoadKey(), "dimensions", dimension.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(dimension)).toString(4));
			IO.writeAll(getWorkspaceFile(dimension.getLoadKey(), "regions", exampleRegion.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleRegion)).toString(4));
			IO.writeAll(getWorkspaceFile(dimension.getLoadKey(), "biomes", exampleLand1.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleLand1)).toString(4));
			IO.writeAll(getWorkspaceFile(dimension.getLoadKey(), "biomes", exampleLand2.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleLand2)).toString(4));
			IO.writeAll(getWorkspaceFile(dimension.getLoadKey(), "biomes", exampleShore1.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleShore1)).toString(4));
			IO.writeAll(getWorkspaceFile(dimension.getLoadKey(), "biomes", exampleOcean1.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(exampleOcean1)).toString(4));
			IO.writeAll(getWorkspaceFile(dimension.getLoadKey(), "generators", gen.getLoadKey() + ".json"), new JSONObject(new Gson().toJson(gen)).toString(4));
			IO.writeAll(getWorkspaceFile(dimension.getLoadKey(), dimension.getLoadKey() + ".code-workspace"), ws.toString(0));
		}

		catch(JSONException | IOException e)
		{
			sender.sendMessage("Failed! Check the console.");
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public void updateWorkspace()
	{
		if(isProjectOpen())
		{
			activeProject.updateWorkspace();
		}
	}
}
