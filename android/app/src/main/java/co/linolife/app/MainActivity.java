package co.linolife.app;

import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.core.splashscreen.SplashScreen;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    private final Handler startupHandler = new Handler(Looper.getMainLooper());
    private FrameLayout startupCover;
    private LinearLayout recovery;
    private final Runnable connectionTimeout = this::showConnectionRecovery;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreen.installSplashScreen(this).setOnExitAnimationListener(provider -> provider.remove());
        }
        registerPlugin(LinoLifePushBridgePlugin.class);
        registerPlugin(LinoLifeStartupPlugin.class);
        super.onCreate(savedInstanceState);
        if (bridge == null) return;
        CookieManager.getInstance().setAcceptCookie(true);
        bridge.getWebView().setWebChromeClient(new UploadWebChromeClient(bridge));
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                bridge.execute(() -> CookieManager.getInstance().flush());
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) showConnectionRecovery();
            }
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                // Preserve useful 4xx/permission pages instead of treating them as offline.
                if (response.getStatusCode() < 500) return;
                super.onReceivedHttpError(view, request, response);
                if (request.isForMainFrame() && response.getStatusCode() >= 500) showConnectionRecovery();
            }
        });
        showStartupCover();
    }

    boolean isAppPage() {
        if (bridge == null || bridge.getWebView().getUrl() == null) return false;
        Uri entry = Uri.parse(bridge.getServerUrl());
        Uri page = Uri.parse(bridge.getWebView().getUrl());
        String path = entry.getPath().replaceAll("/+$", "");
        return "https".equals(page.getScheme()) && entry.getHost().equals(page.getHost()) &&
            entry.getPort() == page.getPort() && (path.equals(page.getPath()) || page.getPath().startsWith(path + "/"));
    }

    boolean isRecoveryDocument() {
        return bridge != null && bridge.getErrorUrl() != null &&
            bridge.getErrorUrl().equals(bridge.getWebView().getUrl());
    }

    private void showStartupCover() {
        if (startupCover == null) {
            startupCover = new FrameLayout(this);
            startupCover.setBackgroundResource(R.drawable.splash_screen);
            startupCover.setClickable(true);
            startupCover.setTag("linolife-startup-cover");
            recovery = new LinearLayout(this);
            recovery.setOrientation(LinearLayout.VERTICAL);
            recovery.setGravity(android.view.Gravity.CENTER);
            int padding = (int) (24 * getResources().getDisplayMetrics().density);
            recovery.setPadding(padding, padding, padding, padding);
            TextView message = new TextView(this);
            message.setText("Unable to connect. Your sign-in is kept on this device.");
            message.setTextColor(android.graphics.Color.BLACK);
            message.setGravity(android.view.Gravity.CENTER);
            Button retry = new Button(this);
            retry.setText("Try again");
            retry.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(253, 46, 137)));
            retry.setTextColor(android.graphics.Color.BLACK);
            retry.setOnClickListener(view -> retryStartup());
            recovery.addView(message);
            recovery.addView(retry);
            FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER);
            position.topMargin = (int) (170 * getResources().getDisplayMetrics().density);
            startupCover.addView(recovery, position);
            addContentView(startupCover, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        startupCover.animate().cancel();
        startupCover.setAlpha(1);
        startupCover.setVisibility(View.VISIBLE);
        recovery.setVisibility(View.GONE);
        startupHandler.removeCallbacks(connectionTimeout);
        startupHandler.postDelayed(connectionTimeout, 20000);
    }

    void finishStartup() {
        startupHandler.removeCallbacks(connectionTimeout);
        if (startupCover == null) return;
        final FrameLayout cover = startupCover;
        cover.animate().alpha(0).setDuration(200).withEndAction(() -> cover.setVisibility(View.GONE)).start();
    }

    void showConnectionRecovery() {
        if (isFinishing() || isDestroyed()) return;
        if (startupCover == null) showStartupCover();
        startupHandler.removeCallbacks(connectionTimeout);
        startupCover.animate().cancel();
        startupCover.setAlpha(1);
        startupCover.setVisibility(View.VISIBLE);
        recovery.setVisibility(View.VISIBLE);
    }

    void retryStartup() {
        if (bridge == null) return;
        showStartupCover();
        // Only a safe GET to this surface's entry: never replay a payment POST.
        bridge.getWebView().stopLoading();
        bridge.getWebView().loadUrl(bridge.getServerUrl());
    }

    @Override
    public void onPause() {
        if (bridge != null) bridge.execute(() -> CookieManager.getInstance().flush());
        super.onPause();
    }

    @Override
    public void onDestroy() {
        startupHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
