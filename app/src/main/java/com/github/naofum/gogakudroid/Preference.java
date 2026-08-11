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

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class Preference extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String theme = PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "AppBaseTheme");
        if (theme.equals("Theme.Holo")) {
            setTheme(R.style.AppTheme_Dark);
        } else if (theme.equals("Theme.Holo.Light")) {
            setTheme(R.style.AppTheme_Light);
        } else {
            setTheme(R.style.AppTheme_LightDarkActionBar);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preference);
        setTitle(R.string.title_activity_preference);

        // Apply window insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.preference_container), (view, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_content, new PreferenceFragment())
                    .commit();
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class PreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            findPreference("theme").setOnPreferenceChangeListener((preference, newValue) -> {
                requireActivity().recreate();
                return true;
            });
        }
    }
}
