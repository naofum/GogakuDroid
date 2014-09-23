/*
 * Copyright (C) 2014 Naofumi Fukue
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.naofum.gogakudroid;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Xml;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.*;
import org.apache.http.util.*;
import org.apache.http.impl.client.*;
import org.apache.http.client.methods.*;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.github.naofum.gogakudroid.R;
import com.github.naofum.gogakudroid.ShellUtils.ShellCallback;

public class AsyncDownload extends AsyncTask<String, Void, String> {

	public Activity owner;
	public String lastKouza;
	public String lastMessage;
	private String receiveStr;
	protected FfmpegController fc;
	protected ProgressDialog progressDialog;

	protected static String TAG = AsyncDownload.class.getSimpleName();
	protected static String AKAMAI = "https://nhk-vh.akamaihd.net/i/gogaku-stream/mp4/";
//	protected static String type = "3gp";

	public AsyncDownload(Activity activity) {
		owner = activity;

//		progressDialog = new ProgressDialog(owner);
//		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//		progressDialog.setMessage("処理を実行しています");
//		progressDialog.setCancelable(true);
//		progressDialog.show();

	}

	@Override
	protected String doInBackground(String[] koza) {
		File fileTmp = new File("tmp");
		try {
			fc = new FfmpegController(owner, fileTmp);
		} catch (IOException e) {
			e.printStackTrace();
			return "1";
		}

		SharedPreferences sharedPref = 
		        PreferenceManager.getDefaultSharedPreferences(owner);
		String type = sharedPref.getString("type", "mp3");

		String url = null;
        for (int i = 0; i < koza.length; i++) {
        	// file index of this week
        	if (MainActivity.ENGLISH.containsKey(koza[i])) {
        	    url = "http://cgi2.nhk.or.jp/gogaku/st/xml/english/" + koza[i] + "/listdataflv.xml";
        	} else if (koza[i].contains("levelup_")) {
        		url = "http://cgi2.nhk.or.jp/gogaku/st/xml/" + koza[i].substring("levelup_".length()) + "/levelup/listdataflv.xml";
        	} else {
        		url = "http://cgi2.nhk.or.jp/gogaku/st/xml/" + koza[i] + "/kouza/listdataflv.xml";
        	}
			try {
				HttpGet httpGet = new HttpGet(url);
				DefaultHttpClient httpClient = new DefaultHttpClient();
				httpGet.setHeader("Connection", "Keep-Alive");
				HttpResponse response = httpClient.execute(httpGet);
				int status = response.getStatusLine().getStatusCode();
				if (status != HttpStatus.SC_OK) {
					throw new Exception("");
				} else {
					receiveStr = EntityUtils
							.toString(response.getEntity(), "UTF-8");
				}
			} catch (Exception e) {
				e.printStackTrace();
				return "1";
			}

			XmlPullParser xmlPullParser = Xml.newPullParser();
			try {
			    xmlPullParser.setInput( new StringReader ( receiveStr ) );
			} catch (XmlPullParserException e) {
			    Log.d(TAG, e.toString());
				return "1";
			}
			try {
				String kouza;
				String hdate;
				String file;
				String nendo;
			    int eventType;
			    eventType = xmlPullParser.getEventType();
			    while (eventType != XmlPullParser.END_DOCUMENT) {
			        if(eventType == XmlPullParser.START_TAG) {
			        	if (xmlPullParser.getName().equals("music")) {
			        		kouza = xmlPullParser.getAttributeValue(null, "kouza");
			        		hdate = xmlPullParser.getAttributeValue(null, "hdate");
			        		file = xmlPullParser.getAttributeValue(null, "file");
			        		nendo = xmlPullParser.getAttributeValue(null, "nendo");
			        		lastKouza = kouza;
			        		download(koza[i], kouza, hdate, file, nendo, type);
			        	}
			        }
			        eventType = xmlPullParser.next();
			    }
			} catch (Exception e) {
			    Log.d("XmlPullParserSample", "Error");
				return "1";
			}
		}
		return "0";
	}

	protected void download(String koza, String kouza, String hdate, String file, String nendo, String type) {
		Clip mediaIn = new Clip(AKAMAI + file + "/master.m3u8");
		Clip mediaOut = new Clip(Environment.getExternalStorageDirectory()
				.getPath() + "/Download/" + kouza + "/" + kouza + "_" + hdate + "." + type);
		File dir = new File(Environment.getExternalStorageDirectory().getPath() + "/Download/" + kouza);
		dir.mkdir();
		try {
			if (type.equals("3g2")) {
				mediaOut.audioCodec = "copy";
				fc.convertTo3GPAudio(mediaIn, mediaOut, new CommonShellCallBack());
			} else if (type.equals("3gp")) {
//				mediaOut.audioCodec = "libopencore_amrnb";
				mediaOut.audioCodec = "copy";
				fc.convertTo3GPAudio(mediaIn, mediaOut, new CommonShellCallBack());
			} else if (type.equals("aac")) {
				mediaOut.audioCodec = "copy";
				fc.convertToAACAudio(mediaIn, mediaOut, new CommonShellCallBack());
			} else if (type.equals("avi")) {
				mediaOut.audioCodec = "copy";
				fc.convertToAVIAudio(mediaIn, mediaOut, kouza + "_" + hdate, nendo, new CommonShellCallBack());
			} else if (type.equals("m4a")) {
				mediaOut.audioCodec = "copy";
				fc.convertToMOVAudio(mediaIn, mediaOut, kouza + "_" + hdate, nendo, new CommonShellCallBack());
			} else if (type.equals("mka")) {
				mediaOut.audioCodec = "copy";
				fc.convertToAVIAudio(mediaIn, mediaOut, kouza + "_" + hdate, nendo, new CommonShellCallBack());
			} else if (type.equals("mkv")) {
				mediaOut.audioCodec = "copy";
				fc.convertToAVIAudio(mediaIn, mediaOut, kouza + "_" + hdate, nendo, new CommonShellCallBack());
			} else if (type.equals("mov")) {
				mediaOut.audioCodec = "copy";
				fc.convertToMOVAudio(mediaIn, mediaOut, kouza + "_" + hdate, nendo, new CommonShellCallBack());
			} else if (type.equals("mp3")) {
				mediaOut.audioCodec = "libmp3lame";
				fc.convertToAVIAudio(mediaIn, mediaOut, kouza + "_" + hdate, nendo, new CommonShellCallBack());
			} else if (type.equals("ts")) {
				mediaOut.audioCodec = "copy";
				fc.convertToAACAudio(mediaIn, mediaOut, new CommonShellCallBack());
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	protected void onPostExecute(String result) {
		TextView textView1 = (TextView) owner.findViewById(R.id.textView1);
		textView1.setText(lastMessage);
	}

	public class CommonShellCallBack implements ShellCallback {
		@Override
		public void shellOut(String msg) {
			Log.d(TAG, msg);
		}

		@Override
		public void processComplete(int exitValue) {
//			progressDialog.dismiss();
			if (exitValue == 0) {
				lastMessage = lastKouza + "をダウンロードしました";
				Log.i(TAG, lastKouza + " Download completed");
			} else {
				lastMessage = lastKouza + "をダウンロードできませんでした";
				Log.i(TAG, lastKouza + " Download failed");
			}
			Toast.makeText(owner, lastMessage, Toast.LENGTH_LONG).show();
		}
	}
}
