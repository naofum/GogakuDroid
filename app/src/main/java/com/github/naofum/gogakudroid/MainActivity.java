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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

	private static final String TAG = MainActivity.class.getSimpleName();
	protected AsyncDownload mTask;

	private AdView adView;
	private String currentTheme;
	private ViewPager2 viewPager;
	private CoursesFragment coursesFragment;
	private DownloadFragment downloadFragment;
	private MainViewModel viewModel;

	protected static Map<String, String> MULTILINGUAL = new LinkedHashMap<String, String>();
	static {
		MULTILINGUAL.put("GGQY3M1929", "小学生の基礎英語");
		MULTILINGUAL.put("148W8XX226", "中学生の基礎英語_レベル1");
		MULTILINGUAL.put("83RW6PK3GG", "中学生の基礎英語_レベル2");
		MULTILINGUAL.put("PMMJ59J6N2", "ラジオ英会話");
		MULTILINGUAL.put("8Z6XJ6J415", "英会話タイムトライアル");
		MULTILINGUAL.put("368315KKP8", "ラジオビジネス英語");
		MULTILINGUAL.put("BR8Z3NX7XM", "エンジョイ・シンプル・イングリッシュ");
		MULTILINGUAL.put("77RQWQX1L6", "ニュースで学ぶ「現代英語」");
		MULTILINGUAL.put("983PKQPYN7", "まいにち中国語");
		MULTILINGUAL.put("LR47WW9K14", "まいにちハングル講座");
		MULTILINGUAL.put("LJWZP7XVMX", "まいにちイタリア語");
		MULTILINGUAL.put("N8PZRZ9WQY", "まいにちドイツ語");
		MULTILINGUAL.put("XQ487ZM61K", "まいにちフランス語");
		MULTILINGUAL.put("NRZWXVGQ19", "まいにちスペイン語");
		MULTILINGUAL.put("YRLK72JZ7Q", "まいにちロシア語");
//		MULTILINGUAL.put("WKMNWGMN6R", "アラビア語講座");
		MULTILINGUAL.put("N13V9K157Y", "ポルトガル語");
//		MULTILINGUAL.put("4MY6Q8XP88", "Living_in_Japan");
//		MULTILINGUAL.put("6LPPKP6W8Q", "やさしい日本語");
	}

	protected static File FILES_DIR;

	@Override
	protected void onStop() {
		super.onStop();
		if (mTask != null && isFinishing()) {
			mTask.cancel(true);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		String theme = PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "AppBaseTheme");
		if (!theme.equals(currentTheme)) {
			recreate();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == 1) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (coursesFragment != null) {
					coursesFragment.setButtonEnabled(true);
				}
			} else {
				if (coursesFragment != null) {
					coursesFragment.setButtonEnabled(false);
				}
				Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		FILES_DIR = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);

		if (Build.VERSION.SDK_INT >= 33) {
			if (ContextCompat.checkSelfPermission(this,
					"android.permission.READ_MEDIA_AUDIO")
					!= PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this,
						new String[]{"android.permission.READ_MEDIA_AUDIO"},
						1);
			}
		} else if (Build.VERSION.SDK_INT >= 23) {
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
		currentTheme = theme;
		if (theme.equals("Theme.Holo")) {
			setTheme(R.style.AppTheme_Dark);
		} else if (theme.equals("Theme.Holo.Light")) {
			setTheme(R.style.AppTheme_Light);
		} else {
			setTheme(R.style.AppTheme_LightDarkActionBar);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Initialize ViewModel
		viewModel = new ViewModelProvider(this).get(MainViewModel.class);

		// Apply window insets for edge-to-edge
		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
				findViewById(R.id.main_view), (view, windowInsets) -> {
					androidx.core.graphics.Insets insets = windowInsets.getInsets(
							androidx.core.view.WindowInsetsCompat.Type.systemBars());
					view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
					return androidx.core.view.WindowInsetsCompat.CONSUMED;
				});

		// Setup ViewPager2 with tabs
		viewPager = findViewById(R.id.viewPager);
		TabLayout tabLayout = findViewById(R.id.tabLayout);

		coursesFragment = new CoursesFragment();
		downloadFragment = new DownloadFragment();

		viewPager.setAdapter(new FragmentStateAdapter(this) {
			@NonNull
			@Override
			public Fragment createFragment(int position) {
				if (position == 0) {
					return coursesFragment;
				} else {
					return downloadFragment;
				}
			}

			@Override
			public int getItemCount() {
				return 2;
			}
		});

		new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
			if (position == 0) {
				tab.setText(R.string.tab_courses);
			} else {
				tab.setText(R.string.tab_download);
			}
		}).attach();

		// Observe ViewModel
		viewModel.getStatusMessage().observe(this, message -> {
			if (coursesFragment != null && message != null) {
				coursesFragment.setStatusText(message);
			}
		});

		viewModel.getIsDownloading().observe(this, downloading -> {
			if (coursesFragment != null) {
				coursesFragment.setButtonEnabled(!downloading);
			}
		});

		// Ads
		MobileAds.initialize(this, new OnInitializationCompleteListener() {
			@Override
			public void onInitializationComplete(InitializationStatus initializationStatus) {
			}
		});
		adView = findViewById(R.id.adView);
		AdRequest adRequest = new AdRequest.Builder().build();
		adView.loadAd(adRequest);
	}

	public void startDownload(String[] koza) {
		if (mTask != null) {
			return;
		}
		// Switch to download tab
		viewPager.setCurrentItem(1);

		mTask = new AsyncDownload(this);
		mTask.owner = this;
		mTask.execute(koza);
	}

	public CoursesFragment getCoursesFragment() {
		return coursesFragment;
	}

	public MainViewModel getViewModel() {
		return viewModel;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.optionmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
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
			BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}
			text = builder.toString();
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		textview1.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
		textview1.setMovementMethod(LinkMovementMethod.getInstance());

		Button dialogButton = (Button) dialog.findViewById(R.id.button1);
		dialogButton.setOnClickListener(v -> dialog.dismiss());

		dialog.show();
	}
}
