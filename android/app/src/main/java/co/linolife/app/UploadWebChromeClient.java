package co.linolife.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Offers photo sources while keeping Capacitor's permission and result handling. */
public class UploadWebChromeClient extends BridgeWebChromeClient {
    private final Bridge bridge;

    public UploadWebChromeClient(Bridge bridge) {
        super(bridge);
        this.bridge = bridge;
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
        UploadParams upload = new UploadParams(params);
        if (!upload.offersPhotoCapture()) {
            return super.onShowFileChooser(webView, callback, upload);
        }

        Activity activity = bridge.getActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            callback.onReceiveValue(null);
            return true;
        }
        if (new Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(activity.getPackageManager()) == null) {
            return super.onShowFileChooser(webView, callback, upload.withSource(Source.FILES));
        }

        CharSequence[] options = {
            activity.getText(R.string.upload_take_photo),
            activity.getText(R.string.upload_choose_from_gallery)
        };
        new AlertDialog.Builder(activity)
            .setTitle(R.string.upload_photo_source)
            .setItems(options, (dialog, choice) -> {
                Source source = choice == 0 ? Source.CAMERA : Source.FILES;
                super.onShowFileChooser(webView, callback, upload.withSource(source));
            })
            .setNegativeButton(android.R.string.cancel, (dialog, choice) -> callback.onReceiveValue(null))
            .setOnCancelListener(dialog -> callback.onReceiveValue(null))
            .show();
        return true;
    }

    enum Source { INPUT, CAMERA, FILES }

    static class UploadParams extends FileChooserParams {
        private final FileChooserParams original;
        private final String[] types;
        private final Source source;

        UploadParams(FileChooserParams original) {
            this.original = original;
            source = Source.INPUT;
            Set<String> accepted = new LinkedHashSet<>();
            for (String entry : original.getAcceptTypes()) {
                if (entry == null) continue;
                for (String value : entry.split("[,;]")) {
                    String type = value.trim().toLowerCase(Locale.ROOT);
                    if (type.startsWith(".")) {
                        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.substring(1));
                    }
                    if (type != null && type.contains("/")) accepted.add(type);
                }
            }
            types = accepted.isEmpty() ? new String[] { "*/*" } : accepted.toArray(new String[0]);
        }

        private UploadParams(FileChooserParams original, String[] types, Source source) {
            this.original = original;
            this.types = types;
            this.source = source;
        }

        UploadParams withSource(Source source) {
            return new UploadParams(original, types, source);
        }

        boolean offersPhotoCapture() {
            if (getMode() != MODE_OPEN && getMode() != MODE_OPEN_MULTIPLE) return false;

            // Capacitor captures JPEG. Preserve explicitly requested video capture.
            var accepted = Arrays.asList(types);
            if (original.isCaptureEnabled() && accepted.contains("video/*")) return false;
            return accepted.contains("image/*") || accepted.contains("image/jpeg");
        }

        @Override
        public Intent createIntent() {
            Intent intent = original.createIntent();
            // Some pickers intersect setType with EXTRA_MIME_TYPES. Keeping the first
            // type (e.g. image/jpeg) hides PNG/WebP and PDFs in mixed upload fields.
            intent.setType(commonType());
            intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, getMode() == MODE_OPEN_MULTIPLE);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            return intent;
        }

        private String commonType() {
            if (types.length == 1) return types[0];
            String category = types[0].substring(0, types[0].indexOf('/'));
            for (String type : types) {
                if (!type.startsWith(category + "/")) return "*/*";
            }
            return category + "/*";
        }

        @Override
        public String[] getAcceptTypes() {
            // Capacitor uses this wildcard for capture. createIntent keeps the
            // original MIME filter if camera capture falls back to files.
            return source == Source.CAMERA ? new String[] { "image/*" } : types.clone();
        }

        @Override
        public int getMode() { return original.getMode(); }

        @Override
        public boolean isCaptureEnabled() {
            return source == Source.CAMERA || (source == Source.INPUT && original.isCaptureEnabled());
        }

        @Override
        public CharSequence getTitle() { return original.getTitle(); }

        @Override
        public String getFilenameHint() { return original.getFilenameHint(); }
    }
}
