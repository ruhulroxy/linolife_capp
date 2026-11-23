package co.linolife.app;

import android.webkit.CookieManager;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "LinoLifeStartup")
public class LinoLifeStartupPlugin extends Plugin {
    @Override
    public void load() {
        bridge.injectScriptBeforeLoad("native-startup.js");
    }

    @PluginMethod
    public void ready(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (getActivity() instanceof MainActivity && ((MainActivity) getActivity()).isAppPage()) {
                // HttpOnly cookies stay in the OS store, never JS/Preferences.
                bridge.execute(() -> CookieManager.getInstance().flush());
                ((MainActivity) getActivity()).finishStartup();
                call.resolve();
            } else call.reject("Untrusted startup document");
        });
    }

    @PluginMethod
    public void unavailable(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (getActivity() instanceof MainActivity && ((MainActivity) getActivity()).isRecoveryDocument())
                ((MainActivity) getActivity()).showConnectionRecovery();
            call.resolve();
        });
    }

    @PluginMethod
    public void retry(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (getActivity() instanceof MainActivity && ((MainActivity) getActivity()).isRecoveryDocument())
                ((MainActivity) getActivity()).retryStartup();
            call.resolve();
        });
    }
}
