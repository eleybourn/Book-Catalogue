package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Generic Activity for editing a list of objects using RecyclerView with Drag & Drop.
 */
public abstract class BookEditObjectList<T extends Serializable> extends BookCatalogueActivity {

    // List Data
    protected ArrayList<T> mList = null;

    // RecyclerView and Adapter
    protected RecyclerView mRecyclerView;
    protected RecyclerListAdapter mAdapter;
    protected ItemTouchHelper mItemTouchHelper;
    protected TextView mEmptyView; // Reference to the empty view

    // DB connection
    protected CatalogueDBAdapter mDbHelper;

    protected String mBookTitle;
    protected String mBookTitleLabel;

    // Configuration
    private String mKey;
    private final int mBaseViewId; // Removed final to fix initialization error
    private final int mRowViewId;  // Removed final to fix initialization error

    protected Long mRowId = null;

    // Abstract methods required by subclasses
    abstract protected void onAdd(View v);
    abstract protected void onSetupView(View target, T object);
    abstract protected void onRowClick(View target, int position, T object);

    protected boolean onSave(Intent intent) { return true; }
    protected boolean onCancel() { return true; }
    protected void onListChanged() { }
    protected ArrayList<T> getList() { return null; }

    protected BookEditObjectList(String key, int baseViewId, int rowViewId) {
        mKey = key;
        mBaseViewId = baseViewId;
        mRowViewId = rowViewId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            mDbHelper = new CatalogueDBAdapter(this);
            mDbHelper.open();

            setContentView(mBaseViewId);
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            if (topAppBar != null) {
                setSupportActionBar(topAppBar);
                topAppBar.setTitle(R.string.app_name);
                topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
            }

            setupListener(R.id.button_confirm, mSaveListener);
            setupListener(R.id.button_cancel, mCancelListener);
            setupListener(R.id.add, mAddListener);

            // Load List Data
            if (savedInstanceState != null && mKey != null && savedInstanceState.containsKey(mKey)) {
                mList = Utils.getListFromBundle(savedInstanceState, mKey);
            }

            if (mList == null) {
                Bundle extras = getIntent().getExtras();
                if (extras != null && mKey != null) {
                    mList = Utils.getListFromBundle(extras, mKey);
                }
                if (mList == null) mList = getList();
                if (mList == null) throw new RuntimeException("Unable to find list key '" + mKey + "'");
            }

            // --- RECYCLER VIEW SETUP ---
            mRecyclerView = findViewById(R.id.list);
            if (mRecyclerView == null) {
                mRecyclerView = findViewById(R.id.list);
            }

            // Look for the standard "Empty" view often used with ListViews (android:id/empty)
            mEmptyView = findViewById(R.id.empty);

            // Layout Manager is required
            mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

            // Initialize Adapter
            mAdapter = new RecyclerListAdapter();
            mRecyclerView.setAdapter(mAdapter);

            // Initialize Drag and Drop
            ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mAdapter);
            mItemTouchHelper = new ItemTouchHelper(callback);
            mItemTouchHelper.attachToRecyclerView(mRecyclerView);

            // Update empty view visibility initially
            updateEmptyView();

