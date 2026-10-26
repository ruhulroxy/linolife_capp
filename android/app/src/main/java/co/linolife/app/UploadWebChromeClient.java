package co.linolife.app;

import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Keeps Capacitor's permission/result handling while accepting every form MIME type. */
public class UploadWebChromeClient extends BridgeWebChromeClient {
    public UploadWebChromeClient(Bridge bridge) {
        super(bridge);
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
        return super.onShowFileChooser(webView, callback, new UploadParams(params));
    }

    static class UploadParams extends FileChooserParams {
        private final FileChooserParams original;
        private final String[] types;

        UploadParams(FileChooserParams original) {
            this.original = original;
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
        public String[] getAcceptTypes() { return types.clone(); }

        @Override
        public int getMode() { return original.getMode(); }

        @Override
        public boolean isCaptureEnabled() { return original.isCaptureEnabled(); }

        @Override
        public CharSequence getTitle() { return original.getTitle(); }

        @Override
        public String getFilenameHint() { return original.getFilenameHint(); }
    }
}
