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
import java.util.LinkedHashMap;
import java.util.Map;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

public class MainActivity extends Activity {

	private static final String TAG = MainActivity.class.getSimpleName();
	private static final String STATE_TEXT = "MainActivity.text";
	protected static Map<String, String> ENGLISH = new LinkedHashMap<String, String>();
    protected AsyncDownload mTask;

	private AdView adView;

	static {
		ENGLISH.put("basic0", "基礎英語0");
		ENGLISH.put("basic1", "基礎英語1");
		ENGLISH.put("basic2", "基礎英語2");
		ENGLISH.put("basic3", "基礎英語3");
		ENGLISH.put("timetrial", "英会話タイムトライアル");
		ENGLISH.put("kaiwa", "ラジオ英会話");
		ENGLISH.put("business1", "入門ビジネス英語");
		ENGLISH.put("business2", "実践ビジネス英語");
		ENGLISH.put("gendai", "高校生からはじめる「現代英語」");
		ENGLISH.put("gakusyu", "遠山顕の英会話楽習");
		ENGLISH.put("enjoy", "エンジョイ・シンプル・イングリッシュ");
	}
	protected static Map<String, String> MULTILINGUAL = new LinkedHashMap<String, String>();
	static {
		MULTILINGUAL.put("chinese_kouza", "まいにち中国語");
		MULTILINGUAL.put("chinese_levelup", "レベルアップ中国語");
        MULTILINGUAL.put("chinese_omotenashi", "おもてなしの中国語");
        MULTILINGUAL.put("hangeul_kouza", "まいにちハングル講座");
        MULTILINGUAL.put("hangeul_levelup", "レベルアップハングル講座");
		MULTILINGUAL.put("hangeul_omotenashi", "おもてなしのハングル");
        MULTILINGUAL.put("italian_kouza", "まいにちイタリア語【初級編】");
        MULTILINGUAL.put("italian_kouza2", "まいにちイタリア語【応用編】");
        MULTILINGUAL.put("german_kouza", "まいにちドイツ語【初級編】");
        MULTILINGUAL.put("german_kouza2", "まいにちドイツ語【応用編】");
		MULTILINGUAL.put("french_kouza", "まいにちフランス語【初級編】");
        MULTILINGUAL.put("french_kouza2", "まいにちフランス語【応用編】");
		MULTILINGUAL.put("spanish_kouza", "まいにちスペイン語【入門編】");
        MULTILINGUAL.put("spanish_kouza2", "まいにちスペイン語【中級編】");
		MULTILINGUAL.put("russian_kouza", "まいにちロシア語【入門編】");
        MULTILINGUAL.put("russian_kouza2", "まいにちロシア語【応用編】");
	}

	protected static ArrayList<Classes> classes;
	protected static ListView list;
	protected static ClassAdapter adapter = null;
	SharedPreferences sharedPref;

	@Override
	protected void onStop() {
		super.onStop();
		if (mTask != null) {
			mTask.cancel(true);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mTask != null) {
			TextView textView1 = (TextView) this.findViewById(R.id.textView1);
			outState.putString(STATE_TEXT, textView1.getText().toString());
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                android.Manifest.permission.READ_EXTERNAL_STORAGE},
                        1);
            }
        }
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

		adView = (AdView) findViewById(R.id.adView);
		AdRequest adRequest = new AdRequest.Builder().build();
		adView.loadAd(adRequest);

		if (savedInstanceState != null && savedInstanceState.containsKey(STATE_TEXT)) {
			String text = savedInstanceState.getString(STATE_TEXT);
			TextView textView1 = (TextView) this.findViewById(R.id.textView1);
			textView1.setText(text);
	    	ArrayList<String> arr = new ArrayList<String>();
	    	for (int j = 0; j < classes.size(); j++) {
	    		if (classes.get(j).is_download != 0) {
	    			arr.add(classes.get(j).getName());
	        	}
	    	}
	        mTask = new AsyncDownload(this);
	        mTask.owner = this;
	        mTask.execute(arr.toArray(new String[0]));
		}
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
        mTask = new AsyncDownload(this);
        mTask.owner = this;
        mTask.execute(arr.toArray(new String[0]));
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
        case R.id.privacy:
        	asset_dialog("privacy.html", R.string.privacy);
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
		textview1.setMovementMethod(LinkMovementMethod.getInstance());

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