            // Handle extras for titles
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                mRowId = extras.getLong(CatalogueDBAdapter.KEY_ROW_ID);
                mBookTitleLabel = extras.getString("label_title");
                mBookTitle = extras.getString("field_title");
                setTextOrHideView(R.id.field_title, mBookTitle);
            }

        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    protected void setList(ArrayList<T> newList) {
        mList = newList;
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
            updateEmptyView();
        }
    }

    /**
     * Toggles visibility between the RecyclerView and the Empty View
     */
    private void updateEmptyView() {
        if (mEmptyView == null) return;

        if (mList == null || mList.isEmpty()) {
            mRecyclerView.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mRecyclerView.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
        }
    }

    // --- RECYCLER ADAPTER ---

    public class RecyclerListAdapter extends RecyclerView.Adapter<RecyclerListAdapter.ViewHolder>
            implements ItemTouchHelperAdapter {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(mRowViewId, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            T object = mList.get(position);

            // Populate fields (Author Name, Sort Name)
            onSetupView(holder.itemView, object);

            // 1. Row Click Listener
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    onRowClick(v, pos, mList.get(pos));
                }
            });

            // 2. Drag Handle Listener (id: grabber)
            View grabber = holder.itemView.findViewById(R.id.grabber);
            if (grabber != null) {
                grabber.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        mItemTouchHelper.startDrag(holder);
                    }
                    return false;
                });
            }

            // 3. Delete Button Listener (id: row_delete)
            View deleteBtn = holder.itemView.findViewById(R.id.row_delete);
            if (deleteBtn != null) {
                // EXPLICITLY SET THE ICON AS REQUESTED
                if (deleteBtn instanceof ImageView) {
                    ((ImageView) deleteBtn).setImageResource(R.drawable.ic_menu_delete);
                }

                deleteBtn.setOnClickListener(v -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        mList.remove(pos);
                        notifyItemRemoved(pos);
                        notifyItemRangeChanged(pos, mList.size());
                        onListChanged();
                        updateEmptyView(); // Check if list is now empty
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return (mList != null) ? mList.size() : 0;
        }

        @Override
        public boolean onItemMove(int fromPosition, int toPosition) {
            if (fromPosition < toPosition) {
                for (int i = fromPosition; i < toPosition; i++) {
                    Collections.swap(mList, i, i + 1);
                }
            } else {
                for (int i = fromPosition; i > toPosition; i--) {
                    Collections.swap(mList, i, i - 1);
                }
            }
            notifyItemMoved(fromPosition, toPosition);
            onListChanged();
            return true;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(View itemView) {
                super(itemView);
            }
        }
    }

    // --- DRAG AND DROP HELPERS ---

    public interface ItemTouchHelperAdapter {
        boolean onItemMove(int fromPosition, int toPosition);
    }

    public static class SimpleItemTouchHelperCallback extends ItemTouchHelper.Callback {

        private final ItemTouchHelperAdapter mAdapter;

        public SimpleItemTouchHelperCallback(ItemTouchHelperAdapter adapter) {
            mAdapter = adapter;
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            int swipeFlags = 0;
            return makeMovementFlags(dragFlags, swipeFlags);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder source,
                              @NonNull RecyclerView.ViewHolder target) {
            return mAdapter.onItemMove(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            // Not used
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                viewHolder.itemView.setAlpha(0.7f);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1.0f);
        }
    }

    // --- STANDARD UTILS ---

    private void setupListener(int id, OnClickListener l) {
        View v = this.findViewById(id);
        if (v != null) v.setOnClickListener(l);
    }

    protected void setTextOrHideView(View v, int id, String s) {
        if (v != null && v.getId() != id) v = v.findViewById(id);
        setTextOrHideView(v, s);
    }

    protected void setTextOrHideView(View v, String s) {
        if (v == null) return;
        try {
            if (s != null && !s.isEmpty()) {
                ((TextView) v).setText(s);
                return;
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
        v.setVisibility(View.GONE);
    }

    @SuppressWarnings("SameParameterValue")
    protected void setTextOrHideView(int id, String s) {
        setTextOrHideView(this.findViewById(id), id, s);
    }

    private final OnClickListener mSaveListener = v -> {
        Intent i = new Intent();
        i.putExtra(mKey, mList);
        if (onSave(i)) {
            setResult(RESULT_OK, i);
            finish();
        }
    };

    private final OnClickListener mCancelListener = v -> {
        if (onCancel()) finish();
    };

    private final OnClickListener mAddListener = v -> {
        onAdd(v);
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
            updateEmptyView(); // Check if we need to hide the empty view
        }
        onListChanged();
    };
}
