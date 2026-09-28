package com.bbbun.tapol530;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class L530TileService extends TileService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        setTileState(Tile.STATE_INACTIVE);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshState();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!Prefs.configured(this)) {
            if (Build.VERSION.SDK_INT >= 24) {
                Intent i = new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(i);
            }
            return;
        }

        setTileState(Tile.STATE_UNAVAILABLE);

        executor.execute(() -> {
            try {
                KlapV2 k = new KlapV2(Prefs.ip(this), Prefs.email(this), Prefs.password(this));
                boolean current = k.getPower();
                k.setPower(!current);
                boolean actual = k.getPower();
                setTileState(actual ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            } catch (Exception e) {
                setTileState(Tile.STATE_UNAVAILABLE);
            }
        });
    }

    private void refreshState() {
        if (!Prefs.configured(this)) {
            setTileState(Tile.STATE_INACTIVE);
            return;
        }

        executor.execute(() -> {
            try {
                KlapV2 k = new KlapV2(Prefs.ip(this), Prefs.email(this), Prefs.password(this));
                boolean on = k.getPower();
                setTileState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            } catch (Exception e) {
                setTileState(Tile.STATE_UNAVAILABLE);
            }
        });
    }

    private void setTileState(int state) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(state);
        tile.setLabel("L530");
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        tile.updateTile();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
