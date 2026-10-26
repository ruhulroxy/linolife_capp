package co.linolife.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "LinoLifePushBridge")
public class LinoLifePushBridgePlugin extends Plugin {
    @Override
    public void load() {
        NotificationChannel channel = new NotificationChannel(
            "linolife_updates", "Lino Life updates", NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Booking reminders and account updates");
        getContext().getSystemService(NotificationManager.class).createNotificationChannel(channel);
        bridge.injectScriptBeforeLoad("native-push.js");
    }
}
