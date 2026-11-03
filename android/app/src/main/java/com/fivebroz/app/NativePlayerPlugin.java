package com.fivebroz.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bridges the web app to native playback:
 *  - play()         -> built-in ExoPlayer (any codec, in-app)
 *  - openExternal() -> hand the stream to VLC / MX Player / any installed player
 *  - appInfo()      -> installed version, for the in-app update check
 *  - openUrl()      -> open a URL (e.g. the APK download) in the system browser
 * Called from the site via window.Capacitor.Plugins.NativePlayer.*
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
        Intent intent = new Intent(getContext(), PlayerActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("title", call.getString("title", ""));
        JSArray subs = call.getArray("subs");
        if (subs != null) intent.putExtra("subs", subs.toString());
        getActivity().startActivity(intent);
        call.resolve();
    }

    /** Hand the stream to an external player via an Intent chooser. */
    @PluginMethod
    public void openExternal(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.trim().isEmpty()) {
            call.reject("missing url");
            return;
        }
        String title = call.getString("title", "");

        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndTypeAndNormalize(Uri.parse(url), "video/*");
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Common extras understood by VLC / MX Player.
        if (!title.isEmpty()) view.putExtra("title", title);
        view.putExtra("secure_uri", true);

        String firstSub = firstSubUrl(call.getArray("subs"));
        if (firstSub != null) {
            // VLC external-subtitle extra
            view.putExtra("subtitles_location", firstSub);
        }

        try {
            Intent chooser = Intent.createChooser(view, "تشغيل عبر");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject("no external player: " + e.getMessage());
        }
    }

    @PluginMethod
    public void appInfo(PluginCall call) {
        JSObject ret = new JSObject();
        try {
            PackageInfo p = getContext().getPackageManager()
                    .getPackageInfo(getContext().getPackageName(), 0);
            long code = (Build.VERSION.SDK_INT >= 28) ? p.getLongVersionCode() : p.versionCode;
            ret.put("versionCode", code);
            ret.put("versionName", p.versionName);
        } catch (Exception e) {
            ret.put("versionCode", 0);
            ret.put("versionName", "");
        }
        call.resolve(ret);
    }

    /** Open a URL in the system browser / Downloader (used by the update banner). */
    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.trim().isEmpty()) {
            call.reject("missing url");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("cannot open url: " + e.getMessage());
        }
    }

    private String firstSubUrl(JSArray subs) {
        if (subs == null) return null;
        try {
            JSONArray arr = new JSONArray(subs.toString());
            if (arr.length() > 0) {
                JSONObject o = arr.getJSONObject(0);
                String u = o.optString("url", "");
                return u.isEmpty() ? null : u;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
