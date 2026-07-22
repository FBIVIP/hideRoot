package com.fateh7.hiderootapps.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fateh7.hiderootapps.R;
import com.fateh7.hiderootapps.data.AppItem;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shows apps with a checkbox; tracks the selected package set. */
public class AppSelectAdapter extends RecyclerView.Adapter<AppSelectAdapter.VH> {

    private final List<AppItem> items = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();

    public void setItems(List<AppItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void setSelected(Set<String> sel) {
        selected.clear();
        selected.addAll(sel);
        notifyDataSetChanged();
    }

    public Set<String> getSelected() {
        return new HashSet<>(selected);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_app_select, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppItem it = items.get(position);
        h.icon.setImageDrawable(it.icon);
        h.label.setText(it.label);
        h.pkg.setText(it.pkg);
        h.check.setChecked(selected.contains(it.pkg));
        h.itemView.setOnClickListener(v -> {
            if (selected.contains(it.pkg)) selected.remove(it.pkg);
            else selected.add(it.pkg);
            h.check.setChecked(selected.contains(it.pkg));
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView label, pkg;
        MaterialCheckBox check;

        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.a_icon);
            label = v.findViewById(R.id.a_label);
            pkg = v.findViewById(R.id.a_pkg);
            check = v.findViewById(R.id.a_check);
        }
    }
}
