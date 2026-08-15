/*
 * Copyright (C) 2014 Naofumi Fukue
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.github.naofum.gogakudroid;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repository for media storage operations.
 * Handles MediaStore insertion, file movement, existence checks, and download history.
 */
public class MediaRepository {

    private static final String TAG = MediaRepository.class.getSimpleName();
    private static final int MAX_DOWNLOAD_HISTORY = 500;

    private final Context context;

    public MediaRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Store a media file into MediaStore (Android Q+).
     * Copies the file from inputPath to MediaStore Downloads, then deletes the original.
     *
     * @return the content URI of the stored media, or null on failure.
     */
    public Uri storeMedia(String inputPath, String inputFile, String outputPath) {
        String downloadPath = Environment.DIRECTORY_DOWNLOADS
                .substring(Environment.DIRECTORY_DOWNLOADS.lastIndexOf("/") + 1);
        ContentValues contentValues = new ContentValues();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Downloads.RELATIVE_PATH, downloadPath + "/" + outputPath);
        }
        contentValues.put(MediaStore.Downloads.DISPLAY_NAME, inputFile);
        contentValues.put(MediaStore.Downloads.MIME_TYPE, getMimeType(inputFile));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Downloads.IS_PENDING, 1);
        }

        ContentResolver resolver = context.getContentResolver();
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Audio.Media.DATA,
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            inputPath).getAbsolutePath() + "/" + outputPath);
        }

        Uri item = resolver.insert(collection, contentValues);
        if (item == null) {
            Log.e(TAG, "Failed to insert into MediaStore");
            return null;
        }

        try (OutputStream out = resolver.openOutputStream(item);
             InputStream in = new FileInputStream(inputPath + inputFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy media to MediaStore", e);
            return null;
        }

        // Delete the original file
        new File(inputPath + inputFile).delete();

        // Mark as not pending
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear();
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(item, contentValues, null, null);
        }

        markMediaDownloaded(inputFile);
        saveContentUri(inputFile, item);

        return item;
    }

    /**
     * Move a file from inputPath to outputPath (pre-Q fallback).
     *
     * @return the file URI of the moved file, or null on failure.
     */
    public Uri moveFile(String inputPath, String inputFile, String outputPath) {
        Log.d(TAG, "Moving file from:" + inputPath + inputFile + " into: " + outputPath);

        File dir = new File(outputPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try (InputStream in = new FileInputStream(inputPath + inputFile);
             OutputStream out = new FileOutputStream(outputPath + inputFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Move file error: " + e.getMessage());
            return null;
        }

        // Delete the original file
        new File(inputPath + inputFile).delete();
        markMediaDownloaded(inputFile);

        return Uri.fromFile(new File(outputPath + inputFile));
    }

    /**
     * Check if a media file exists in MediaStore (Q+) or SharedPreferences + file system (pre-Q).
     */
    public boolean isMediaExist(String inputFile) {
        Log.d("GogakuDroid", "Search: " + inputFile);

        // Debug: dump all accessible audio media information
/*
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
            try (android.database.Cursor cursor = context.getContentResolver().query(
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
*/

        SharedPreferences pref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
        if (!pref.contains(inputFile)) {
            return false;
        }

        // Verify the file still exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Search by TITLE (filename without extension)
            String title = inputFile.contains(".")
                    ? inputFile.substring(0, inputFile.lastIndexOf("."))
                    : inputFile;
            Uri audioUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            try (android.database.Cursor cursor = context.getContentResolver().query(
                    audioUri,
                    new String[]{MediaStore.Audio.Media._ID},
                    MediaStore.Audio.Media.TITLE + "=?",
                    new String[]{title},
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
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
        context.getSharedPreferences("downloaded_uris", Context.MODE_PRIVATE)
                .edit().remove(inputFile).apply();
        return false;
    }

    /**
     * Check if a file exists on the file system (pre-Q).
     */
    public boolean isFileExist(String inputFile) {
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + inputFile);
        Log.d(TAG, "Check file: " + file.getPath());
        Log.d(TAG, "file exist: " + file.exists());
        return file.exists();
    }

    /**
     * Record that a media file has been downloaded.
     */
    public void markMediaDownloaded(String inputFile) {
        SharedPreferences pref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
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

    private void saveContentUri(String inputFile, Uri uri) {
        context.getSharedPreferences("downloaded_uris", Context.MODE_PRIVATE)
                .edit().putString(inputFile, uri.toString()).apply();
    }

    private String getMimeType(String filename) {
        if (filename.endsWith("mp3")) return "audio/mpeg";
        if (filename.endsWith("ogg")) return "audio/ogg";
        if (filename.endsWith("3gp")) return "audio/3gpp";
        if (filename.endsWith("3g2")) return "audio/3gpp2";
        if (filename.endsWith("m4a")) return "audio/x-m4a";
        return "audio/aac";
    }
}
