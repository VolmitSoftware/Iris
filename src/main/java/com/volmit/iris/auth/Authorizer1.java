package com.volmit.iris.auth;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.bukkit.Bukkit;

import com.volmit.iris.Iris;
import com.volmit.iris.util.CustomOutputStream;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.J;
import com.volmit.iris.util.RNG;

public class Authorizer1
{
	public static void validate()
	{
		J.a(() ->
		{
			try
			{
				String key = "343D9040A671C45832EE5381860E2996";
				StringBuilder hashlist = new StringBuilder();
				hashlist.append(Bukkit.getServer().getIp());
				URL website = new URL("https://checkip.amazonaws.com/");
				URLConnection connection = website.openConnection();
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder response = new StringBuilder();
				String inputLine;

				while((inputLine = in.readLine()) != null)
				{
					response.append(inputLine);
				}

				in.close();
				hashlist.append(key);
				hashlist.append(response.toString());
				Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

				while(networkInterfaces.hasMoreElements())
				{
					NetworkInterface ni = networkInterfaces.nextElement();
					byte[] hardwareAddress = ni.getHardwareAddress();
					if(hardwareAddress != null)
					{
						String[] hexadecimalFormat = new String[hardwareAddress.length];

						for(int i = 0; i < hardwareAddress.length; i++)
						{
							hexadecimalFormat[i] = String.format("%02X", hardwareAddress[i]);
						}

						hashlist.append(String.join("-", hexadecimalFormat));
						hashlist.append(ni.getDisplayName());
						hashlist.append(ni.getMTU() + "");
						hashlist.append(ni.getName());
						hashlist.append(Runtime.getRuntime().availableProcessors());

					}
				}

				hashlist.append(System.getProperty("os.name"));
				hashlist.append(Inet4Address.getLocalHost().getHostName());

				for(File i : File.listRoots())
				{
					hashlist.append(i.getAbsolutePath() + ";");
				}

				hashlist.append(key);
				hashlist.append(System.getProperty("java.home") + "");
				hashlist.append(System.getProperty("java.library.path") + "");
				hashlist.append(System.getProperty("java.class.path") + "");
				hashlist.append(System.getProperty("java.ext.dirs") + "");
				hashlist.append(System.getProperty("java.version") + "");
				hashlist.append(System.getProperty("java.runtime.version") + "");
				hashlist.append(System.getProperty("user.name") + "");
				hashlist.append(System.getProperty("user.home") + "");
				hashlist.append(System.getProperty("user.dir") + "");
				hashlist.append(System.getProperty("os.arch") + "");
				hashlist.append(Iris.instance.getDescription().getAPIVersion());
				hashlist.append(Iris.instance.getDescription().getVersion());
				hashlist.append(Iris.instance.getDescription().getAuthors().hashCode());
				hashlist.append(Iris.instance.getDescription().getDescription());
				hashlist.append(Iris.instance.getDescription().getFullName());
				hashlist.append(Iris.instance.getDescription().getMain());
				String h = IO.hash("fhhj + fdf" + IO.hash(IO.hash(key)) + IO.hash(key) + key + IO.hash(IO.hash(IO.hash(hashlist.toString()) + "dirisf")));
				SecureRandom s = new SecureRandom(h.getBytes());
				KeyGenerator keyGen = KeyGenerator.getInstance("AES");
				keyGen.init(256, s);
				SecretKey secretKey = keyGen.generateKey();
				Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
				cipher.init(Cipher.ENCRYPT_MODE, secretKey);
				RNG rngx = new RNG("eaf3afa271d59f60afb077b855dbe25797410c189f68e4b99a6cd9253f27cf0c").nextParallelRNG(key.hashCode());
				char[] cbit = h.toCharArray();
				char[] kbit = key.toCharArray();
				ByteArrayOutputStream boas = new ByteArrayOutputStream();
				CipherOutputStream cos = new CipherOutputStream(boas, cipher);
				GZIPOutputStream gos = new CustomOutputStream(cos, 9);
				DataOutputStream dos = new DataOutputStream(gos);

				for(int i = 0; i < cbit.length; i++)
				{
					rngx = rngx.nextParallelRNG(new RNG((i * 489) + cbit[i] + "077b855dbe25797410c189f" + rngx.nextParallelRNG(496 - i).s(1024)).nextInt());
					dos.writeUTF(i + rngx.s(12 + i));
					rngx = rngx.nextParallelRNG(new RNG((i * 499) + cbit[i] + "f68e4b99a6cd9253f27cf01" + rngx.nextParallelRNG(496 - i).s(1024)).nextInt());
					dos.writeUTF(i + rngx.s(7));
				}

				for(int i = 0; i < kbit.length; i++)
				{
					rngx = rngx.nextParallelRNG(new RNG((i * 129) + kbit[i] + "410c189" + rngx.nextParallelRNG(416 - i).s(1024)).nextInt());
					dos.writeUTF(i + rngx.s(3 + i));
					rngx = rngx.nextParallelRNG(new RNG((i * 4229) + kbit[i] + "68e4b9" + rngx.nextParallelRNG(456 - i).s(1024)).nextInt());
					dos.writeUTF(i + rngx.s(9));
				}

				dos.flush();
				dos.close();
				gos.close();
				cos.close();
				boas.close();
				byte[] raw = boas.toByteArray();
				String code = IO.bytesToHex(raw);
				AuthMemory.meta.put("*", code);
				URL a = new URL("https://raw.githubusercontent.com/VolmitSoftware/iauth/master/auth");
				connection = a.openConnection();
				in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				response = new StringBuilder();

				while((inputLine = in.readLine()) != null)
				{
					response.append(inputLine);
				}

				in.close();
				String acode = response.toString();

				if(acode.contains(code))
				{
					// AUTHORIZED
				}

				else
				{
					// DEAUTHORIZE
				}
			}

			catch(Throwable e)
			{
				// DEAUTHORIZE
			}
		});
	}
}
