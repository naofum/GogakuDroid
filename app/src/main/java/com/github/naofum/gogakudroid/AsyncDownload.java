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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Xml;
import android.widget.TextView;
import android.widget.Toast;

import com.github.naofum.gogakudroid.ShellUtils.ShellCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Semaphore;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AsyncDownload extends AsyncTask<String, Integer, String> {

	public Activity owner;
	public String lastKouza;
	public String lastHdate;
	public String lastMessage;
	public String lastLog;
	public boolean isSkip;
	public boolean isWakeLock;
	private String receiveStr;
	protected FfmpegController fc;
	protected ProgressDialog progressDialog;

	protected static String TAG = AsyncDownload.class.getSimpleName();
	protected static String AKAMAI = "https://nhks-vh.akamaihd.net/i/gogaku-stream/mp4/";
//	protected static String type = "3gp";
	private PowerManager.WakeLock mWakeLock;

	private long duration;
	private int perc;
	private int currentkoza;

	private static int available = 0;

	public AsyncDownload(Activity activity) {
		owner = activity;

	}

	@Override
	protected void onPreExecute() {
        super.onPreExecute();
	    Log.d(TAG, "onPreExecute");

	    // take CPU lock to prevent CPU from going off if the user 
        // presses the power button during download
		SharedPreferences sharedPref = 
		        PreferenceManager.getDefaultSharedPreferences(owner);
		isWakeLock = sharedPref.getBoolean("wake_lock", true);
        PowerManager pm = (PowerManager) owner.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
             getClass().getName());
        if (isWakeLock) {
            mWakeLock.acquire();
        }
		available = 0;

        lastMessage = "";

        progressDialog = new ProgressDialog(owner);
	    progressDialog.setMessage(owner.getString(R.string.downloading));
	    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	    progressDialog.setCancelable(true);
	    progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setProgress(0);
		progressDialog.setSecondaryProgress(0);
	    progressDialog.show();
	}

	  @Override
	protected String doInBackground(String[] koza) {
		File fileTmp = new File("tmp");
		try {
			fc = new FfmpegController(owner, fileTmp);
		} catch (IOException e) {
			e.printStackTrace();
			return owner.getString(R.string.init_error);
		}

		SharedPreferences sharedPref = 
		        PreferenceManager.getDefaultSharedPreferences(owner);
		String type = sharedPref.getString("type", "m4a");
		isSkip = sharedPref.getBoolean("skip_file", true);

//		progressDialog.setMax(koza.length);
		String url = null;
        for (int i = 0; i < koza.length; i++) {
			currentkoza = 100 * i / koza.length;
        	// file index of this week
        	if (MainActivity.ENGLISH.containsKey(koza[i])) {
        	    url = "https://cgi2.nhk.or.jp/gogaku/st/xml/english/" + koza[i] + "/listdataflv.xml";
        	} else {
				url = "https://www.nhk.or.jp/radioondemand/json/" + koza[i] + "/bangumi_" + koza[i] + "_01.json";
        	}
			try {
				OkHttpClient client = new OkHttpClient();
				Request request = new Request.Builder()
						.url(url)
						.build();
				Response response = client.newCall(request).execute();
				if (!response.isSuccessful()) {
					throw new Exception("");
				} else {
					receiveStr = response.body().string();
				}
			} catch (Exception e) {
				e.printStackTrace();
				return owner.getString(R.string.conn_error);
			}
			Log.d(TAG, "receiveStr: " + receiveStr);

			if (receiveStr.charAt(0) == '<') {
				XmlPullParser xmlPullParser = Xml.newPullParser();
				try {
					xmlPullParser.setInput( new StringReader ( receiveStr ) );
				} catch (XmlPullParserException e) {
					Log.d(TAG, e.toString());
					return owner.getString(R.string.parse_error);
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
								file = AKAMAI + file + ".mp4/master.m3u8";
								nendo = xmlPullParser.getAttributeValue(null, "nendo");
								lastKouza = kouza;
								lastHdate = hdate;
								download(koza[i], kouza, hdate, file, nendo, type);
								publishProgress(perc, currentkoza);
								if (isCancelled()) {
									return owner.getString(R.string.cancelled);
								}
							}
						}
						eventType = xmlPullParser.next();
					}
				} catch (Exception e) {
					Log.d(TAG, e.toString());
					return owner.getString(R.string.parse_error);
				}
			} else {
				try {
					JSONObject obj = new JSONObject(receiveStr);
					JSONObject main = obj.getJSONObject("main");
					JSONArray detail_list = main.getJSONArray("detail_list");
					Log.d(TAG, main.getString("program_name"));
					for (int l = 0; l < detail_list.length(); l++) {
						JSONArray file_list = detail_list.getJSONObject(l).getJSONArray("file_list");
						for (int m = 0; m < file_list.length(); m++) {
							JSONObject file = file_list.getJSONObject(m);
							String file_name = file.getString("file_name");
							String kouza = main.getString("program_name").replaceAll(" ", "_");
							lastKouza = kouza;
							String hdate = file.getString("onair_date");
							lastHdate = hdate;
							download2(koza[i], kouza, hdate, file_name, "", type);
							publishProgress(perc, currentkoza);
							if (isCancelled()) {
								return owner.getString(R.string.cancelled);
							}
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
		return lastMessage;
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
//		super.onProgressUpdate(values[0]);
		progressDialog.setProgress(values[0]);
        progressDialog.setSecondaryProgress(values[1]);
		progressDialog.setMessage(owner.getString(R.string.downloading) + "\n" + lastKouza + "_" + lastHdate);
        try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
        	//
		}
	}
	  
	protected void download(String koza, String kouza, String hdate, String file, String nendo, String type) {
		Log.d(TAG, "download: " + file);
		Clip mediaIn = new Clip(file);
		Clip mediaOut = new Clip(MainActivity.FILES_DIR.getPath() + "/" + kouza + "/" + kouza + "_" + hdate + "." + type);
		File dir = new File(MainActivity.FILES_DIR.getPath() + "/" + kouza);
		dir.mkdirs();
		if (isSkip && isMediaExist(kouza + "_" + hdate)) {
			lastMessage = owner.getString(R.string.skipped);
			return;
		}
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

	protected void download2(String koza, String kouza, String hdate, String file, String nendo, String type) {
		Log.d(TAG, "download2: " + file);

		while (available == 1) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Log.e(TAG, "InterruptedException");
				e.printStackTrace();
			}
		}

		String lastUrl = "";
		String m3u8base = "";
		String m3u8 = "";
		File workdir = new File(MainActivity.FILES_DIR.getPath());

		try {
			OkHttpClient client = new OkHttpClient().newBuilder()
					.addNetworkInterceptor(new Interceptor() {
						@Override
						public Response intercept(Chain chain) throws IOException {
							return chain.proceed(chain.request());
						}
					})
					.build();
			Request request = new Request.Builder()
					.url(file)
					.build();
			Response response = client.newCall(request).execute();
			if (!response.isSuccessful()) {
				Log.d(TAG, String.format("Get m3u8 file error with code: %d", response.code()));
				return;
			} else {
				lastUrl = response.request().url().toString();
				receiveStr = response.body().string();
			}
		} catch (IOException e) {
			Log.e(TAG, "Get m3u8 file error.");
			e.printStackTrace();
			return;
		}

		m3u8base = lastUrl.substring(0, lastUrl.lastIndexOf("/") + 1);
		{
			String[] lines = receiveStr.split("[\r\n]+");
			for (int i = 0; i < lines.length; i++) {
				if (!lines[i].startsWith("#") && lines[i].length() > 0) {
					m3u8 = m3u8base + lines[i];
				}
			}
		}
		Log.d(TAG, "m3u8base: " + m3u8base);

		try {
			OkHttpClient client = new OkHttpClient();
			Request request = new Request.Builder()
					.url(m3u8)
					.build();
			Response response = client.newCall(request).execute();
			if (!response.isSuccessful()) {
				Log.d(TAG, String.format("Get m3u8 file error with code: %d", response.code()));
				return;
			} else {
				receiveStr = response.body().string();
			}
		} catch (IOException e) {
			Log.e(TAG, "Get m3u8 file error.");
			e.printStackTrace();
			return;
		}

		try {
			File out = new File(workdir + "/index.m3u8");
			FileWriter writer = new FileWriter(out);
			BufferedWriter bw = new BufferedWriter(writer);
			String[] lines = receiveStr.split("[\r\n]+");
			String url = "";
			String file_name = "";
			for (int i = 0; i < lines.length; i++) {
				if (lines[i].startsWith("#EXT-X-KEY")) {
					url = lines[i].substring(lines[i].indexOf('"') + 1, lines[i].length() - 1);
					file_name = url.substring(url.lastIndexOf("/") + 1, url.indexOf("?"));
					bw.write("#EXT-X-KEY:METHOD=AES-128,URI=\"" + file_name + "\"");
					Log.d(TAG, "download url: " + url);
					downloadBinary(url, workdir + "/" + file_name);
				} else if (lines[i].startsWith("#")) {
					bw.write(lines[i]);
				} else {
					url = m3u8base + lines[i];
					file_name = url.substring(url.lastIndexOf("/") + 1, url.indexOf("?"));
					bw.write(file_name);
					Log.d(TAG, "download url: " + url);
					downloadBinary(url, workdir + "/" + file_name);
					int per = i * 160 / lines.length;
					publishProgress(per, 100);
				}
				bw.newLine();
			}
			bw.flush();
			bw.close();
		} catch (IOException e) {
			Log.e(TAG, "Read/Write m3u8 file error.");
			e.printStackTrace();
			return;
		}

		Clip mediaIn = new Clip(workdir + "/index.m3u8");
		Clip mediaOut = new Clip(workdir.getPath() + "/" + kouza + "/" + kouza + "_" + hdate + "." + type);
		File dir = new File(workdir.getPath() + "/" + kouza);
		dir.mkdirs();
		Log.d(TAG, "mediaOut: " + workdir.getPath() + "/" + kouza);
		if (isSkip && isMediaExist(kouza + "_" + hdate)) {
			lastMessage = owner.getString(R.string.skipped);
			return;
		}
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
			Log.e(TAG, "Convert error.");
			e.printStackTrace();
			Log.e(TAG, e.getMessage());
		}

		String[] files = workdir.list();
		for (int i = 0; i < files.length; i++) {
			File afile = new File(workdir.getPath() + "/" + files[i]);
			if (afile.isFile()) {
				afile.delete();
			}
		}

	}

	protected boolean downloadBinary(String url, String file) {
		try {
			OkHttpClient client = new OkHttpClient();
			Request request = new Request.Builder()
					.url(url)
					.build();
			Response response = client.newCall(request).execute();
			if (!response.isSuccessful()) {
				Log.d(TAG, String.format("Download binary file error %s with code: %d", url, response.code()));
				return false;
			}
			try (OutputStream os = new FileOutputStream(file)) {
				os.write(Objects.requireNonNull(response.body()).bytes());
			} catch (IOException e) {
				Log.e(TAG, String.format("Write binary file error. %s", file));
				e.printStackTrace();
				return false;
			}
		} catch (IOException e) {
			Log.e(TAG, String.format("Download binary file error. %s", url));
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	protected void onCancelled() {
//		fc.cancel();
		if (progressDialog != null && progressDialog.isShowing()) {
			try {
	            progressDialog.dismiss();
			} catch (Exception e) {
				//
			}
		}
	}

	@Override
	protected void onPostExecute(String result) {
		if (mWakeLock.isHeld())
			mWakeLock.release();
		((MainActivity) owner).mTask = null;
		if (progressDialog != null && progressDialog.isShowing()) {
			try {
	            progressDialog.dismiss();
			} catch (Exception e) {
				//
			}
		}

		available = 0;

		TextView textView1 = (TextView) owner.findViewById(R.id.textView1);
		textView1.setText(lastMessage);
		Toast.makeText(owner, lastMessage, Toast.LENGTH_LONG).show();
	}

	public class CommonShellCallBack implements ShellCallback {
		@Override
		public void shellOut(String msg) {
			Log.d(TAG, msg);
			if (msg.length() > 0) {
				if (lastLog != null && lastLog.startsWith("  Duration:") && msg.length() > 8 && msg.substring(0, 3).equals("00:")) {
					SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
					try {
						Date date1 = sdf.parse(msg.substring(0, 8));
                        Date date2 = sdf.parse("00:00:00");
						duration = date1.getTime() - date2.getTime();
					} catch (java.text.ParseException e) {
						//
					}
				} else  if (msg.indexOf(" time=") > -1) {
					SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
					String time = msg.substring(msg.indexOf(" time=") + 6, msg.indexOf(" time=") + 14);
					try {
						Date date1 = sdf.parse(time);
						Date date2 = sdf.parse("00:00:00");
						long current = date1.getTime() - date2.getTime();
						perc = (int) (100 * current / duration);
						publishProgress(perc, currentkoza);
					} catch (java.text.ParseException e) {
						//
					}
				}
				lastLog = msg;
			}
		}

		@Override
		public void processComplete(int exitValue) {
//			progressDialog.dismiss();
			((MainActivity) owner).mTask = null;
			if (exitValue == 0) {
				lastMessage = owner.getString(R.string.finished);
				Log.i(TAG, lastMessage);
			} else if (exitValue == 255) {
					lastMessage = owner.getString(R.string.cancelled);
					Log.i(TAG, lastMessage);
			} else {
				lastMessage = owner.getString(R.string.failed);
				Log.i(TAG, lastMessage);
			}

			// move files
			File target_dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());
			Log.i(TAG, target_dir.getPath());
			File dir = new File(MainActivity.FILES_DIR.getPath());
			String[] files = dir.list();
			for (int i = 0; i < files.length; i++) {
				Log.d(TAG, "Local dir: " + files[i]);
				File subdir = new File(MainActivity.FILES_DIR.getPath() + "/" + files[i]);
				if (subdir.isDirectory()) {
					String[] sub_files = subdir.list();
					for (int j = 0; j < sub_files.length; j++) {
						File sub_file = new File(MainActivity.FILES_DIR.getPath() + "/" + files[i] + "/" + sub_files[j]);
						Log.d(TAG, "Local file: " + sub_files[j]);
						if (sub_file.isFile()) {
							Log.i(TAG, "Storing file into Media: " + files[i] + "/" + sub_files[j]);
							storeMedia(subdir.getPath() + "/", sub_files[j], files[i]);
						}
					}
				} else if (subdir.isFile()) {
					// still remain temp files at this point
//					storeMedia(dir.getPath() + "/", files[i], "");
//					Log.i(TAG, "from " + dir.getPath() + "/" + files[i] + " to " + target_dir.getPath());
				}
			}
			available = 0;

		}
	}

	private void storeMedia(String inputPath, String inputFile, String outputPath) {
		String download_path = Environment.DIRECTORY_DOWNLOADS.substring(Environment.DIRECTORY_DOWNLOADS.lastIndexOf("/") + 1);
		ContentValues contentValues = new ContentValues();
		contentValues.put(MediaStore.Audio.Media.ALBUM, outputPath);
		contentValues.put(MediaStore.Audio.Media.RELATIVE_PATH, download_path + "/" + outputPath);
		contentValues.put(MediaStore.Audio.Media.TITLE, inputFile);
		contentValues.put(MediaStore.Audio.Media.DISPLAY_NAME, inputFile);
		if (inputFile.endsWith("mp3")) {
			contentValues.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
		} else if (inputFile.endsWith("og3")) {
			contentValues.put(MediaStore.Audio.Media.MIME_TYPE, "audio/og3");
		} else if (inputFile.endsWith("3gp")) {
			contentValues.put(MediaStore.Audio.Media.MIME_TYPE, "audio/3gpp");
		} else if (inputFile.endsWith("3g2")) {
			contentValues.put(MediaStore.Audio.Media.MIME_TYPE, "audio/3gpp2");
		} else {
			contentValues.put(MediaStore.Audio.Media.MIME_TYPE, "audio/aac");
		}

		contentValues.put(MediaStore.Audio.Media.IS_PENDING, 1);
		ContentResolver resolver = owner.getContentResolver();
		Uri collection;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			collection = MediaStore.Files.getContentUri(
					MediaStore.VOLUME_EXTERNAL_PRIMARY);
		} else {
			collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		}
		Uri item = resolver.insert(collection, contentValues);

		try (OutputStream out = owner.getContentResolver().openOutputStream(item)) {
			InputStream in = new FileInputStream(inputPath + inputFile);

			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			in.close();
			in = null;

			// delete the original file
			new File(inputPath + inputFile).delete();
		} catch (IOException e) {
			e.printStackTrace();
		}

		contentValues.clear();
		contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0);
		resolver.update(item, contentValues, null, null);
	}

	private boolean isMediaExist(String inputFile) {
		boolean found = false;
		Uri collection;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			collection = MediaStore.Files.getContentUri(
					MediaStore.VOLUME_EXTERNAL_PRIMARY);
		} else {
			collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		}
		ContentResolver resolver = owner.getContentResolver();
		Cursor cursor = resolver.query(collection,
				new String[]{
						MediaStore.Audio.Media.TITLE
				},
				"title=?",
				new String[] {
						inputFile
				}, null);
		while (cursor.moveToNext()) {
			String title = cursor.getString(0);
			found = true;
		}
		return found;
	}

}
