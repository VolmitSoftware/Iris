package com.volmit.iris.activation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicMulticache;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.Info;
import com.volmit.iris.util.J;
import com.volmit.iris.util.JSONException;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.M;

public class IrisActivation2
{
	private static String getBakedCode()
	{
		return "Q8UZz5!&%rCK$Fn=eXmYQDBmC-MDS!yQVQ#?zE&BUq83yx@y?AHwE94=nakvy%-?M4KXrd9V2&PBm7@&NzG6yV*2&F29dAwqxu24F5X$2A9y4akpxSp3hYFnuJM8TdA?@hPhz5W6PHjFGHsg^GefHs7-TQLVGv#LunSs4SNRAM@qJB$t!ySkFBez*!T6tb%y9WJKkru-=UK_w8FcSdjPgm_weZFYvZ?2cXWW&u9tEa6c-QLGeW4Kzx$xP^MF^3^^nHGW7A#rKk4Z=u24^^nEhQRTFP-83WGW9HRkYAGMGua$bhtP!$h#2emPg9AMgD*Z+A9ZRw67tA?pnh5LtZKy9^jke?kcxfR_AtAVLG5pJewY2H&%T^eyV=YxF-5&h6QUhKXGEDFEYm?$#W_nTZ_xX4!fuVPx+T$uM2e7k!LH2-YaV=Pu4^zrP9MBzmw$D+TvM6bG-mFK3AQFYrR^SZ9E4kb*9V4HRGbDP#szpr6%xqJ6SD#8rg_&j*MEZUQ8@&&H#6$9Zsv*rMkST2@f%&P=2Yt?*Eg83@9uX_aP$DhWeGmU4WmB4N6*nmbFDLqGA#Sn2b#DCpU=q#FP*ZeRLqVQbQ@tNjWd4^bwfhY34gyjGQnsbT@vHedH^ZFqD!G=Yx-&&+CgDA*a=gxXQ+_Z@b_$hs3sVH+nxtH?Qt85SY2=H5-gu+KhhYm*d=dD6x-xCCqFG8G3U@eeU%6=#VNEkq5gTVNV5_3Vad4#4gMpphtc6A*xLj4RS!dq-P-=2Umcy?xqR@GK_!kNUCZztsc7Q+Unc_2L+ybTea+JY=WqNXUw!MxXt26PuRBXEt3h&-RQfTCDYc3?M^gNFDk*m9mfdR!na4A*AESRX*Qwsw==Vs_#25!W=s?Rs5rw*x3wk3vfgL9v6uzUa%Q=9E*u!AK%=hmsz9yty^Zs9YjCAp_pFX4bsL%f_zs@rWdRQjmurrZ_ttR@3fHk?FB-DVPxmKMY%MmkDswG6&7F%SPB%!^meVkqp2pYWkQCdb=pk56MD&^uztgJUza&nQq^3+KzFJP-HQU+he2&fuFCv$#6r+R3+c^d^U@%T?ucp6nG#ma9%zNVKPr_FqG@x-xK?gsfNrhLm#f!$a8GZjzSqD=Bps9p#!mX8&8kF8p79U4fzgJRFb$QEfreNsdf$$XjyDsRwCKk7nX@HrP+muZfrDwq-^Tw?Ge*DJ*n7&zWadJRW3MHk7Zn?=?6X9V$*SCR+XQ-%q@86=bp*g-bGGD&Y3e7bv+?-v=FfkGa3yL7Vst5w%&6_2WHUE4?J8ZSadYCk6FPSETnz6L3jWwJ4*tc&VE+EYy6t5vLe*sJCr*8Lk?UJjnb3w#&avU@@&+L8+MnzyN5YFcUX@VF=mk6mZcX+dhSbjv@_A@yZKwB_Z4Vya9gWgJ^Q2f_p-U2FyGF5$uF$WMe$u*U6Q8Q@&CedwJR-ynFuQ2DVkRD%yy$jQp#47J%@7H+aUZcvFbqxQgUA3S*t6Pger=Y=Z6n%Vf6yp*W+ST79-E8^@JCx_=H#Da$9QjM9sss#R&9c8pL8*NCkYk7gSN24%65bMyGb7G7T5$7n&nLhn7&wxwmPZpw^-C&g__5?YMrHQ-n@7EBJww*?%dbej-^5&y7CctU7PVHbVKMHQTkC4F$k%HPSYgb_6*Fk*DHq7taNay!dr-GX#XA=#LP3@Q6ZZuNayeChK&r%fFtuUvBgkch*7yuhHJR7hJ?Jx+33?SRU7WzFbRxkPf+fm%jNXm*jghJn@y4K2j*Gq7CYR_TC#VzDHNNRPSWf&B7+-TB7?#h@fFW9_uJ_!FbDtHD$dB7+&j=3DCaqN7NwGhwsfR3Z-?dhkURubrdD9u^!!RVY?YdUjQd%c-&mNuCJ9JNq+6$SDG+749P-v-aAP%7THf&VjpTcYeHV=scbtv2G+XSFSX#?TJ%6&7Vc&MyxcL$mjsBRvvX5aBvQZHTdp&ba&cWPmp?Lqm5VsWzyx%+Fhw+gYnnYVQUtyP$g3psWCEdQD6-Yd4p5@6-E^&CZ$t23y=KL2Ntq?M$s-EBxFdWBFKm9D*&f*DB=EgB";
	}

	public static long getTime()
	{
		return 1598623810000L;
	}

	public static void validate()
	{if(true)
	{
		return;
	}
		IrisActivation3.validate();
		J.attemptAsync(() ->
		{
			try
			{
				if(isTimeValid() && authorize(new JSONObject(IO.readAll(Iris.instance.getDataFile("settings.json"))).getString("activationCode")) && isTimeValid())
				{
					return;
				}
			}

			catch(JSONException | IOException e)
			{

			}

			AtomicMulticache.broken = true;
		});
	}

	public static boolean isTimeValid()
	{
		return M.ms() < getTime();
	}

	public static String computeSecurityHash()
	{
		URL url;
		InputStream is = null;
		BufferedReader br;
		String line;
		String ip = null;

		try
		{
			url = new URL("http://checkip.amazonaws.com/");
			is = url.openStream();
			br = new BufferedReader(new InputStreamReader(is));

			while((line = br.readLine()) != null)
			{
				if(ip == null)
				{
					ip = line.trim();
				}
			}
		}

		catch(MalformedURLException mue)
		{

		}

		catch(IOException ioe)
		{

		}

		finally
		{
			try
			{
				if(is != null)
				{
					is.close();
				}
			}

			catch(IOException ioe)
			{

			}
		}

		return IO.hash(Info.getPortIP() + ip + "c" + Runtime.getRuntime().availableProcessors());
	}

	public static boolean authorize(String auth)
	{
		try
		{
			if(auth.length() == "a3a7557488e5790598ef04830bcf2cfe630a918bff5226b144946154ea37b068".length() && isTimeValid() && auth.toUpperCase().equals(IO.hash(IO.hash(getBakedCode()).toLowerCase()).toUpperCase()))
			{
				String aa = auth;
				String authrequest = IO.hash(aa + computeSecurityHash());
				String g = Iris.getNonCached("h", "https://raw.githubusercontent.com/VolmitSoftware/iauth/master/auth");

				if(g.toUpperCase().contains(authrequest.toUpperCase()))
				{
					return true;
				}
			}
		}

		catch(Throwable e)
		{

		}

		return false;
	}
}
