package co.linolife.app;

import static org.junit.Assert.*;

import android.content.Intent;
import android.webkit.WebChromeClient.FileChooserParams;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UploadWebChromeClientTest {
    @Test
    public void imageUploadAllowsJpegPngAndWebp() {
        Intent intent = params(new String[] { "image/jpeg", "image/png", "image/webp" }, false).createIntent();
        assertEquals("image/*", intent.getType());
        assertArrayEquals(new String[] { "image/jpeg", "image/png", "image/webp" }, intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));
    }

    @Test
    public void identityUploadAllowsImagesAndPdf() {
        Intent intent = params(new String[] { "image/jpeg", "image/png", "application/pdf" }, false).createIntent();
        assertEquals("*/*", intent.getType());
        assertArrayEquals(new String[] { "image/jpeg", "image/png", "application/pdf" }, intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));
    }

    @Test
    public void normalizesCombinedAcceptTypesAndExtensions() {
        FileChooserParams params = params(new String[] { " .jpg, image/png ; image/jpeg ", "", null }, false);
        assertArrayEquals(new String[] { "image/jpeg", "image/png" }, params.getAcceptTypes());
        assertEquals("image/*", params.createIntent().getType());
    }

    @Test
    public void emptyAcceptAndMultipleSelectionStillWork() {
        Intent intent = params(new String[] { "" }, true).createIntent();
        assertEquals("*/*", intent.getType());
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertEquals(Intent.ACTION_GET_CONTENT, intent.getAction());
    }

    @Test
    public void singleSelectionAndCaptureArePreserved() {
        FileChooserParams params = params(new String[] { "image/*" }, false);
        assertTrue(params.isCaptureEnabled());
        assertFalse(params.createIntent().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true));
        assertEquals("Upload", params.getTitle());
        assertEquals("photo.jpg", params.getFilenameHint());
    }

    private FileChooserParams params(String[] types, boolean multiple) {
        return new UploadWebChromeClient.UploadParams(new FileChooserParams() {
            @Override public int getMode() { return multiple ? MODE_OPEN_MULTIPLE : MODE_OPEN; }
            @Override public String[] getAcceptTypes() { return types; }
            @Override public boolean isCaptureEnabled() { return true; }
            @Override public CharSequence getTitle() { return "Upload"; }
            @Override public String getFilenameHint() { return "photo.jpg"; }
            @Override public Intent createIntent() { return new Intent(Intent.ACTION_GET_CONTENT).setType("image/jpeg"); }
        });
    }
}
