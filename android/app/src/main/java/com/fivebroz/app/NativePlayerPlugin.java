package com.fivebroz.app;

import android.content.Intent;

import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Bridges the web app to a native ExoPlayer screen.
 * The web site calls: window.Capacitor.Plugins.NativePlayer.play({ url, title, subs })
 * subs = [{ url, label, lang }]
 */
@CapacitorPlugin(name = "NativePlayer")
public class NativePlayerPlugin extends Plugin {

    @PluginMethod
    public void play(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.trim().isEmpty()) {
            call.reject("missing url");
            return;
        }
        String title = call.getString("title", "");

        Intent intent = new Intent(getContext(), PlayerActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("title", title);

        JSArray subs = call.getArray("subs");
        if (subs != null) {
            intent.putExtra("subs", subs.toString());
        }

        getActivity().startActivity(intent);
        call.resolve();
    }
}
