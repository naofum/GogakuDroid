package com.github.naofum.gogakudroid;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * Unit tests for NhkRepository.
 * Tests XML/JSON parsing and network error handling using MockWebServer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class NhkRepositoryTest {

    private MockWebServer mockWebServer;
    private NhkRepository repository;

    @Before
    public void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        OkHttpClient client = new OkHttpClient();
        repository = new NhkRepository(client);
    }

    @After
    public void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    // --- XML Parsing Tests ---

    @Test
    public void fetchFromUrl_xml_parsesMultipleEpisodes() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<musicdata>\n"
                + "  <music kouza=\"基礎英語\" hdate=\"2024_0805_0600\" "
                + "file=\"/path/to/stream\" nendo=\"2024\"/>\n"
                + "  <music kouza=\"基礎英語\" hdate=\"2024_0806_0600\" "
                + "file=\"/path/to/stream2\" nendo=\"2024\"/>\n"
                + "</musicdata>";

        mockWebServer.enqueue(new MockResponse().setBody(xml));
        String url = mockWebServer.url("/test.xml").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertTrue(result.isSuccess());
        assertNotNull(result.episodes);
        assertEquals(2, result.episodes.size());

        NhkRepository.Episode ep1 = result.episodes.get(0);
        assertEquals("基礎英語", ep1.kouza);
        assertEquals("2024_0805_0600", ep1.hdate);
        assertEquals("2024", ep1.nendo);
        assertTrue(ep1.streamUrl.contains("/path/to/stream"));
    }

    @Test
    public void fetchFromUrl_xml_emptyDocument() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<musicdata></musicdata>";

        mockWebServer.enqueue(new MockResponse().setBody(xml));
        String url = mockWebServer.url("/empty.xml").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertTrue(result.isSuccess());
        assertNotNull(result.episodes);
        assertEquals(0, result.episodes.size());
    }

    @Test
    public void fetchFromUrl_xml_singleEpisode() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<musicdata>\n"
                + "  <music kouza=\"ラジオ英会話\" hdate=\"6_05\" "
                + "file=\"/radio/stream\" nendo=\"2024\"/>\n"
                + "</musicdata>";

        mockWebServer.enqueue(new MockResponse().setBody(xml));
        String url = mockWebServer.url("/single.xml").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertTrue(result.isSuccess());
        assertEquals(1, result.episodes.size());
        assertEquals("ラジオ英会話", result.episodes.get(0).kouza);
        assertEquals("6_05", result.episodes.get(0).hdate);
    }

    @Test
    public void fetchFromUrl_xml_streamUrlContainsPrefix() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<musicdata>\n"
                + "  <music kouza=\"test\" hdate=\"date\" "
                + "file=\"/mypath\" nendo=\"2024\"/>\n"
                + "</musicdata>";

        mockWebServer.enqueue(new MockResponse().setBody(xml));
        String url = mockWebServer.url("/prefix.xml").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertTrue(result.isSuccess());
        NhkRepository.Episode ep = result.episodes.get(0);
        assertTrue(ep.streamUrl.endsWith("/mypath/index.m3u8"));
    }

    // --- JSON Parsing Tests ---

    @Test
    public void fetchFromUrl_json_parsesMultipleEpisodes() {
        String json = "{\n"
                + "  \"title\": \"基礎英語 レベル1\",\n"
                + "  \"episodes\": [\n"
                + "    {\"stream_url\": \"https://example.com/stream1.m3u8\", \"onair_date\": \"2024_0805\"},\n"
                + "    {\"stream_url\": \"https://example.com/stream2.m3u8\", \"onair_date\": \"2024_0806\"}\n"
                + "  ]\n"
                + "}";

        mockWebServer.enqueue(new MockResponse().setBody(json));
        String url = mockWebServer.url("/api").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertTrue(result.isSuccess());
        assertNotNull(result.episodes);
        assertEquals(2, result.episodes.size());

        NhkRepository.Episode ep1 = result.episodes.get(0);
        assertEquals("基礎英語_レベル1", ep1.kouza);
        assertEquals("2024_0805", ep1.hdate);
        assertEquals("https://example.com/stream1.m3u8", ep1.streamUrl);

        NhkRepository.Episode ep2 = result.episodes.get(1);
        assertEquals("2024_0806", ep2.hdate);
        assertEquals("https://example.com/stream2.m3u8", ep2.streamUrl);
    }

    @Test
    public void fetchFromUrl_json_titleSpacesReplacedWithUnderscore() {
        String json = "{\n"
                + "  \"title\": \"まいにち 中国語\",\n"
                + "  \"episodes\": [\n"
                + "    {\"stream_url\": \"https://example.com/s.m3u8\", \"onair_date\": \"2024_0801\"}\n"
                + "  ]\n"
                + "}";

        mockWebServer.enqueue(new MockResponse().setBody(json));
        String url = mockWebServer.url("/api").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertTrue(result.isSuccess());
        assertEquals("まいにち_中国語", result.episodes.get(0).kouza);
    }

    @Test
    public void fetchFromUrl_json_emptyEpisodeArray() {
        String json = "{\"title\": \"Test\", \"episodes\": []}";

        mockWebServer.enqueue(new MockResponse().setBody(json));
        String url = mockWebServer.url("/api").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertTrue(result.isSuccess());
        assertEquals(0, result.episodes.size());
    }

    @Test
    public void fetchFromUrl_json_nendoIsEmptyString() {
        String json = "{\n"
                + "  \"title\": \"Test Course\",\n"
                + "  \"episodes\": [\n"
                + "    {\"stream_url\": \"https://example.com/s.m3u8\", \"onair_date\": \"2024_0801\"}\n"
                + "  ]\n"
                + "}";

        mockWebServer.enqueue(new MockResponse().setBody(json));
        String url = mockWebServer.url("/api").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertTrue(result.isSuccess());
        assertEquals("", result.episodes.get(0).nendo);
    }

    // --- Network Error Tests ---

    @Test
    public void fetchFromUrl_serverError500_returnsConnectionError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        String url = mockWebServer.url("/error").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertFalse(result.isSuccess());
        assertEquals("connection_error", result.error);
    }

    @Test
    public void fetchFromUrl_notFound404_returnsConnectionError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));
        String url = mockWebServer.url("/notfound").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertFalse(result.isSuccess());
        assertEquals("connection_error", result.error);
    }

    @Test
    public void fetchFromUrl_invalidUrl_returnsConnectionError() {
        NhkRepository.FetchResult result = repository.fetchFromUrl("http://invalid.localhost:1/bad");

        assertFalse(result.isSuccess());
        assertEquals("connection_error", result.error);
    }

    // --- Parse Error Tests ---

    @Test
    public void fetchFromUrl_malformedXml_returnsParseError() {
        String badXml = "<musicdata><music kouza=\"test\"";

        mockWebServer.enqueue(new MockResponse().setBody(badXml));
        String url = mockWebServer.url("/bad.xml").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertFalse(result.isSuccess());
        assertEquals("parse_error", result.error);
    }

    @Test
    public void fetchFromUrl_malformedJson_returnsParseError() {
        String badJson = "{\"title\": broken}";

        mockWebServer.enqueue(new MockResponse().setBody(badJson));
        String url = mockWebServer.url("/bad.json").toString();

        NhkRepository.FetchResult result = repository.fetchFromUrl(url);

        assertFalse(result.isSuccess());
        assertEquals("parse_error", result.error);
    }

    // --- FetchResult Data Class Tests ---

    @Test
    public void fetchResult_success_hasEpisodesAndNoError() {
        List<NhkRepository.Episode> episodes = List.of(
                new NhkRepository.Episode("test", "date", "url", "2024"));
        NhkRepository.FetchResult result = NhkRepository.FetchResult.success(episodes);

        assertTrue(result.isSuccess());
        assertNull(result.error);
        assertEquals(1, result.episodes.size());
    }

    @Test
    public void fetchResult_failure_hasErrorAndNoEpisodes() {
        NhkRepository.FetchResult result = NhkRepository.FetchResult.failure("test_error");

        assertFalse(result.isSuccess());
        assertEquals("test_error", result.error);
        assertNull(result.episodes);
    }

    // --- Episode Data Class Tests ---

    @Test
    public void episode_holdsAllFields() {
        NhkRepository.Episode ep = new NhkRepository.Episode("kouza", "hdate", "stream", "nendo");

        assertEquals("kouza", ep.kouza);
        assertEquals("hdate", ep.hdate);
        assertEquals("stream", ep.streamUrl);
        assertEquals("nendo", ep.nendo);
    }
}
