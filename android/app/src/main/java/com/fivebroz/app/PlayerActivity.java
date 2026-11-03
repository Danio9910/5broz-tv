package com.fivebroz.app;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen native player. Plays virtually any container/codec the device
 * can decode (MKV, HEVC/H265, multi-audio, HLS, DASH) and loads external
 * subtitle tracks. Audio/subtitle switching is available from the player's
 * built-in track menu.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        playerView.setShowSubtitleButton(true);
        playerView.setControllerShowTimeoutMs(3500);

        String url = getIntent().getStringExtra("url");
        String subsJson = getIntent().getStringExtra("subs");

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url));

        List<MediaItem.SubtitleConfiguration> subConfigs = buildSubs(subsJson);
        if (!subConfigs.isEmpty()) {
            item.setSubtitleConfigurations(subConfigs);
        }

        player.setMediaItem(item.build());
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private List<MediaItem.SubtitleConfiguration> buildSubs(String subsJson) {
        List<MediaItem.SubtitleConfiguration> out = new ArrayList<>();
        if (subsJson == null || subsJson.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(subsJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String sUrl = o.optString("url", "");
                if (sUrl.isEmpty()) continue;
                String lang = o.optString("lang", "und");
                String label = o.optString("label", lang);
                // The web proxy serves subtitles as WebVTT (text/vtt).
                MediaItem.SubtitleConfiguration.Builder b =
                        new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sUrl))
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage(lang)
                                .setLabel(label);
                if (i == 0) {
                    b.setSelectionFlags(C.SELECTION_FLAG_DEFAULT);
                }
                out.add(b.build());
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void immersive() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        release();
    }

    private void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
