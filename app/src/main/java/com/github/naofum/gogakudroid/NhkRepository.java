/*
 * Copyright (C) 2014 Naofumi Fukue
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.github.naofum.gogakudroid;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Repository for fetching program data from the NHK radio on-demand API.
 * Handles HTTP communication and JSON parsing.
 */
public class NhkRepository {

    private static final String TAG = NhkRepository.class.getSimpleName();
    private static final String JSON_BASE_URL = "https://www.nhk.or.jp/radio-api/app/v1/web/ondemand/series";

    private final OkHttpClient client;

    public NhkRepository(OkHttpClient client) {
        this.client = client;
    }

    /**
     * Represents a single program episode.
     */
    public static class Episode {
        public final String kouza;
        public final String hdate;
        public final String streamUrl;
        public final String nendo;

        public Episode(String kouza, String hdate, String streamUrl, String nendo) {
            this.kouza = kouza;
            this.hdate = hdate;
            this.streamUrl = streamUrl;
            this.nendo = nendo;
        }
    }

    /**
     * Result of fetching episodes.
     */
    public static class FetchResult {
        public final List<Episode> episodes;
        public final String error;

        private FetchResult(List<Episode> episodes, String error) {
            this.episodes = episodes;
            this.error = error;
        }

        public static FetchResult success(List<Episode> episodes) {
            return new FetchResult(episodes, null);
        }

        public static FetchResult failure(String error) {
            return new FetchResult(null, error);
        }

        public boolean isSuccess() {
            return error == null;
        }
    }

    /**
     * Fetch episodes for the given course site ID.
     */
    public FetchResult fetchEpisodes(String koza) {
        String url = JSON_BASE_URL + "?site_id=" + koza + "&corner_site_id=01";
        return fetchFromUrl(url);
    }

    /**
     * Fetch episodes from a specific URL. Useful for testing with MockWebServer.
     */
    public FetchResult fetchFromUrl(String url) {

        String responseBody;
        try {
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                response.close();
                return FetchResult.failure("connection_error");
            }
            responseBody = response.body().string();
            response.close();
        } catch (Exception e) {
            Log.e(TAG, "Network error", e);
            return FetchResult.failure("connection_error");
        }

        Log.d(TAG, "Response: " + responseBody);

        if (responseBody == null || responseBody.trim().isEmpty()) {
            Log.e(TAG, "Empty response body");
            return FetchResult.failure("parse_error");
        }

        return parseJson(responseBody);
    }

    private FetchResult parseJson(String json) {
        List<Episode> episodes = new ArrayList<>();
        try {
            JSONObject obj = new JSONObject(json);
            String title = obj.getString("title").replaceAll(" ", "_");
            Log.d(TAG, "Course title: " + title);
            JSONArray detailList = obj.getJSONArray("episodes");
            for (int i = 0; i < detailList.length(); i++) {
                JSONObject ep = detailList.getJSONObject(i);
                String streamUrl = ep.getString("stream_url");
                String hdate = ep.getString("onair_date");
                episodes.add(new Episode(title, hdate, streamUrl, ""));
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON parse error", e);
            return FetchResult.failure("parse_error");
        }
        return FetchResult.success(episodes);
    }
}
