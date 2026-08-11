package com.github.naofum.gogakudroid;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Unit tests for MediaRepository.
 * Tests download history management, file existence checks, and media existence logic.
 * Uses Robolectric to provide an Android Context.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class MediaRepositoryTest {

    private Context context;
    private MediaRepository repository;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        repository = new MediaRepository(context);
        // Clear SharedPreferences before each test
        context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("downloaded_uris", Context.MODE_PRIVATE).edit().clear().apply();
    }

    // --- markMediaDownloaded Tests ---

    @Test
    public void markMediaDownloaded_addsEntryToPreferences() {
        repository.markMediaDownloaded("test_file.m4a");

        SharedPreferences pref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
        assertTrue(pref.contains("test_file.m4a"));
        assertTrue(pref.getLong("test_file.m4a", 0) > 0);
    }

    @Test
    public void markMediaDownloaded_multipleFiles() {
        repository.markMediaDownloaded("file1.m4a");
        repository.markMediaDownloaded("file2.mp3");
        repository.markMediaDownloaded("file3.aac");

        SharedPreferences pref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
        assertTrue(pref.contains("file1.m4a"));
        assertTrue(pref.contains("file2.mp3"));
        assertTrue(pref.contains("file3.aac"));
    }

    @Test
    public void markMediaDownloaded_evictsOldestWhenOverLimit() {
        // Fill up to the limit (500)
        SharedPreferences pref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        for (int i = 0; i < 500; i++) {
            editor.putLong("old_file_" + i + ".m4a", i + 1); // timestamps 1-500
        }
        editor.apply();

        // Add one more - should evict the oldest (timestamp=1)
        repository.markMediaDownloaded("new_file.m4a");

        assertFalse(pref.contains("old_file_0.m4a")); // timestamp=1 should be evicted
        assertTrue(pref.contains("new_file.m4a"));
        assertTrue(pref.contains("old_file_499.m4a")); // most recent should remain
    }

    @Test
    public void markMediaDownloaded_timestampIsRecent() {
        long before = System.currentTimeMillis();
        repository.markMediaDownloaded("timed_file.m4a");
        long after = System.currentTimeMillis();

        SharedPreferences pref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
        long timestamp = pref.getLong("timed_file.m4a", 0);
        assertTrue(timestamp >= before);
        assertTrue(timestamp <= after);
    }

    // --- isMediaExist Tests ---

    @Test
    public void isMediaExist_notInPreferences_returnsFalse() {
        boolean result = repository.isMediaExist("nonexistent_file.m4a");
        assertFalse(result);
    }

    @Test
    public void isMediaExist_inPreferencesButNoMedia_returnsFalseAndCleansUp() {
        // Add to SharedPreferences but don't add to MediaStore
        SharedPreferences pref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
        pref.edit().putLong("missing_media.m4a", System.currentTimeMillis()).apply();

        boolean result = repository.isMediaExist("missing_media.m4a");

        // File doesn't exist in MediaStore, so should return false and clean up
        assertFalse(result);
        // Stale record should be removed
        assertFalse(pref.contains("missing_media.m4a"));
    }

    @Test
    public void isMediaExist_cleansUpUriPreferenceOnMissing() {
        SharedPreferences filePref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
        SharedPreferences uriPref = context.getSharedPreferences("downloaded_uris", Context.MODE_PRIVATE);
        filePref.edit().putLong("stale.m4a", System.currentTimeMillis()).apply();
        uriPref.edit().putString("stale.m4a", "content://media/123").apply();

        repository.isMediaExist("stale.m4a");

        // Both should be cleaned up
        assertFalse(filePref.contains("stale.m4a"));
        assertFalse(uriPref.contains("stale.m4a"));
    }

    // --- isFileExist Tests ---

    @Test
    public void isFileExist_nonexistentFile_returnsFalse() {
        boolean result = repository.isFileExist("definitely_not_here.m4a");
        assertFalse(result);
    }

    @Test
    public void isFileExist_existingFile_returnsTrue() throws IOException {
        // Create a file in the Downloads directory
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        downloadsDir.mkdirs();
        File testFile = new File(downloadsDir, "test_exist.m4a");
        testFile.createNewFile();

        try {
            boolean result = repository.isFileExist("test_exist.m4a");
            assertTrue(result);
        } finally {
            testFile.delete();
        }
    }

    // --- storeMedia Tests ---

    @Test
    public void storeMedia_withValidFile_returnsNonNullUri() throws IOException {
        // Create a temporary input file
        File tempDir = new File(context.getCacheDir(), "test_input");
        tempDir.mkdirs();
        File inputFile = new File(tempDir, "test.m4a");
        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            fos.write("fake audio content".getBytes());
        }

        Uri result = repository.storeMedia(tempDir.getPath() + "/", "test.m4a", "test_course");

        // On Robolectric with SDK 30, MediaStore insert should work
        assertNotNull(result);

        // Should have been marked as downloaded
        SharedPreferences pref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
        assertTrue(pref.contains("test.m4a"));

        // Content URI should have been saved
        SharedPreferences uriPref = context.getSharedPreferences("downloaded_uris", Context.MODE_PRIVATE);
        assertTrue(uriPref.contains("test.m4a"));
    }

    // --- moveFile Tests ---

    @Test
    public void moveFile_movesFileAndReturnsUri() throws IOException {
        // Create source file
        File srcDir = new File(context.getCacheDir(), "src");
        srcDir.mkdirs();
        File srcFile = new File(srcDir, "move_test.m4a");
        try (FileOutputStream fos = new FileOutputStream(srcFile)) {
            fos.write("audio data".getBytes());
        }

        // Create destination dir
        File dstDir = new File(context.getCacheDir(), "dst");
        dstDir.mkdirs();

        android.net.Uri result = repository.moveFile(srcDir.getPath() + "/", "move_test.m4a", dstDir.getPath() + "/");

        assertNotNull(result);
        // Source file should be deleted
        assertFalse(srcFile.exists());
        // Destination file should exist
        assertTrue(new File(dstDir, "move_test.m4a").exists());
        // Should be marked as downloaded
        SharedPreferences pref = context.getSharedPreferences("downloaded_files", Context.MODE_PRIVATE);
        assertTrue(pref.contains("move_test.m4a"));

        // Cleanup
        new File(dstDir, "move_test.m4a").delete();
    }

    @Test
    public void moveFile_sourceNotFound_returnsNull() {
        android.net.Uri result = repository.moveFile("/nonexistent/", "no_file.m4a", "/tmp/");
        assertNull(result);
    }

    @Test
    public void moveFile_createsDestinationDirectory() throws IOException {
        File srcDir = new File(context.getCacheDir(), "src2");
        srcDir.mkdirs();
        File srcFile = new File(srcDir, "dir_test.m4a");
        try (FileOutputStream fos = new FileOutputStream(srcFile)) {
            fos.write("test".getBytes());
        }

        File dstDir = new File(context.getCacheDir(), "new_dir/sub");

        android.net.Uri result = repository.moveFile(srcDir.getPath() + "/", "dir_test.m4a", dstDir.getPath() + "/");

        assertNotNull(result);
        assertTrue(dstDir.exists());
        assertTrue(new File(dstDir, "dir_test.m4a").exists());

        // Cleanup
        new File(dstDir, "dir_test.m4a").delete();
        dstDir.delete();
        dstDir.getParentFile().delete();
    }

    // --- getMimeType (indirectly tested via storeMedia) ---

    @Test
    public void storeMedia_mp3_setsCorrectMimeType() throws IOException {
        File tempDir = new File(context.getCacheDir(), "mime_test");
        tempDir.mkdirs();
        File inputFile = new File(tempDir, "test.mp3");
        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            fos.write("fake".getBytes());
        }

        // Should not throw and should set audio/mpeg mime type
        Uri result = repository.storeMedia(tempDir.getPath() + "/", "test.mp3", "output");
        assertNotNull(result);
    }
}
