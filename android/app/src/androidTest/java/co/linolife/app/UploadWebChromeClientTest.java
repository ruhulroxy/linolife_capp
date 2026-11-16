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

    @Test
    public void ordinaryPhotoUploadOffersCameraAndGallery() {
        var upload = params(new String[] { "image/jpeg", "image/png", "image/webp" }, false, false);
        assertTrue(upload.offersPhotoCapture());

        var camera = upload.withSource(UploadWebChromeClient.Source.CAMERA);
        assertTrue(camera.isCaptureEnabled());
        assertArrayEquals(new String[] { "image/*" }, camera.getAcceptTypes());
        assertArrayEquals(upload.getAcceptTypes(), camera.createIntent().getStringArrayExtra(Intent.EXTRA_MIME_TYPES));

        var files = upload.withSource(UploadWebChromeClient.Source.FILES);
        assertFalse(files.isCaptureEnabled());
        assertArrayEquals(upload.getAcceptTypes(), files.getAcceptTypes());
    }

    @Test
    public void galleryKeepsDocumentTypesAndMultipleSelection() {
        var upload = params(new String[] { ".jpg", "application/pdf" }, true);
        assertTrue(upload.offersPhotoCapture());

        var files = upload.withSource(UploadWebChromeClient.Source.FILES);
        assertFalse(files.isCaptureEnabled());
        assertArrayEquals(new String[] { "image/jpeg", "application/pdf" }, files.getAcceptTypes());
        assertTrue(files.createIntent().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
    }

    @Test
    public void photoCaptureRequiresAnInputThatAcceptsJpeg() {
        assertTrue(params(new String[] { "image/*" }, false, false).offersPhotoCapture());
        assertFalse(params(new String[] { "application/pdf" }, false, false).offersPhotoCapture());
        assertFalse(params(new String[] { "image/png" }, false, false).offersPhotoCapture());
        assertFalse(params(new String[] { "image/*", "video/*" }, false).offersPhotoCapture());
    }

    private UploadWebChromeClient.UploadParams params(String[] types, boolean multiple) {
        return params(types, multiple, true);
    }

    private UploadWebChromeClient.UploadParams params(String[] types, boolean multiple, boolean capture) {
        return new UploadWebChromeClient.UploadParams(new FileChooserParams() {
            @Override public int getMode() { return multiple ? MODE_OPEN_MULTIPLE : MODE_OPEN; }
            @Override public String[] getAcceptTypes() { return types; }
            @Override public boolean isCaptureEnabled() { return capture; }
            @Override public CharSequence getTitle() { return "Upload"; }
            @Override public String getFilenameHint() { return "photo.jpg"; }
            @Override public Intent createIntent() { return new Intent(Intent.ACTION_GET_CONTENT).setType("image/jpeg"); }
        });
    }
}
