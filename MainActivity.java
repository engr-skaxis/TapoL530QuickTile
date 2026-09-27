package com.bbbun.tapol530;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText ip, email, password;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 48, 40, 32);

        TextView title = new TextView(this);
        title.setText("Tapo L530 Quick Tile");
        title.setTextSize(25);
        title.setTextColor(Color.BLACK);
        box.addView(title);

        TextView info = new TextView(this);
        info.setText("\nEnter the L530's local IP and your Tapo account credentials. "
                + "The credentials are encrypted with Android Keystore and the bulb is controlled locally.\n");
        info.setTextSize(16);
        box.addView(info);

        ip = field("L530 IP address (example: 192.168.1.50)");
        email = field("Tapo email");
        password = field("Tapo password");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        box.addView(ip);
        box.addView(email);
        box.addView(password);

        Button save = new Button(this);
        save.setText("Save configuration");
        save.setOnClickListener(v -> save());
        box.addView(save);

        Button add = new Button(this);
        add.setText("Add L530 to Quick Panel");
        add.setOnClickListener(v -> addTile());
        box.addView(add);

        TextView note = new TextView(this);
        note.setText("\nBefore testing, enable Tapo → Me → Third-Party Services → "
                + "Third-Party Compatibility. Your phone and L530 must be on the same Wi-Fi.\n\n"
                + "If the tile is missing, open Quick Panel → Edit → add “L530”.");
        note.setTextSize(14);
        box.addView(note);

        setContentView(box);

        try {
            ip.setText(Prefs.ip(this));
            email.setText(Prefs.email(this));
            password.setText(Prefs.password(this));
        } catch (Exception ignored) {}
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setPadding(0, 20, 0, 20);
        return e;
    }

    private void save() {
        String a = ip.getText().toString().trim();
        String b = email.getText().toString().trim();
        String c = password.getText().toString();
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) {
            Toast.makeText(this, "Fill in all three fields", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Prefs.save(this, a, b, c);
            Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save securely: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            ComponentName cn = new ComponentName(this, L530TileService.class);
            sbm.requestAddTileService(cn, "L530",
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile),
                    getMainExecutor(), result ->
                            Toast.makeText(this,
                                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                            ? "L530 added to Quick Panel"
                                            : "Open Quick Panel → Edit and add L530",
                                    Toast.LENGTH_LONG).show());
        } else {
            Toast.makeText(this, "Open Quick Panel → Edit and add L530", Toast.LENGTH_LONG).show();
        }
    }
}
