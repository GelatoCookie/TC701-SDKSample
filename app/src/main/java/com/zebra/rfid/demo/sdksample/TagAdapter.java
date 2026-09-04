package com.zebra.rfid.demo.sdksample;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

class TagAdapter extends RecyclerView.Adapter<TagAdapter.TagViewHolder> {

    static class TagItem {
        final String tagId;
        final String rssi;
        final boolean accessResult;

        TagItem(String tagId, String rssi) {
            this(tagId, rssi, false);
        }

        TagItem(String tagId, String rssi, boolean accessResult) {
            this.tagId = tagId;
            this.rssi = rssi;
            this.accessResult = accessResult;
        }
    }

    private final List<TagItem> tags = new ArrayList<>();

    void addTag(String tagId, String rssi) {
        tags.add(new TagItem(tagId, rssi));
        notifyItemInserted(tags.size() - 1);
    }

    void addAccessResult(String primary, String status) {
        tags.add(new TagItem(primary, status, true));
        notifyItemInserted(tags.size() - 1);
    }

    void clear() {
        int size = tags.size();
        tags.clear();
        notifyItemRangeRemoved(0, size);
    }

    @NonNull
    @Override
    public TagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag, parent, false);
        return new TagViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TagViewHolder holder, int position) {
        TagItem item = tags.get(position);
        holder.tagIdText.setText(item.tagId);
        holder.rssiText.setText(item.accessResult ? item.rssi : item.rssi + " dBm");
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    static class TagViewHolder extends RecyclerView.ViewHolder {
        final TextView tagIdText;
        final TextView rssiText;

        TagViewHolder(@NonNull View itemView) {
            super(itemView);
            tagIdText = itemView.findViewById(R.id.tagIdText);
            rssiText = itemView.findViewById(R.id.rssiText);
        }
    }
}
