/*
 * Copyright (C) 2014 Naofumi Fukue
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.github.naofum.gogakudroid;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for MainActivity.
 * Manages download items, status messages, and download state via LiveData.
 */
public class MainViewModel extends ViewModel {

    // --- Download items (single source of truth) ---
    private final Object itemsLock = new Object();
    private final List<DownloadItem> items = new ArrayList<>();
    private final MutableLiveData<List<DownloadItem>> downloadItems = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<DownloadItem>> getDownloadItems() {
        return downloadItems;
    }

    public void addDownloadItem(DownloadItem item) {
        synchronized (itemsLock) {
            items.add(item);
            downloadItems.postValue(new ArrayList<>(items));
        }
    }

    public void updateDownloadItem(int index, int progress, DownloadItem.Status status) {
        synchronized (itemsLock) {
            if (index < 0 || index >= items.size()) {
                return;
            }
            DownloadItem item = items.get(index);
            DownloadItem.Status current = item.getStatus();
            if (current == DownloadItem.Status.COMPLETED
                    || current == DownloadItem.Status.FAILED
                    || current == DownloadItem.Status.SKIPPED) {
                return;
            }
            item.setProgress(progress);
            item.setStatus(status);
            downloadItems.postValue(new ArrayList<>(items));
        }
    }

    public void setDownloadItemUri(int index, Uri uri) {
        synchronized (itemsLock) {
            if (index < 0 || index >= items.size()) {
                return;
            }
            items.get(index).setContentUri(uri);
            downloadItems.postValue(new ArrayList<>(items));
        }
    }

    public void removeDownloadItem(int index) {
        synchronized (itemsLock) {
            if (index < 0 || index >= items.size()) {
                return;
            }
            items.remove(index);
            downloadItems.postValue(new ArrayList<>(items));
        }
    }

    public void clearDownloadItems() {
        synchronized (itemsLock) {
            items.clear();
            downloadItems.postValue(new ArrayList<>(items));
        }
    }

    public int getDownloadItemCount() {
        synchronized (itemsLock) {
            return items.size();
        }
    }

    // --- Status message (shown on CoursesFragment) ---
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String message) {
        statusMessage.setValue(message);
    }

    // Post from background thread
    public void postStatusMessage(String message) {
        statusMessage.postValue(message);
    }

    // --- Download running state ---
    private final MutableLiveData<Boolean> isDownloading = new MutableLiveData<>(false);

    public LiveData<Boolean> getIsDownloading() {
        return isDownloading;
    }

    public void setDownloading(boolean downloading) {
        isDownloading.setValue(downloading);
    }

    public void postDownloading(boolean downloading) {
        isDownloading.postValue(downloading);
    }
}
