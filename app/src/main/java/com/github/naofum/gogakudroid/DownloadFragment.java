package com.github.naofum.gogakudroid;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DownloadFragment extends Fragment {

    private ArrayList<DownloadItem> downloadItems = new ArrayList<>();
    private DownloadAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_download, container, false);
        recyclerView = view.findViewById(R.id.downloadRecyclerView);
        emptyText = view.findViewById(R.id.emptyText);

        adapter = new DownloadAdapter(downloadItems, this::getStatusString);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
        recyclerView.setAdapter(adapter);

        // Swipe to delete
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                DownloadItem item = downloadItems.get(position);
                removeDownloadRecord(item.getTitle());
                adapter.removeItem(position);
                updateEmptyState();
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        view.findViewById(R.id.clearButton).setOnClickListener(v -> clearItems());

        updateEmptyState();
        return view;
    }

    public void clearItems() {
        for (DownloadItem item : downloadItems) {
            removeDownloadRecord(item.getTitle());
        }
        downloadItems.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateEmptyState();
    }

    public void addItem(DownloadItem item) {
        downloadItems.add(item);
        if (adapter != null) {
            adapter.notifyItemInserted(downloadItems.size() - 1);
        }
        updateEmptyState();
    }

    public void updateItem(int index, int progress, DownloadItem.Status status) {
        if (index >= 0 && index < downloadItems.size()) {
            DownloadItem item = downloadItems.get(index);
            // Don't overwrite a terminal status with a non-terminal one
            DownloadItem.Status current = item.getStatus();
            if (current == DownloadItem.Status.COMPLETED
                    || current == DownloadItem.Status.FAILED
                    || current == DownloadItem.Status.SKIPPED) {
                return;
            }
            item.setProgress(progress);
            item.setStatus(status);
            if (adapter != null) {
                adapter.notifyItemChanged(index);
            }
        }
    }

    public int getItemCount() {
        return downloadItems.size();
    }

    public void setItemUri(int index, android.net.Uri uri) {
        if (index >= 0 && index < downloadItems.size()) {
            downloadItems.get(index).setContentUri(uri);
            if (adapter != null) {
                adapter.notifyItemChanged(index);
            }
        }
    }

    private void updateEmptyState() {
        if (emptyText == null) return;
        if (downloadItems.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private String getStatusString(DownloadItem.Status status) {
        switch (status) {
            case WAITING:
                return getString(R.string.status_waiting);
            case DOWNLOADING:
                return getString(R.string.status_downloading);
            case COMPLETED:
                return getString(R.string.status_completed);
            case FAILED:
                return getString(R.string.status_failed);
            case SKIPPED:
                return getString(R.string.status_skipped);
            default:
                return "";
        }
    }

    private void removeDownloadRecord(String title) {
        // Remove records for all possible extensions
        String[] extensions = {"3g2", "3gp", "aac", "m4a", "mp3", "avi", "mka", "mkv", "mov", "ts"};
        android.content.SharedPreferences files = requireActivity()
                .getSharedPreferences("downloaded_files", android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences uris = requireActivity()
                .getSharedPreferences("downloaded_uris", android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor filesEditor = files.edit();
        android.content.SharedPreferences.Editor urisEditor = uris.edit();
        for (String ext : extensions) {
            String key = title + "." + ext;
            filesEditor.remove(key);
            urisEditor.remove(key);
        }
        filesEditor.apply();
        urisEditor.apply();
    }
}
