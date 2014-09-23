/*
 * Copyright (C) 2014 Naofumi Fukue
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.naofum.gogakudroid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.github.naofum.gogakudroid.R;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

	protected static String TAG = MainActivity.class.getSimpleName();
	protected static Map<String, String> ENGLISH = new HashMap<String, String>();

	private AdView adView;
	private static final String MY_AD_UNIT_ID = "ca-app-pub-9209419102968336/3450818802";

	static {
		ENGLISH.put("basic1", "基礎英語1");
		ENGLISH.put("basic2", "基礎英語2");
		ENGLISH.put("basic3", "基礎英語3");
		ENGLISH.put("timetrial", "英会話タイムトライアル");
		ENGLISH.put("kaiwa", "ラジオ英会話");
		ENGLISH.put("business1", "入門ビジネス英語");
		ENGLISH.put("business2", "実践ビジネス英語");
		ENGLISH.put("kouryaku", "攻略！英語リスニング");
		ENGLISH.put("yomu", "英語で読む村上春樹");
		ENGLISH.put("enjoy", "エンジョイ・シンプル・イングリッシュ");
	}
	protected static Map<String, String> MULTILINGUAL = new HashMap<String, String>();
	static {
		MULTILINGUAL.put("chinese", "まいにち中国語");
		MULTILINGUAL.put("levelup_chinese", "レベルアップ中国語");
		MULTILINGUAL.put("french", "まいにちフランス語");
		MULTILINGUAL.put("italian", "まいにちイタリア語");
		MULTILINGUAL.put("hangeul", "まいにちハングル講座");
		MULTILINGUAL.put("levelup_hangeul", "レベルアップハングル講座");
		MULTILINGUAL.put("german", "まいにちドイツ語");
		MULTILINGUAL.put("spanish", "まいにちスペイン語");
		MULTILINGUAL.put("russian", "まいにちロシア語");
	}

//	protected String[] koza = { "basic1", "basic2" };
//	protected FfmpegController fc;
	protected static ArrayList<Classes> classes;
	protected static ListView list;
	protected static ClassAdapter adapter = null;
	SharedPreferences sharedPref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		String theme = PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "AppBaseTheme");
		if (Build.VERSION.SDK_INT <= 13) {
	        if (theme.equals("Theme.Holo")) {
	            setTheme(android.R.style.Theme_Black);
	        } else if (theme.equals("Theme.Holo.Light")) {
		            setTheme(android.R.style.Theme_Light);
	        } else {
	            setTheme(android.R.style.Theme_Black);
	        }
		} else {
	        if (theme.equals("Theme.Holo")) {
	            setTheme(android.R.style.Theme_Holo);
	        } else if (theme.equals("Theme.Holo.Light")) {
	            setTheme(android.R.style.Theme_Holo_Light);
	        } else {
	            setTheme(android.R.style.Theme_Holo_Light_DarkActionBar);
	        }
        }

        super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		sharedPref = 
		        PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences pref = 
                this.getSharedPreferences( "target_class", Context.MODE_PRIVATE );
        Map<String, ?> map = pref.getAll();
		classes = new ArrayList<Classes>();
        int i = 0;
        int chk = 0;
        for (String key : ENGLISH.keySet()) {
    		chk = 0;
        	if (map.containsKey(key)) {
        		if ((Integer)map.get(key) == 1) {
        		    chk = 1;
            	}
        	}
        	Classes cls = new Classes(i, key, ENGLISH.get(key), chk);
        	classes.add(cls);
        	i++;
        }
        i = 0;
        for (String key : MULTILINGUAL.keySet()) {
    		chk = 0;
        	if (map.containsKey(key)) {
        		if ((Integer)map.get(key) == 1) {
        		    chk = 1;
            	}
        	}
        	Classes cls = new Classes(i, key, MULTILINGUAL.get(key), chk);
        	classes.add(cls);
        	i++;
        }

        list = (ListView)findViewById(R.id.listView1);
        adapter = new ClassAdapter(this, R.layout.list_row, classes);
        list.setAdapter(adapter);	

		Button btn1 = (Button) this.findViewById(R.id.button1);
		btn1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	button1_click(v);
            }
        });

		adView = new AdView(this);
		adView.setAdUnitId(MY_AD_UNIT_ID);
		adView.setAdSize(AdSize.BANNER);

        View root = getWindow().getDecorView();
        View firstChild = ((ViewGroup) root).getChildAt(0);
		((LinearLayout) firstChild).addView(adView);
		
//		RelativeLayout layout = (RelativeLayout)findViewById(R.id.main_view);
//		layout.addView(adView);

		AdRequest adRequest = new AdRequest.Builder().build();
		adView.loadAd(adRequest);
	}

	public void button1_click(View view) {
		TextView textView1 = (TextView) this.findViewById(R.id.textView1);
		textView1.setText(R.string.started);
		
		savePreferences();
    	ArrayList<String> arr = new ArrayList<String>();
    	for (int i = 0; i < classes.size(); i++) {
    		if (classes.get(i).is_download != 0) {
    			arr.add(classes.get(i).getName());
        	}
    	}
        AsyncDownload task = new AsyncDownload(this);
        task.owner = this;
        task.execute(arr.toArray(new String[0]));
	}

	public void savePreferences() {
        SharedPreferences pref = 
                this.getSharedPreferences( "target_class", Context.MODE_PRIVATE );
        Editor editor = pref.edit();
        for (String key : ENGLISH.keySet()) {
        	editor.putInt(key, 0);
        }
        for (String key : MULTILINGUAL.keySet()) {
        	editor.putInt(key, 0);
        }
        for (int i = 0; i < classes.size(); i++) {
        	if (classes.get(i).getIsDownload() != 0) {
            	editor.putInt(classes.get(i).getName(), 1);
        	}
        }
        editor.commit();		
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.optionmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
        case R.id.settings:
            startActivity(new Intent(this, Preference.class));
            return true;
        case R.id.about:
        	asset_dialog("about.html", R.string.about);
            return true;
        }
        return false;
    }

	private void asset_dialog(String filename, int title) {

		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.dialog_about);
		dialog.setTitle(title);
		TextView textview1 = (TextView) dialog.findViewById(R.id.textView1);

		String text = "";

		StringBuilder builder = new StringBuilder();
		InputStream fis;
		try {
			fis = getAssets().open(filename);
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					fis, "utf-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}

			text = builder.toString();
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		textview1.setText(Html.fromHtml(text));

		Button dialogButton = (Button) dialog.findViewById(R.id.button1);
		// if button is clicked, close the custom dialog
		dialogButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		dialog.show();
	}

    public class ClassAdapter extends ArrayAdapter {

		private ArrayList<Classes> items;
		private LayoutInflater inflater;

		@SuppressWarnings("unchecked")
		public ClassAdapter(Context context, int textViewResourceId,
				ArrayList<Classes> items) {
			super(context, textViewResourceId, items);
			this.items = items;
			this.inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@SuppressWarnings("unchecked")
		public void insert(String str, int position) {
			super.insert(str, position);
		}

		@SuppressWarnings("unchecked")
		public void remove(String str) {
			super.remove(str);
		}

		@SuppressWarnings("unchecked")
		public void add(String str) {
			super.add(str);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null) {
				view = inflater.inflate(R.layout.list_row, null);
			}

			Classes item = (Classes) items.get(position);
			if (item != null) {
				TextView todoName = (TextView) view
						.findViewById(R.id.todo_name);
				if (todoName != null) {
					todoName.setText(item.getKouza());
				}
				CheckBox ck = (CheckBox) view.findViewById(R.id.todo_check);
				final int p = position;
				ck.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						if (isChecked) {
							classes.get(p).setIsDownload(1);
						} else {
							classes.get(p).setIsDownload(0);
						}
					}
				});
				if (item.getIsDownload() == 1) {
					ck.setChecked(true);
				} else {
					ck.setChecked(false);
				}
			}
			return view;
		}
	}

	class Classes implements Serializable {

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

		public int getId() {
			return this.id;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getKouza() {
			return this.kouza;
		}

		public void setKouza(String kouza) {
			this.kouza = kouza;
		}

		public int getIsDownload() {
			return this.is_download;
		}

		public void setIsDownload(int flg) {
			this.is_download = flg;
		}
	}
}
