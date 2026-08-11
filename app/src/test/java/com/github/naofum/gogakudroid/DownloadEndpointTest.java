package com.github.naofum.gogakudroid;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Integration test: verifies that all configured program download URLs are reachable.
 * Requires network access. Tests may fail if NHK changes their API.
 *
 * Run with: ./gradlew testDebugUnitTest --tests "*.DownloadEndpointTest"
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class DownloadEndpointTest {

    private static final String XML_BASE_URL = "https://www.nhk.or.jp/gogaku/st/xml/";
    private static final String JSON_BASE_URL = "https://www.nhk.or.jp/radio-api/app/v1/web/ondemand/series";

    private OkHttpClient client;
    private NhkRepository repository;

    // Legacy XML courses
    private static final Map<String, String> ENGLISH = new LinkedHashMap<>();
    static {
        ENGLISH.put("english/basic0", "小学生の基礎英語");
        ENGLISH.put("english/basic1", "中学生の基礎英語_レベル1");
        ENGLISH.put("english/basic2", "中学生の基礎英語_レベル2");
        ENGLISH.put("english/basic3", "中高生の基礎英語_in_English");
        ENGLISH.put("english/kaiwa", "ラジオ英会話");
        ENGLISH.put("english/timetrial", "英会話タイムトライアル");
        ENGLISH.put("english/business1", "ラジオビジネス英語");
        ENGLISH.put("english/enjoy", "エンジョイ・シンプル・イングリッシュ");
        ENGLISH.put("italian/kouza", "まいにちイタリア語【初級編】");
        ENGLISH.put("italian/kouza2", "まいにちイタリア語【応用編】");
        ENGLISH.put("german/kouza", "まいにちドイツ語【初級編】");
        ENGLISH.put("german/kouza2", "まいにちドイツ語【応用編】");
        ENGLISH.put("french/kouza", "まいにちフランス語【初級編】");
        ENGLISH.put("french/kouza2", "まいにちフランス語【応用編】");
        ENGLISH.put("spanish/kouza", "まいにちスペイン語【初級編】");
        ENGLISH.put("spanish/kouza2", "まいにちスペイン語【応用編】");
        ENGLISH.put("russian/kouza", "まいにちロシア語【初級編】");
        ENGLISH.put("russian/kouza2", "まいにちロシア語【応用編】");
    }

    // JSON API courses
    private static final Map<String, String> MULTILINGUAL = new LinkedHashMap<>();
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
//        MULTILINGUAL.put("WKMNWGMN6R", "アラビア語講座");
        MULTILINGUAL.put("N13V9K157Y", "ポルトガル語");
//        MULTILINGUAL.put("4MY6Q8XP88", "Living_in_Japan");
//        MULTILINGUAL.put("6LPPKP6W8Q", "やさしい日本語");
    }

    @Before
    public void setUp() {
        client = new OkHttpClient();
        repository = new NhkRepository(client);
    }

    // --- Legacy XML endpoint tests ---

    @Test
    public void allLegacyXmlEndpoints_areReachable() {
        StringBuilder failures = new StringBuilder();

        for (Map.Entry<String, String> entry : ENGLISH.entrySet()) {
            String koza = entry.getKey();
            String name = entry.getValue();
            String url = XML_BASE_URL + koza + "/listdataflv.xml";

            try {
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                int code = response.code();
                response.close();

                if (code != 200) {
                    failures.append(String.format("  [%d] %s (%s)\n", code, name, url));
                }
            } catch (IOException e) {
                failures.append(String.format("  [ERR] %s (%s): %s\n", name, url, e.getMessage()));
            }
        }

        if (failures.length() > 0) {
            fail("Legacy XML endpoints failed:\n" + failures);
        }
    }

    // --- JSON API endpoint tests ---

    @Test
    public void allJsonApiEndpoints_areReachable() {
        StringBuilder failures = new StringBuilder();

        for (Map.Entry<String, String> entry : MULTILINGUAL.entrySet()) {
            String koza = entry.getKey();
            String name = entry.getValue();
            String url = JSON_BASE_URL + "?site_id=" + koza + "&corner_site_id=01";

            try {
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                int code = response.code();
                response.close();

                if (code != 200) {
                    failures.append(String.format("  [%d] %s (%s)\n", code, name, url));
                }
            } catch (IOException e) {
                failures.append(String.format("  [ERR] %s (%s): %s\n", name, url, e.getMessage()));
            }
        }

        if (failures.length() > 0) {
            fail("JSON API endpoints failed:\n" + failures);
        }
    }

    // --- Fetch and parse tests (actually fetches episodes) ---

    @Test
    public void allLegacyXmlEndpoints_returnValidEpisodes() {
        StringBuilder failures = new StringBuilder();

        for (Map.Entry<String, String> entry : ENGLISH.entrySet()) {
            String koza = entry.getKey();
            String name = entry.getValue();

            NhkRepository.FetchResult result = repository.fetchEpisodes(koza, true);

            if (!result.isSuccess()) {
                failures.append(String.format("  [%s] %s (%s)\n", result.error, name, koza));
            }
        }

        if (failures.length() > 0) {
            fail("Legacy XML fetch/parse failed:\n" + failures);
        }
    }

    @Test
    public void allJsonApiEndpoints_returnValidEpisodes() {
        StringBuilder failures = new StringBuilder();

        for (Map.Entry<String, String> entry : MULTILINGUAL.entrySet()) {
            String koza = entry.getKey();
            String name = entry.getValue();

            NhkRepository.FetchResult result = repository.fetchEpisodes(koza, false);

            if (!result.isSuccess()) {
                failures.append(String.format("  [%s] %s (%s)\n", result.error, name, koza));
            }
        }

        if (failures.length() > 0) {
            fail("JSON API fetch/parse failed:\n" + failures);
        }
    }

    // --- Stream URL validity tests ---

    @Test
    public void allJsonApiEndpoints_haveValidStreamUrls() {
        StringBuilder failures = new StringBuilder();

        for (Map.Entry<String, String> entry : MULTILINGUAL.entrySet()) {
            String koza = entry.getKey();
            String name = entry.getValue();

            NhkRepository.FetchResult result = repository.fetchEpisodes(koza, false);
            if (!result.isSuccess()) {
                failures.append(String.format("  [fetch failed] %s (%s)\n", name, koza));
                continue;
            }

            if (result.episodes.isEmpty()) {
                // No episodes available (out of broadcast period) — not a failure
                continue;
            }

            // Test first episode's stream URL
            NhkRepository.Episode ep = result.episodes.get(0);
            try {
                Request request = new Request.Builder().url(ep.streamUrl).build();
                Response response = client.newCall(request).execute();
                int code = response.code();
                response.close();

                if (code != 200) {
                    failures.append(String.format("  [%d] %s - stream: %s\n", code, name, ep.streamUrl));
                }
            } catch (IOException e) {
                failures.append(String.format("  [ERR] %s - stream: %s (%s)\n", name, ep.streamUrl, e.getMessage()));
            }
        }

        if (failures.length() > 0) {
            fail("Stream URLs not reachable:\n" + failures);
        }
    }
}
