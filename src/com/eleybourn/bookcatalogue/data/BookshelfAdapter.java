package com.eleybourn.bookcatalogue.data;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.R;

public class BookshelfAdapter extends ListAdapter<Bookshelf, BookshelfAdapter.BookshelfViewHolder> {
    private final OnItemClickListener listener;
    private final OnItemLongClickListener longListener; // 1. Add Long Click Listener

    public interface OnItemClickListener {
        void onItemClick(Bookshelf bookshelf);
    }

    // Define Long Click Interface
    public interface OnItemLongClickListener {
        void onItemLongClick(Bookshelf bookshelf, View view, ContextMenu menu);
    }

    // Update Constructor
    public BookshelfAdapter(OnItemClickListener listener, OnItemLongClickListener longListener) {
        super(new DiffCallback());
        this.listener = listener;
        this.longListener = longListener;
    }

    @NonNull
    @Override
    public BookshelfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // INFLATE THE NEW ROW LAYOUT HERE
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_bookshelf, parent, false);
        return new BookshelfViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BookshelfViewHolder holder, int position) {
        holder.bind(getItem(position), listener, longListener);
    }

    public static class BookshelfViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        public BookshelfViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.row_bookshelf);
        }

        public void bind(Bookshelf bookshelf, OnItemClickListener listener, OnItemLongClickListener longListener) {
            textView.setText(bookshelf.name);

            // Short click
            itemView.setOnClickListener(v -> listener.onItemClick(bookshelf));

            // Long click (Context Menu)
            itemView.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
                // Pass the MENU object to the activity so it can populate it
                longListener.onItemLongClick(bookshelf, v, menu);
            });
        }
    }

    static class DiffCallback extends DiffUtil.ItemCallback<Bookshelf> {
        @Override
        public boolean areItemsTheSame(@NonNull Bookshelf oldItem, @NonNull Bookshelf newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Bookshelf oldItem, @NonNull Bookshelf newItem) {
            return oldItem.name.equals(newItem.name);
        }
    }
}
