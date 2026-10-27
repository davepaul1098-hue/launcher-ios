package com.moi.launcherios;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private boolean hideStatus = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LauncherPlugin.class);
        super.onCreate(savedInstanceState);
        setupWindow();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyBars();
        }
    }

    public void setStatusHidden(boolean hidden) {
        hideStatus = hidden;
        applyBars();
    }

    private void setupWindow() {
        try {
            final Window w = getWindow();
            WindowCompat.setDecorFitsSystemWindows(w, false);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) {
                w.setNavigationBarContrastEnforced(false);
                w.setStatusBarContrastEnforced(false);
            }
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
            final View content = findViewById(android.R.id.content);
            final float density = getResources().getDisplayMetrics().density;
            ViewCompat.setOnApplyWindowInsetsListener(w.getDecorView(), new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                    Insets status = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars());
                    Insets cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                    Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                    int top = Math.max(status.top, cut.top);
                    int bottom = ime.bottom > 0 ? 0 : nav.bottom;
                    if (content != null) {
                        content.setPadding(0, 0, 0, ime.bottom);
                    }
                    LauncherPlugin.setInsets(Math.round(top / density), Math.round(bottom / density));
                    return insets;
                }
            });
            ViewCompat.requestApplyInsets(w.getDecorView());
            applyBars();
        } catch (Exception e) {
            // ignore
        }
    }

    private void applyBars() {
        try {
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (c == null) {
                return;
            }
            c.setAppearanceLightStatusBars(false);
            c.setAppearanceLightNavigationBars(false);
            if (hideStatus) {
                c.hide(WindowInsetsCompat.Type.statusBars());
                c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsetsCompat.Type.statusBars());
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
