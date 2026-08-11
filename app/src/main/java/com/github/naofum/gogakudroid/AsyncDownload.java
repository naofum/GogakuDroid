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
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.github.naofum.gogakudroid.ShellUtils.ShellCallback;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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
	private MainViewModel viewModel;
	private NhkRepository nhkRepository;
	private MediaRepository mediaRepository;

	public AsyncDownload(Activity activity) {
		owner = activity;
		viewModel = ((MainActivity) activity).getViewModel();
		nhkRepository = new NhkRepository(client);
		mediaRepository = new MediaRepository(activity);
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
		if (currentItemIndex >= 0) {
			viewModel.postUpdateItem(currentItemIndex, values[0], DownloadItem.Status.DOWNLOADING);
		}
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

		viewModel.postDownloading(true);
		viewModel.postStatusMessage(owner.getString(R.string.started));
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

		for (int i = 0; i < koza.length; i++) {
			currentkoza = 100 * i / koza.length;
			boolean isLegacy = MainActivity.ENGLISH.containsKey(koza[i]);

			NhkRepository.FetchResult result = nhkRepository.fetchEpisodes(koza[i], isLegacy);
			if (!result.isSuccess()) {
				if ("connection_error".equals(result.error)) {
					return owner.getString(R.string.conn_error);
				} else {
					return owner.getString(R.string.parse_error);
				}
			}

			List<NhkRepository.Episode> episodes = result.episodes;
			for (NhkRepository.Episode episode : episodes) {
				lastKouza = episode.kouza;
				lastHdate = episode.hdate;
				final String itemTitle = episode.kouza + "_" + episode.hdate;
				currentItemIndex++;
				final int idx = currentItemIndex;
				final boolean willSkip = isSkip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
						&& mediaRepository.isMediaExist(episode.kouza + "_" + episode.hdate + "." + type);
				viewModel.postAddItem(idx, new DownloadItem(itemTitle),
						willSkip ? DownloadItem.Status.SKIPPED : DownloadItem.Status.DOWNLOADING);

				if (isLegacy) {
					download(koza[i], episode.kouza, episode.hdate, episode.streamUrl, episode.nendo, type);
				} else {
					download2(koza[i], episode.kouza, episode.hdate, episode.streamUrl, episode.nendo, type);
				}

				final String msg = lastMessage;
				if (!willSkip) {
					DownloadItem.Status st = DownloadItem.Status.COMPLETED;
					if (msg != null && msg.equals(owner.getString(R.string.skipped))) {
						st = DownloadItem.Status.SKIPPED;
					} else if (msg != null && msg.equals(owner.getString(R.string.failed))) {
						st = DownloadItem.Status.FAILED;
					}
					viewModel.postUpdateItem(idx, 100, st);
				}
				if (isCancelled()) {
					return owner.getString(R.string.cancelled);
				}
			}
		}
		return lastMessage;
	}


	private void onProgressUpdate(Integer... values) {
		if (currentItemIndex >= 0) {
			viewModel.postUpdateItem(currentItemIndex, values[0], DownloadItem.Status.DOWNLOADING);
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

		viewModel.setStatusMessage(owner.getString(R.string.finished));
		viewModel.setDownloading(false);
	}


	protected void download(String koza, String kouza, String hdate, String file, String nendo, String type) {
		Log.d(TAG, "download: " + file);
		Clip mediaIn = new Clip(file);
		Clip mediaOut = new Clip(MainActivity.FILES_DIR.getPath() + "/" + kouza + "/" + kouza + "_" + hdate + "." + type);
		File dir = new File(MainActivity.FILES_DIR.getPath() + "/" + kouza);
		dir.mkdirs();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			if (isSkip && mediaRepository.isMediaExist(kouza + "_" + hdate + "." + type)) {
				lastMessage = owner.getString(R.string.skipped);
				return;
			}
		} else {
			if (isSkip && mediaRepository.isFileExist(kouza + "_" + hdate + "." + type)) {
				lastMessage = owner.getString(R.string.skipped);
				return;
			}
		}
		try {
			convertMedia(mediaIn, mediaOut, type, kouza + "_" + hdate, nendo);
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
			if (isSkip && mediaRepository.isMediaExist(kouza + "_" + hdate + "." + type)) {
				lastMessage = owner.getString(R.string.skipped);
				return;
			}
		} else {
			if (isSkip && mediaRepository.isFileExist(kouza + "/" + kouza + "_" + hdate + "." + type)) {
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
			convertMedia(mediaIn, mediaOut, type, kouza + "_" + hdate, nendo);
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


	private void convertMedia(Clip mediaIn, Clip mediaOut, String type, String title, String nendo) throws Exception {
		if (type.equals("3g2") || type.equals("3gp")) {
			mediaOut.audioCodec = "copy";
			fc.convertTo3GPAudio(mediaIn, mediaOut, new CommonShellCallBack());
		} else if (type.equals("aac") || type.equals("ts")) {
			mediaOut.audioCodec = "copy";
			fc.convertToAACAudio(mediaIn, mediaOut, new CommonShellCallBack());
		} else if (type.equals("avi") || type.equals("mka") || type.equals("mkv")) {
			mediaOut.audioCodec = "copy";
			fc.convertToAVIAudio(mediaIn, mediaOut, title, nendo, new CommonShellCallBack());
		} else if (type.equals("m4a") || type.equals("mov")) {
			mediaOut.audioCodec = "copy";
			fc.convertToMOVAudio(mediaIn, mediaOut, title, nendo, new CommonShellCallBack());
		} else if (type.equals("mp3")) {
			mediaOut.audioCodec = "libmp3lame";
			fc.convertToAVIAudio(mediaIn, mediaOut, title, nendo, new CommonShellCallBack());
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

			// Move files via MediaRepository
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
							Uri resultUri;
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								Log.d(TAG, "Storing file into Media: " + files[i] + "/" + sub_files[j]);
								resultUri = mediaRepository.storeMedia(subdir.getPath() + "/", sub_files[j], files[i]);
							} else {
								Log.d(TAG, "Moving file into: " + target_dir.getPath() + "/" + files[i] + "/" + sub_files[j]);
								resultUri = mediaRepository.moveFile(subdir.getPath() + "/", sub_files[j], target_dir.getPath() + "/" + files[i] + "/");
							}
							if (resultUri != null) {
								viewModel.postSetItemUri(currentItemIndex, resultUri);
							}
						}
					}
				}
			}
			available = 0;
		}
	}
}
