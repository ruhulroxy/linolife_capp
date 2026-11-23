package co.linolife.app;

import static org.junit.Assert.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31)
public class MainActivityStartupTest {
    @Test
    public void systemSplashHandsOffToTheCapacitorActivity() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertBridgeReady(scenario);
            scenario.recreate();
            assertBridgeReady(scenario);
        }
    }

    private void assertBridgeReady(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            assertFalse(activity.isFinishing());
            assertNotNull(activity.getBridge());
            assertNotNull(activity.getBridge().getWebView());
            assertTrue(activity.getBridge().getWebView().getSettings().getJavaScriptEnabled());
            assertTrue(android.webkit.CookieManager.getInstance().acceptCookie());
        });
    }

    @Test
    public void connectionRecoveryPreservesTheBridgeAndRetriesOnlyTheAppEntry() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.showConnectionRecovery();
                android.view.View cover = activity.findViewById(android.R.id.content).findViewWithTag("linolife-startup-cover");
                assertNotNull(cover);
                assertEquals(android.view.View.VISIBLE, cover.getVisibility());
                activity.retryStartup();
                assertNotNull(activity.getBridge().getWebView());
                assertEquals(android.view.View.VISIBLE, cover.getVisibility());
            });
        }
    }
}
