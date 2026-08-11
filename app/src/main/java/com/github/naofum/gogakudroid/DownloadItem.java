package com.github.naofum.gogakudroid;

import android.net.Uri;

public class DownloadItem {

    public enum Status {
        WAITING,
        DOWNLOADING,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    private String title;
    private int progress;
    private Status status;
    private Uri contentUri;

    public DownloadItem(String title) {
        this.title = title;
        this.progress = 0;
        this.status = Status.WAITING;
    }

    public String getTitle() {
        return title;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Uri getContentUri() {
        return contentUri;
    }

    public void setContentUri(Uri contentUri) {
        this.contentUri = contentUri;
    }
}
