package com.moi.launcherios;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LauncherPlugin.class);
        super.onCreate(savedInstanceState);
        hideStatusBar();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideStatusBar();
        }
    }

    private void hideStatusBar() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            }
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (c != null) {
                c.hide(WindowInsetsCompat.Type.statusBars());
                c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
