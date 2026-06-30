package com.eleybourn.bookcatalogue.data;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.R;

import java.util.ArrayList;
import java.util.List;

public class FormatAdapter extends RecyclerView.Adapter<FormatAdapter.FormatViewHolder> {
    private final List<FormatCount> mFormats = new ArrayList<>();
    private final OnItemClickListener listener;
    private final OnItemLongClickListener longListener;

    public interface OnItemClickListener {
        void onItemClick(String format);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(String format, View view, ContextMenu menu);
    }

    public FormatAdapter(OnItemClickListener listener, OnItemLongClickListener longListener) {
        this.listener = listener;
        this.longListener = longListener;
    }

    public void setFormats(List<FormatCount> formats) {
        mFormats.clear();
        mFormats.addAll(formats);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FormatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_format, parent, false);
        return new FormatViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FormatViewHolder holder, int position) {
        holder.bind(mFormats.get(position), listener, longListener);
    }

    @Override
    public int getItemCount() {
        return mFormats.size();
    }

    public static class FormatViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        private final TextView countView;

        public FormatViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.row_format);
            countView = itemView.findViewById(R.id.row_format_count);
        }

        public void bind(FormatCount formatCount, OnItemClickListener listener, OnItemLongClickListener longListener) {
            textView.setText(formatCount.format);
            countView.setText(String.valueOf(formatCount.count));

            itemView.setOnClickListener(v -> listener.onItemClick(formatCount.format));

            itemView.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
                longListener.onItemLongClick(formatCount.format, v, menu);
            });
        }
    }
}
