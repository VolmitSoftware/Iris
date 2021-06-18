package com.volmit.iris.manager;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.*;
import lombok.Data;
import org.bukkit.potion.PotionEffectType;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class ProjectManager
{
	public static final String LISTING = "https://raw.githubusercontent.com/IrisDimensions/_listing/main/listing-v2.json";
	public static final String WORKSPACE_NAME = "packs";
	private KMap<String, String> cacheListing = null;
	private IrisProject activeProject;
	private static final AtomicCache<Integer> counter = new AtomicCache<>();

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

	public static int countUniqueDimensions() {
		int vv = counter.aquire(() -> {
			int v = 0;

			try
			{
				for(File i : Iris.instance.getDataFolder(WORKSPACE_NAME).listFiles())
				{
					try
					{
						if(i.isDirectory() && i.list().length > 0 && !Iris.proj.getListing(true).keySet().contains(i.getName()))
						{
							v++;
						}
					}

					catch(Throwable ignored)
					{

					}
				}
			}

			catch(Throwable ignored)
			{

			}

			return v;
		});

		return vv;
	}

	public IrisDimension installIntoWorld(MortarSender sender, String type, File folder)
	{
		sender.sendMessage("Looking for Package: " + type);
		File iris = new File(folder, "iris");
		File irispack = new File(folder, "iris/pack");
		IrisDimension dim = IrisDataManager.loadAnyDimension(type);

		if(dim == null)
		{
			for(File i : Iris.proj.getWorkspaceFolder().listFiles())
			{
				if(i.isFile() && i.getName().equals(type + ".iris"))
				{
					sender.sendMessage("Found " + type + ".iris in " + ProjectManager.WORKSPACE_NAME + " folder");
					ZipUtil.unpack(i, irispack);
					break;
				}
			}
		}

		else
		{
			sender.sendMessage("Found " + type + " dimension in " + ProjectManager.WORKSPACE_NAME + " folder. Repackaging");
			File f = new IrisProject(new File(getWorkspaceFolder(), type)).getPath();

			try
			{
				FileUtils.copyDirectory(f, irispack);
			}

			catch(IOException e)
			{

			}
		}

		File dimf = new File(irispack, "dimensions/" + type + ".json");

		if(!dimf.exists() || !dimf.isFile())
		{
			Iris.proj.downloadSearch(sender, type, false);
			File downloaded = Iris.proj.getWorkspaceFolder(type);

			for(File i : downloaded.listFiles())
			{
				if(i.isFile())
				{
					try
					{
						FileUtils.copyFile(i, new File(irispack, i.getName()));
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
						FileUtils.copyDirectory(i, new File(irispack, i.getName()));
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

		IrisDataManager dm = new IrisDataManager(irispack);
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
		downloadSearch(sender, key, trim, false);
	}

	public void downloadSearch(MortarSender sender, String key, boolean trim, boolean forceOverwrite)
	{
		String url = "?";

		try
		{
			url = getListing(false).get(key);
			url = url == null ? key : url;
			Iris.info("Assuming URL " + url);
			String branch = "master";
			String[] nodes = url.split("\\Q/\\E");
			String repo = nodes[0] + "/" + nodes[1];
			branch = nodes.length > 2 ? nodes[2] : branch;
			download(sender, repo, branch, trim, forceOverwrite);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
			sender.sendMessage("Failed to download '" + key + "' from " + url + ".");
		}
	}

	public void download(MortarSender sender, String repo, String branch, boolean trim) throws JsonSyntaxException, IOException
	{
		download(sender, repo, branch, trim, false);
	}

	public void download(MortarSender sender, String repo, String branch, boolean trim, boolean forceOverwrite) throws JsonSyntaxException, IOException
	{
		String url = "https://codeload.github.com/" + repo + "/zip/refs/heads/" + branch;
		sender.sendMessage("Downloading " + url);
		File zip = Iris.getNonCachedFile("pack-" + trim + "-" + repo, url);
		File temp = Iris.getTemp();
		File work = new File(temp, "dl-" + UUID.randomUUID());
		File packs = getWorkspaceFolder();
		sender.sendMessage("Unpacking " + repo);
		try {
			ZipUtil.unpack(zip, work);
		} catch (Throwable e){
			e.printStackTrace();
			sender.sendMessage(
					"Issue when unpacking. Please check/do the following:" +
							"\n1. Do you have a functioning internet connection?" +
							"\n2. Did the download corrupt?" +
							"\n3. Try deleting the */plugins/iris/packs folder and re-download." +
							"\n4. Download the pack from the GitHub repo: https://github.com/IrisDimensions/overworld" +
							"\n5. Contact support (if all other options do not help)"
			);
		}
		File dir = null;
		File[] zipFiles = work.listFiles();

		if (zipFiles == null) {
			sender.sendMessage("No files were extracted from the zip file.");
			return;
		}

		try {
			dir = zipFiles.length == 1 && zipFiles[0].isDirectory() ? zipFiles[0] : null;
		} catch (NullPointerException e) {
			sender.sendMessage("Error when finding home directory. Are there any non-text characters in the file name?");
			return;
		}

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

		if(dimensions.listFiles() == null){
			sender.sendMessage("No dimension file found in the extracted zip file.");
			sender.sendMessage("Check it is there on GitHub and report this to staff!");
		}
		else if (dimensions.listFiles().length != 1)
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
		File packEntry = new File(packs, key);

		if(forceOverwrite)
		{
			IO.delete(packEntry);
		}

		if(IrisDataManager.loadAnyDimension(key) != null)
		{
			sender.sendMessage("Another dimension in the packs folder is already using the key " + key + " IMPORT FAILED!");
			return;
		}

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
	}

	public KMap<String, String> getListing(boolean cached)
	{
		if(cached && cacheListing != null)
		{
			return cacheListing;
		}

		JSONObject a;

		if(cached)
		{
			a = new JSONObject(Iris.getCached("cachedlisting", LISTING));
		}

		else
		{
			a = new JSONObject(Iris.getNonCached(true + "listing", LISTING));
		}

		KMap<String, String> l = new KMap<>();

		for(String i : a.keySet())
		{
			l.put(i, a.getString(i));
		}

		return l;
	}

	public boolean isProjectOpen()
	{
		return activeProject != null && activeProject.isOpen();
	}

	public void open(MortarSender sender, String dimm)
	{
		try {
			open(sender, dimm, () ->
			{
				if (sender.isPlayer()) {
					sender.player().removePotionEffect(PotionEffectType.BLINDNESS);
				}
			});
		} catch (Exception e){
			sender.player().removePotionEffect(PotionEffectType.BLINDNESS);
			sender.sendMessage("Error when creating studio world:");
			e.printStackTrace();
		}
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
		return Iris.instance.getDataFolderList(WORKSPACE_NAME, sub);
	}

	public File getWorkspaceFile(String... sub)
	{
		return Iris.instance.getDataFileList(WORKSPACE_NAME, sub);
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
		create(sender, s, "example");
	}

	public void updateWorkspace()
	{
		if(isProjectOpen())
		{
			activeProject.updateWorkspace();
		}
	}
}