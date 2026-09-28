package com.bbbun.tapol530;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class L530TileService extends TileService {
    private static final String TAG = "L530Tile";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        safeSetState(Tile.STATE_INACTIVE);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        safeSetState(currentGuessState());
        refreshState();
    }

    @Override
    public void onClick() {
        super.onClick();
        try {
            if (!Prefs.configured(this)) {
                if (Build.VERSION.SDK_INT >= 24) {
                    Intent i = new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivityAndCollapse(i);
                }
                return;
            }

            safeSetState(Tile.STATE_UNAVAILABLE);

            executor.execute(() -> {
                try {
                    KlapV2 k = new KlapV2(Prefs.ip(this), Prefs.email(this), Prefs.password(this));
                    boolean current = k.getPower();
                    k.setPower(!current);
                    boolean actual = k.getPower();
                    safeSetState(actual ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
                } catch (Throwable t) {
                    Log.w(TAG, "toggle failed", t);
                    safeSetState(Tile.STATE_UNAVAILABLE);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "onClick failed", t);
        }
    }

    private void refreshState() {
        executor.execute(() -> {
            try {
                if (!Prefs.configured(this)) {
                    safeSetState(Tile.STATE_INACTIVE);
                    return;
                }
                KlapV2 k = new KlapV2(Prefs.ip(this), Prefs.email(this), Prefs.password(this));
                boolean on = k.getPower();
                safeSetState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            } catch (Throwable t) {
                Log.w(TAG, "refresh failed", t);
                safeSetState(Tile.STATE_UNAVAILABLE);
            }
        });
    }

    private int currentGuessState() {
        try {
            return Prefs.configured(this) ? Tile.STATE_INACTIVE : Tile.STATE_INACTIVE;
        } catch (Throwable t) {
            return Tile.STATE_INACTIVE;
        }
    }

    private void safeSetState(int state) {
        try {
            Tile tile = getQsTile();
            if (tile == null) return;
            tile.setState(state);
            tile.setLabel("L530");
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
            tile.updateTile();
        } catch (Throwable t) {
            Log.w(TAG, "setState failed", t);
        }
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
