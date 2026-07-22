package com.fateh7.hiderootapps.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fateh7.hiderootapps.R;

import org.json.JSONArray;
import org.json.JSONObject;

public class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.VH> {

    public interface Listener {
        void onOpen(String name);
        void onDelete(String name);
    }

    private JSONArray data = new JSONArray();
    private final Listener listener;

    public TemplateAdapter(Listener l) {
        this.listener = l;
    }

    public void setData(JSONArray arr) {
        this.data = arr != null ? arr : new JSONArray();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_template, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        JSONObject o = data.optJSONObject(position);
        if (o == null) return;
        final String name = o.optString("name");
        int count = o.optJSONArray("pkgs") != null ? o.optJSONArray("pkgs").length() : 0;
        h.name.setText(name);
        h.count.setText(h.itemView.getContext()
                .getString(R.string.count_selected, count));
        h.itemView.setOnClickListener(v -> listener.onOpen(name));
        h.delete.setOnClickListener(v -> listener.onDelete(name));
    }

    @Override
    public int getItemCount() {
        return data.length();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, count;
        ImageButton delete;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.t_name);
            count = v.findViewById(R.id.t_count);
            delete = v.findViewById(R.id.t_delete);
        }
    }
}
