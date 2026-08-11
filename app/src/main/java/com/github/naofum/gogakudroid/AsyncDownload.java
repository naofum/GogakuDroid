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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Xml;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AsyncDownload {

	public Activity owner;
	public String lastKouza;
	public String lastHdate;
	public String lastMessage;
	public String lastLog;
	public boolean isSkip;
	public boolean isWakeLock;
	private String receiveStr;
	protected FfmpegController fc;

	private static final String TAG = AsyncDownload.class.getSimpleName();
	private static final String AKAMAI = "https://www.nhk.or.jp/radio-api/app/v1/web/ondemand/series";
	private PowerManager.WakeLock mWakeLock;

	private long duration;
	private int perc;
	private int currentkoza;

	private volatile int available = 0;
	private static final OkHttpClient client = new OkHttpClient();
	private int currentItemIndex = -1;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final AtomicBoolean cancelled = new AtomicBoolean(false);

	public AsyncDownload(Activity activity) {
		owner = activity;
	}

	public void execute(String[] koza) {
		onPreExecute();
		executor.execute(() -> {
			String result = doInBackground(koza);
			handler.post(() -> onPostExecute(result));
		});
	}

	public void cancel(boolean mayInterruptIfRunning) {
		cancelled.set(true);
		executor.shutdownNow();
		handler.post(this::onCancelled);
	}

	public boolean isCancelled() {
		return cancelled.get();
	}

	private void publishProgress(Integer... values) {
		handler.post(() -> onProgressUpdate(values));
	}

	private void onPreExecute() {
		Log.d(TAG, "onPreExecute");

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

		// Start indexing after existing items in the download list
		DownloadFragment df = ((MainActivity) owner).getDownloadFragment();
		if (df != null) {
			currentItemIndex = df.getItemCount() - 1;
		} else {
			currentItemIndex = -1;
		}
	}


	private String doInBackground(String[] koza) {
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

		String url = null;
		for (int i = 0; i < koza.length; i++) {
			currentkoza = 100 * i / koza.length;
			if (MainActivity.ENGLISH.containsKey(koza[i])) {
				url = "https://www.nhk.or.jp/gogaku/st/xml/" + koza[i] + "/listdataflv.xml";
			} else {
				url = "https://www.nhk.or.jp/radio-api/app/v1/web/ondemand/series?site_id=" + koza[i] + "&corner_site_id=01";
			}
			try {
				Request request = new Request.Builder()
						.url(url)
						.build();
				Response response = client.newCall(request).execute();
				if (!response.isSuccessful()) {
					response.close();
					throw new Exception("");
				} else {
					receiveStr = response.body().string();
					response.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
				return owner.getString(R.string.conn_error);
			}
			Log.d(TAG, "receiveStr: " + receiveStr);

			if (receiveStr.charAt(0) == '<') {
				XmlPullParser xmlPullParser = Xml.newPullParser();
				try {
					xmlPullParser.setInput(new StringReader(receiveStr));
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
						if (eventType == XmlPullParser.START_TAG) {
							if (xmlPullParser.getName().equals("music")) {
								kouza = xmlPullParser.getAttributeValue(null, "kouza");
								hdate = xmlPullParser.getAttributeValue(null, "hdate");
								file = xmlPullParser.getAttributeValue(null, "file");
								file = AKAMAI + file + "/index.m3u8";
								nendo = xmlPullParser.getAttributeValue(null, "nendo");
								lastKouza = kouza;
								lastHdate = hdate;
								final String itemTitle = kouza + "_" + hdate;
								currentItemIndex++;
								final int idx = currentItemIndex;
								final boolean willSkip = isSkip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
										&& isMediaExist(kouza + "_" + hdate + "." + type);
								handler.post(() -> {
									DownloadFragment df = ((MainActivity) owner).getDownloadFragment();
									if (df != null) {
										DownloadItem item = new DownloadItem(itemTitle);
										df.addItem(item);
										if (willSkip) {
											df.updateItem(idx, 100, DownloadItem.Status.SKIPPED);
										} else {
											df.updateItem(idx, 0, DownloadItem.Status.DOWNLOADING);
										}
									}
								});
								download(koza[i], kouza, hdate, file, nendo, type);
								final String msg = lastMessage;
								if (!willSkip) {
									handler.post(() -> {
										DownloadFragment df = ((MainActivity) owner).getDownloadFragment();
										if (df != null) {
											DownloadItem.Status st = DownloadItem.Status.COMPLETED;
											if (msg != null && msg.equals(owner.getString(R.string.skipped))) {
												st = DownloadItem.Status.SKIPPED;
											} else if (msg != null && msg.equals(owner.getString(R.string.failed))) {
												st = DownloadItem.Status.FAILED;
											}
											df.updateItem(idx, 100, st);
										}
									});
								}
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
					Log.d(TAG, obj.getString("title"));
					JSONArray detail_list = obj.getJSONArray("episodes");
					for (int l = 0; l < detail_list.length(); l++) {
						JSONObject file = detail_list.getJSONObject(l);
						String file_name = file.getString("stream_url");
						String kouza = obj.getString("title").replaceAll(" ", "_");
						lastKouza = kouza;
						String hdate = file.getString("onair_date");
						lastHdate = hdate;
						final String itemTitle = kouza + "_" + hdate;
						currentItemIndex++;
						final int idx = currentItemIndex;
						final boolean willSkip = isSkip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
								&& isMediaExist(kouza + "_" + hdate + "." + type);
						handler.post(() -> {
							DownloadFragment df = ((MainActivity) owner).getDownloadFragment();
							if (df != null) {
								DownloadItem item = new DownloadItem(itemTitle);
								df.addItem(item);
								if (willSkip) {
									df.updateItem(idx, 100, DownloadItem.Status.SKIPPED);
								} else {
									df.updateItem(idx, 0, DownloadItem.Status.DOWNLOADING);
								}
							}
						});
						download2(koza[i], kouza, hdate, file_name, "", type);
						final String msg = lastMessage;
						if (!willSkip) {
							handler.post(() -> {
								DownloadFragment df = ((MainActivity) owner).getDownloadFragment();
								if (df != null) {
									DownloadItem.Status st = DownloadItem.Status.COMPLETED;
									if (msg != null && msg.equals(owner.getString(R.string.skipped))) {
										st = DownloadItem.Status.SKIPPED;
									} else if (msg != null && msg.equals(owner.getString(R.string.failed))) {
										st = DownloadItem.Status.FAILED;
									}
									df.updateItem(idx, 100, st);
								}
							});
						}
						if (isCancelled()) {
							return owner.getString(R.string.cancelled);
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
		return lastMessage;
	}


	private void onProgressUpdate(Integer... values) {
		// Update download fragment progress
		if (currentItemIndex >= 0) {
			DownloadFragment df = ((MainActivity) owner).getDownloadFragment();
			if (df != null) {
				df.updateItem(currentItemIndex, values[0], DownloadItem.Status.DOWNLOADING);
			}
		}
	}

	private void onCancelled() {
	}

	private void onPostExecute(String result) {
		if (mWakeLock != null && mWakeLock.isHeld())
			mWakeLock.release();
		((MainActivity) owner).mTask = null;

		available = 0;

		Toast.makeText(owner, lastMessage, Toast.LENGTH_LONG).show();

		// Update status text on CoursesFragment
		CoursesFragment cf = ((MainActivity) owner).getCoursesFragment();
		if (cf != null) {
			cf.setStatusText(owner.getString(R.string.finished));
		}
	}


	protected void download(String koza, String kouza, String hdate, String file, String nendo, String type) {
		Log.d(TAG, "download: " + file);
		Clip mediaIn = new Clip(file);
		Clip mediaOut = new Clip(MainActivity.FILES_DIR.getPath() + "/" + kouza + "/" + kouza + "_" + hdate + "." + type);
		File dir = new File(MainActivity.FILES_DIR.getPath() + "/" + kouza);
		dir.mkdirs();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			if (isSkip && isMediaExist(kouza + "_" + hdate + "." + type)) {
				lastMessage = owner.getString(R.string.skipped);
				return;
			}
		} else {
			if (isSkip && isFileExist(kouza + "_" + hdate + "." + type)) {
				lastMessage = owner.getString(R.string.skipped);
				return;
			}
		}
		try {
			if (type.equals("3g2")) {
				mediaOut.audioCodec = "copy";
				fc.convertTo3GPAudio(mediaIn, mediaOut, new CommonShellCallBack());
			} else if (type.equals("3gp")) {
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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			if (isSkip && isMediaExist(kouza + "_" + hdate + "." + type)) {
				lastMessage = owner.getString(R.string.skipped);
				return;
			}
		} else {
			if (isSkip && isFileExist(kouza + "/" + kouza + "_" + hdate + "." + type)) {
				lastMessage = owner.getString(R.string.skipped);
				return;
			}
		}

		try {
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
				response.body().close();
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
			Request request = new Request.Builder()
					.url(m3u8)
					.build();
			Response response = client.newCall(request).execute();
			if (!response.isSuccessful()) {
				Log.d(TAG, String.format("Get m3u8 file error with code: %d", response.code()));
				return;
			} else {
				receiveStr = response.body().string();
				response.body().close();
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

		try {
			if (type.equals("3g2")) {
				mediaOut.audioCodec = "copy";
				fc.convertTo3GPAudio(mediaIn, mediaOut, new CommonShellCallBack());
			} else if (type.equals("3gp")) {
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
				response.body().close();
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
				} else if (msg.indexOf(" time=") > -1) {
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
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								Log.d(TAG, "Storing file into Media: " + files[i] + "/" + sub_files[j]);
								storeMedia(subdir.getPath() + "/", sub_files[j], files[i]);
							} else {
								Log.d(TAG, "Moving file into: " + target_dir.getPath() + "/" + files[i] + "/" + sub_files[j]);
								moveFile(subdir.getPath() + "/", sub_files[j], target_dir.getPath() + "/" + files[i] + "/");
							}
						}
					}
				}
			}
			available = 0;
		}
	}


	private void storeMedia(String inputPath, String inputFile, String outputPath) {
		String download_path = Environment.DIRECTORY_DOWNLOADS.substring(Environment.DIRECTORY_DOWNLOADS.lastIndexOf("/") + 1);
		ContentValues contentValues = new ContentValues();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			contentValues.put(MediaStore.Downloads.RELATIVE_PATH, download_path + "/" + outputPath);
		}
		contentValues.put(MediaStore.Downloads.DISPLAY_NAME, inputFile);
		if (inputFile.endsWith("mp3")) {
			contentValues.put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg");
		} else if (inputFile.endsWith("og3")) {
			contentValues.put(MediaStore.Downloads.MIME_TYPE, "audio/og3");
		} else if (inputFile.endsWith("3gp")) {
			contentValues.put(MediaStore.Downloads.MIME_TYPE, "audio/3gpp");
		} else if (inputFile.endsWith("3g2")) {
			contentValues.put(MediaStore.Downloads.MIME_TYPE, "audio/3gpp2");
		} else if (inputFile.endsWith("m4a")) {
			contentValues.put(MediaStore.Downloads.MIME_TYPE, "audio/x-m4a");
		} else {
			contentValues.put(MediaStore.Downloads.MIME_TYPE, "audio/aac");
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			contentValues.put(MediaStore.Downloads.IS_PENDING, 1);
		}
		ContentResolver resolver = owner.getContentResolver();
		Uri collection;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			collection = MediaStore.Downloads.getContentUri(
					MediaStore.VOLUME_EXTERNAL_PRIMARY);
		} else {
			collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			contentValues.put(MediaStore.Audio.Media.DATA, new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), inputPath).getAbsolutePath() + "/" + outputPath);
		}
		Uri item = resolver.insert(collection, contentValues);

		try {
			assert item != null;
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
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		contentValues.clear();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			contentValues.put(MediaStore.Downloads.IS_PENDING, 0);
			resolver.update(item, contentValues, null, null);
		}
		markMediaDownloaded(inputFile);

		// Save content URI for existence check and playback
		if (item != null) {
			owner.getSharedPreferences("downloaded_uris", Context.MODE_PRIVATE)
					.edit().putString(inputFile, item.toString()).apply();
		}

		// Set content URI for playback
		final Uri mediaUri = item;
		final int idx = currentItemIndex;
		handler.post(() -> {
			DownloadFragment df = ((MainActivity) owner).getDownloadFragment();
			if (df != null) {
				df.setItemUri(idx, mediaUri);
			}
		});
	}

	private static final int MAX_DOWNLOAD_HISTORY = 500;

	private boolean isMediaExist(String inputFile) {
		Log.d("GogakuDroid", "Search: " + inputFile);
		// Debug: dump all accessible audio media information
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			Uri audioUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
			String[] projection = {
					MediaStore.Audio.Media._ID,
					MediaStore.Audio.Media.TITLE,
					MediaStore.Audio.Media.DISPLAY_NAME,
					MediaStore.Audio.Media.RELATIVE_PATH,
					MediaStore.Audio.Media.MIME_TYPE,
					MediaStore.Audio.Media.SIZE,
					MediaStore.Audio.Media.DURATION,
					MediaStore.Audio.Media.DATE_ADDED,
					MediaStore.Audio.Media.DATE_MODIFIED,
					MediaStore.Audio.Media.ARTIST,
					MediaStore.Audio.Media.ALBUM
			};
			try (android.database.Cursor cursor = owner.getContentResolver().query(
					audioUri, projection, null, null, null)) {
				if (cursor != null) {
					Log.d("GogakuDroid", "=== Audio Media List (total: " + cursor.getCount() + ") ===");
					while (cursor.moveToNext()) {
						StringBuilder sb = new StringBuilder();
						for (int i = 0; i < cursor.getColumnCount(); i++) {
							if (i > 0) sb.append(", ");
							sb.append(cursor.getColumnName(i)).append("=").append(cursor.getString(i));
						}
						Log.d("GogakuDroid", sb.toString());
					}
					Log.d("GogakuDroid", "=== End Audio Media List ===");
				}
			} catch (Exception e) {
				Log.e("GogakuDroid", "Failed to query audio media", e);
			}
		}

		SharedPreferences pref = owner.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
		if (!pref.contains(inputFile)) {
			return false;
		}
		// Verify the file still exists
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			// Search by TITLE (filename without extension)
			String title = inputFile.contains(".") ? inputFile.substring(0, inputFile.lastIndexOf(".")) : inputFile;
			String display_name = inputFile.replace(":", "_");
			Log.d("GogakuDroid", "Search display_name: " + display_name);
			Uri audioUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
			try (android.database.Cursor cursor = owner.getContentResolver().query(
					audioUri,
					new String[]{ MediaStore.Audio.Media._ID },
					MediaStore.Audio.Media.DISPLAY_NAME + " = ?",
					new String[]{ display_name },
					null)) {
				if (cursor != null && cursor.moveToFirst()) {
					Log.d("GogakuDroid", "Search file still exist: " + inputFile);
					return true;
				}
			} catch (Exception e) {
				Log.e("GogakuDroid", "Failed to query audio by title: " + title, e);
			}
		} else {
			File file = new File(Environment.getExternalStoragePublicDirectory(
					Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + inputFile);
			if (file.exists()) {
				return true;
			}
		}
		// File no longer exists, remove stale record
		pref.edit().remove(inputFile).apply();
		owner.getSharedPreferences("downloaded_uris", Context.MODE_PRIVATE)
				.edit().remove(inputFile).apply();
		return false;
	}

	private void markMediaDownloaded(String inputFile) {
		SharedPreferences pref = owner.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = pref.edit();
		editor.putLong(inputFile, System.currentTimeMillis());

		// Evict oldest entries if over limit
		Map<String, ?> all = pref.getAll();
		if (all.size() >= MAX_DOWNLOAD_HISTORY) {
			List<Map.Entry<String, ?>> entries = new ArrayList<>(all.entrySet());
			entries.sort((a, b) -> Long.compare(
					(Long) a.getValue(), (Long) b.getValue()));
			int removeCount = entries.size() - MAX_DOWNLOAD_HISTORY + 1;
			for (int i = 0; i < removeCount; i++) {
				editor.remove(entries.get(i).getKey());
			}
		}
		editor.apply();
	}

	private boolean isFileExist(String inputFile) {
		File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + inputFile);
		Log.d(TAG, "Check file: " + file.getPath());
		Log.d(TAG, "file exist: " + file.exists());
		return file.exists();
	}

	private void moveFile(String inputPath, String inputFile, String outputPath) {
		Log.d(TAG, "Moving file from:" + inputPath + inputFile + " into: " + outputPath);

		InputStream in = null;
		OutputStream out = null;
		try {
			//create output directory if it doesn't exist
			File dir = new File(outputPath);
			if (!dir.exists()) {
				dir.mkdirs();
			}

			in = new FileInputStream(inputPath + inputFile);
			out = new FileOutputStream(outputPath + inputFile);

			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			in.close();
			in = null;

			// write the output file
			out.flush();
			out.close();
			out = null;

			// delete the original file
			new File(inputPath + inputFile).delete();
			markMediaDownloaded(inputFile);

			// Set file URI for playback
			final Uri fileUri = Uri.fromFile(new File(outputPath + inputFile));
			final int idx = currentItemIndex;
			handler.post(() -> {
				DownloadFragment df = ((MainActivity) owner).getDownloadFragment();
				if (df != null) {
					df.setItemUri(idx, fileUri);
				}
			});

		} catch (FileNotFoundException fnfe1) {
			Log.e("tag", fnfe1.getMessage());
		} catch (Exception e) {
			Log.e("tag", e.getMessage());
		}
	}
}
