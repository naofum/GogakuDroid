package com.github.naofum.gogakudroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Network smoke test: downloads a real course (小学生の基礎英語) end-to-end.
 * Fetches episodes, resolves the HLS playlists, downloads every audio segment,
 * decrypts them (AES-128-CBC) and merges them in order into a single AAC file.
 * Each segment starts with an ID3 tag followed by ADTS AAC frames.
 *
 * Requires network access. Run with:
 *   ./gradlew testDebugUnitTest -PnetworkTests --tests "*BasicEnglishDownloadTest"
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
@Category(NetworkTest.class)
public class BasicEnglishDownloadTest {

    // 小学生の基礎英語 (JSON API site_id)
    private static final String SITE_ID = "GGQY3M1929";

    private OkHttpClient client;
    private NhkRepository repository;

    @Before
    public void setUp() {
        client = new OkHttpClient();
        repository = new NhkRepository(client);
    }

    @Test
    public void basicEnglish_downloadAllSegments_mergeAndSave() throws Exception {
        NhkRepository.FetchResult result = repository.fetchEpisodes(SITE_ID);
        assertTrue("Failed to fetch episodes: " + result.error, result.isSuccess());
        assertFalse("No episodes available for " + SITE_ID, result.episodes.isEmpty());

        NhkRepository.Episode episode = result.episodes.get(0);

        // 1. Master playlist -> variant playlist URL
        String masterM3u8 = downloadText(episode.streamUrl);
        assertNotNull("Master playlist is empty: " + episode.streamUrl, masterM3u8);
        assertTrue("Not a master playlist: " + episode.streamUrl, masterM3u8.contains("#EXTM3U"));

        String variantUrl = resolveMediaUrl(episode.streamUrl, masterM3u8);
        assertNotNull("No variant playlist referenced by " + episode.streamUrl, variantUrl);

        // 2. Variant playlist -> segments + AES key
        String variantM3u8 = downloadText(variantUrl);
        assertNotNull("Variant playlist is empty: " + variantUrl, variantM3u8);
        assertTrue("Not a media playlist: " + variantUrl, variantM3u8.contains("#EXTM3U"));

        List<String> segmentUrls = resolveAllSegments(variantUrl, variantM3u8);
        assertFalse("No segments in variant playlist: " + variantUrl, segmentUrls.isEmpty());

        long mediaSequence = parseMediaSequence(variantM3u8);
        String keyUrl = parseKeyUri(variantM3u8, variantUrl);
        assertNotNull("No AES key in variant playlist: " + variantUrl, keyUrl);

        byte[] key = downloadBinary(keyUrl);
        assertEquals("AES key must be 16 bytes", 16, key.length);

        byte[] explicitIv = parseExplicitIv(variantM3u8);

        // 3. Download, decrypt and merge every segment in order
        ByteArrayOutputStream merged = new ByteArrayOutputStream();
        for (int i = 0; i < segmentUrls.size(); i++) {
            byte[] encrypted = downloadBinary(segmentUrls.get(i));
            assertTrue("Empty segment: " + segmentUrls.get(i), encrypted.length > 0);

            byte[] iv = explicitIv != null ? explicitIv : buildIv(mediaSequence + i);
            byte[] decrypted = decryptAes128Cbc(key, iv, encrypted);

            if (i == 0) {
                assertTrue("Decrypted segment does not start with an ID3 tag (wrong key/IV?)",
                        decrypted.length > 3
                                && (decrypted[0] & 0xFF) == 'I'
                                && (decrypted[1] & 0xFF) == 'D'
                                && (decrypted[2] & 0xFF) == '3');
            }
            merged.write(decrypted);
        }
        byte[] output = merged.toByteArray();
        assertTrue("Merged output is empty", output.length > 0);

        // 4. Save the merged output to a file
        File outDir = new File(System.getProperty("java.io.tmpdir"), "gogakudroid");
        if (!outDir.exists()) {
            assertTrue("Cannot create output directory", outDir.mkdirs());
        }
        File outFile = new File(outDir, "basic_english_" + episode.kouza + ".aac");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(output);
        }

        assertTrue("Output file was not created", outFile.exists());
        assertTrue("Output file is empty", outFile.length() > 0);
        assertEquals("Merged size does not match output file size", (long) output.length, outFile.length());
    }

    private String resolveMediaUrl(String playlistUrl, String playlist) {
        String base = playlistUrl.substring(0, playlistUrl.lastIndexOf('/') + 1);
        for (String line : playlist.split("[\r\n]+")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            return trimmed.startsWith("http") ? trimmed : base + trimmed;
        }
        return null;
    }

    private List<String> resolveAllSegments(String playlistUrl, String playlist) {
        List<String> segmentUrls = new ArrayList<>();
        String base = playlistUrl.substring(0, playlistUrl.lastIndexOf('/') + 1);
        for (String line : playlist.split("[\r\n]+")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            segmentUrls.add(trimmed.startsWith("http") ? trimmed : base + trimmed);
        }
        return segmentUrls;
    }

    private long parseMediaSequence(String playlist) {
        for (String line : playlist.split("[\r\n]+")) {
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                return Long.parseLong(line.substring("#EXT-X-MEDIA-SEQUENCE:".length()).trim());
            }
        }
        return 0;
    }

    private String parseKeyUri(String playlist, String playlistUrl) {
        for (String line : playlist.split("[\r\n]+")) {
            if (line.startsWith("#EXT-X-KEY:")) {
                String uri = extractAttributeValue(line, "URI");
                if (uri == null) {
                    return null;
                }
                if (uri.startsWith("http")) {
                    return uri;
                }
                String base = playlistUrl.substring(0, playlistUrl.lastIndexOf('/') + 1);
                return base + uri;
            }
        }
        return null;
    }

    private byte[] parseExplicitIv(String playlist) {
        for (String line : playlist.split("[\r\n]+")) {
            if (line.startsWith("#EXT-X-KEY:")) {
                String iv = extractAttributeValue(line, "IV");
                if (iv != null) {
                    String hex = iv.startsWith("0x") || iv.startsWith("0X") ? iv.substring(2) : iv;
                    return hexToBytes(hex);
                }
            }
        }
        return null;
    }

    private String extractAttributeValue(String line, String attribute) {
        String prefix = attribute + "=";
        int idx = line.indexOf(prefix);
        if (idx < 0) {
            return null;
        }
        int start = idx + prefix.length();
        if (start >= line.length()) {
            return null;
        }
        if (line.charAt(start) == '"') {
            int end = line.indexOf('"', start + 1);
            return end < 0 ? null : line.substring(start + 1, end);
        }
        int end = line.indexOf(',', start);
        return line.substring(start, end < 0 ? line.length() : end);
    }

    private byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4)
                    + Character.digit(hex.charAt(i * 2 + 1), 16));
        }
        return data;
    }

    private byte[] buildIv(long sequence) {
        byte[] iv = new byte[16];
        for (int i = 0; i < 8; i++) {
            iv[15 - i] = (byte) ((sequence >>> (8 * i)) & 0xFF);
        }
        return iv;
    }

    private byte[] decryptAes128Cbc(byte[] key, byte[] iv, byte[] encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(encrypted);
    }

    private String downloadText(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            assertTrue("HTTP " + response.code() + " for " + url, response.isSuccessful());
            return response.body() != null ? response.body().string() : null;
        }
    }

    private byte[] downloadBinary(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            assertTrue("HTTP " + response.code() + " for " + url, response.isSuccessful());
            return response.body() != null ? response.body().bytes() : null;
        }
    }
}
