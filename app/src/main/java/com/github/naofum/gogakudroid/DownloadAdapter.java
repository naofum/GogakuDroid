package com.github.naofum.gogakudroid;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    private List<DownloadItem> items = new ArrayList<>();
    private final StatusTextProvider statusTextProvider;

    public interface StatusTextProvider {
        String getStatusText(DownloadItem.Status status);
    }

    public DownloadAdapter(StatusTextProvider provider) {
        this.statusTextProvider = provider;
    }

    public void setItems(List<DownloadItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public DownloadItem getItem(int position) {
        return items.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_row_download, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadItem item = items.get(position);
        holder.title.setText(item.getTitle());
        holder.progressBar.setProgress(item.getProgress());
        holder.status.setText(statusTextProvider.getStatusText(item.getStatus()));

        // Show play button only for completed items with a content URI
        if (item.getStatus() == DownloadItem.Status.COMPLETED && item.getContentUri() != null) {
            holder.playButton.setVisibility(View.VISIBLE);
            holder.playButton.setOnClickListener(v -> {
                Context context = v.getContext();
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(item.getContentUri(), "audio/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    context.startActivity(intent);
                } catch (android.content.ActivityNotFoundException e) {
                    // No media player installed
                }
            });
        } else {
            holder.playButton.setVisibility(View.GONE);
            holder.playButton.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final ProgressBar progressBar;
        final TextView status;
        final ImageButton playButton;

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.downloadTitle);
            progressBar = itemView.findViewById(R.id.downloadProgress);
            status = itemView.findViewById(R.id.downloadStatus);
            playButton = itemView.findViewById(R.id.playButton);
        }
    }
}
