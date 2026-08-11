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

    // --- Download items ---
    private final MutableLiveData<List<DownloadItem>> downloadItems = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<DownloadItem>> getDownloadItems() {
        return downloadItems;
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

    // --- Events for download item updates ---

    /**
     * Event to add a new download item.
     */
    public static class AddItemEvent {
        public final int index;
        public final DownloadItem item;
        public final DownloadItem.Status initialStatus;

        public AddItemEvent(int index, DownloadItem item, DownloadItem.Status initialStatus) {
            this.index = index;
            this.item = item;
            this.initialStatus = initialStatus;
        }
    }

    private final MutableLiveData<AddItemEvent> addItemEvent = new MutableLiveData<>();

    public LiveData<AddItemEvent> getAddItemEvent() {
        return addItemEvent;
    }

    public void postAddItem(int index, DownloadItem item, DownloadItem.Status initialStatus) {
        addItemEvent.postValue(new AddItemEvent(index, item, initialStatus));
    }

    /**
     * Event to update download item progress/status.
     */
    public static class UpdateItemEvent {
        public final int index;
        public final int progress;
        public final DownloadItem.Status status;

        public UpdateItemEvent(int index, int progress, DownloadItem.Status status) {
            this.index = index;
            this.progress = progress;
            this.status = status;
        }
    }

    private final MutableLiveData<UpdateItemEvent> updateItemEvent = new MutableLiveData<>();

    public LiveData<UpdateItemEvent> getUpdateItemEvent() {
        return updateItemEvent;
    }

    public void postUpdateItem(int index, int progress, DownloadItem.Status status) {
        updateItemEvent.postValue(new UpdateItemEvent(index, progress, status));
    }

    /**
     * Event to set item URI after media is stored.
     */
    public static class SetItemUriEvent {
        public final int index;
        public final Uri uri;

        public SetItemUriEvent(int index, Uri uri) {
            this.index = index;
            this.uri = uri;
        }
    }

    private final MutableLiveData<SetItemUriEvent> setItemUriEvent = new MutableLiveData<>();

    public LiveData<SetItemUriEvent> getSetItemUriEvent() {
        return setItemUriEvent;
    }

    public void postSetItemUri(int index, Uri uri) {
        setItemUriEvent.postValue(new SetItemUriEvent(index, uri));
    }
}
