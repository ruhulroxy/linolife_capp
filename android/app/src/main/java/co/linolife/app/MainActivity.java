package co.linolife.app;

import android.os.Bundle;
import android.os.Build;
import androidx.core.splashscreen.SplashScreen;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Apply the post-splash AppCompat theme before Activity initialization.
            // The system splash must not keep animating after the first app frame.
            SplashScreen.installSplashScreen(this).setOnExitAnimationListener(provider -> provider.remove());
        }
        registerPlugin(LinoLifePushBridgePlugin.class);
        super.onCreate(savedInstanceState);
        if (bridge != null) {
            bridge.getWebView().setWebChromeClient(new UploadWebChromeClient(bridge));
        }
    }
}
