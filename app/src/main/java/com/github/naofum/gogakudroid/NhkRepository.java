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
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Repository for fetching program data from NHK APIs.
 * Handles HTTP communication and XML/JSON parsing.
 */
public class NhkRepository {

    private static final String TAG = NhkRepository.class.getSimpleName();
    private static final String XML_BASE_URL = "https://www.nhk.or.jp/gogaku/st/xml/";
    private static final String JSON_BASE_URL = "https://www.nhk.or.jp/radio-api/app/v1/web/ondemand/series";
    private static final String STREAM_PREFIX = "https://www.nhk.or.jp/radio-api/app/v1/web/ondemand/series";

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
     * Fetch episodes for the given course ID.
     * Determines XML or JSON API based on whether the ID is in the legacy ENGLISH map.
     */
    public FetchResult fetchEpisodes(String koza, boolean isLegacy) {
        String url;
        if (isLegacy) {
            url = XML_BASE_URL + koza + "/listdataflv.xml";
        } else {
            url = JSON_BASE_URL + "?site_id=" + koza + "&corner_site_id=01";
        }
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

        if (responseBody.charAt(0) == '<') {
            return parseXml(responseBody);
        } else {
            return parseJson(responseBody);
        }
    }

    private FetchResult parseXml(String xml) {
        List<Episode> episodes = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.getName().equals("music")) {
                    String kouza = parser.getAttributeValue(null, "kouza");
                    String hdate = parser.getAttributeValue(null, "hdate");
                    String file = parser.getAttributeValue(null, "file");
                    String nendo = parser.getAttributeValue(null, "nendo");
                    String streamUrl = STREAM_PREFIX + file + "/index.m3u8";
                    episodes.add(new Episode(kouza, hdate, streamUrl, nendo));
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "XML parse error", e);
            return FetchResult.failure("parse_error");
        }
        return FetchResult.success(episodes);
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
