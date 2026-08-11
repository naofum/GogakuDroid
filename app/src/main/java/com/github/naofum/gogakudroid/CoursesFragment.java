package com.github.naofum.gogakudroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;

public class CoursesFragment extends Fragment {

    protected ArrayList<Classes> classes;
    protected ListView list;
    protected ClassAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_courses, container, false);

        SharedPreferences pref = requireActivity().getSharedPreferences("target_class", Context.MODE_PRIVATE);
        Map<String, ?> map = pref.getAll();
        classes = new ArrayList<>();
        int i = 0;
        int chk;
        for (String key : MainActivity.MULTILINGUAL.keySet()) {
            chk = 0;
            if (map.containsKey(key)) {
                if ((Integer) map.get(key) == 1) {
                    chk = 1;
                }
            }
            Classes cls = new Classes(i, key, MainActivity.MULTILINGUAL.get(key), chk);
            classes.add(cls);
            i++;
        }

        list = view.findViewById(R.id.listView1);
        adapter = new ClassAdapter(requireActivity(), R.layout.list_row, classes);
        list.setAdapter(adapter);

        Button btn1 = view.findViewById(R.id.button1);
        btn1.setOnClickListener(v -> button1_click(v));

        // Disable button if storage permission is required but not granted
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(requireActivity(),
                    "android.permission.READ_MEDIA_AUDIO")
                    != PackageManager.PERMISSION_GRANTED) {
                btn1.setEnabled(false);
            }
        } else if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29) {
            if (ContextCompat.checkSelfPermission(requireActivity(),
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                btn1.setEnabled(false);
            }
        }

        return view;
    }

    public void setButtonEnabled(boolean enabled) {
        View view = getView();
        if (view != null) {
            Button btn1 = view.findViewById(R.id.button1);
            btn1.setEnabled(enabled);
        }
    }

    public void setStatusText(String text) {
        View view = getView();
        if (view != null) {
            TextView textView1 = view.findViewById(R.id.textView1);
            textView1.setText(text);
        }
    }

    public void button1_click(View view) {
        TextView textView1 = getView().findViewById(R.id.textView1);
        textView1.setText(R.string.started);
        view.setKeepScreenOn(true);
        savePreferences();

        ArrayList<String> arr = new ArrayList<>();
        for (int i = 0; i < classes.size(); i++) {
            if (classes.get(i).getIsDownload() != 0) {
                arr.add(classes.get(i).getName());
            }
        }
        ((MainActivity) requireActivity()).startDownload(arr.toArray(new String[0]));
    }

    public void savePreferences() {
        SharedPreferences pref = requireActivity().getSharedPreferences("target_class", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        for (String key : MainActivity.ENGLISH.keySet()) {
            editor.putInt(key, 0);
        }
        for (String key : MainActivity.MULTILINGUAL.keySet()) {
            editor.putInt(key, 0);
        }
        for (int i = 0; i < classes.size(); i++) {
            if (classes.get(i).getIsDownload() != 0) {
                editor.putInt(classes.get(i).getName(), 1);
            }
        }
        editor.commit();
    }

    public class ClassAdapter extends ArrayAdapter<Classes> {

        private ArrayList<Classes> items;
        private LayoutInflater inflater;

        public ClassAdapter(Context context, int textViewResourceId, ArrayList<Classes> items) {
            super(context, textViewResourceId, items);
            this.items = items;
            this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            if (rowView == null) {
                rowView = inflater.inflate(R.layout.list_row, parent, false);
            }

            Classes item = items.get(position);
            if (item != null) {
                TextView todoName = rowView.findViewById(R.id.todo_name);
                if (todoName != null) {
                    todoName.setText(item.getKouza());
                }
                CheckBox ck = rowView.findViewById(R.id.todo_check);
                final int p = position;
                ck.setOnCheckedChangeListener(null);
                if (item.getIsDownload() == 1) {
                    ck.setChecked(true);
                } else {
                    ck.setChecked(false);
                }
                ck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        classes.get(p).setIsDownload(1);
                    } else {
                        classes.get(p).setIsDownload(0);
                    }
                });
            }
            return rowView;
        }
    }

    static class Classes implements Serializable {

        private static final long serialVersionUID = 8023254505558453097L;

        private int id;
        private String name;
        private String kouza;
        private int is_download;

        protected Classes(int id, String name, String kouza, int is_download) {
            this.id = id;
            this.name = name;
            this.kouza = kouza;
            this.is_download = is_download;
        }

        public int getId() { return this.id; }
        public String getName() { return this.name; }
        public void setName(String name) { this.name = name; }
        public String getKouza() { return this.kouza; }
        public void setKouza(String kouza) { this.kouza = kouza; }
        public int getIsDownload() { return this.is_download; }
        public void setIsDownload(int flg) { this.is_download = flg; }
    }
}
