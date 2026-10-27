package com.moi.launcherios;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.view.HapticFeedbackConstants;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@CapacitorPlugin(name = "Launcher")
public class LauncherPlugin extends Plugin {

    private BroadcastReceiver batteryRcv;
    private BroadcastReceiver screenRcv;
    private BroadcastReceiver pkgRcv;
    private boolean torchOn = false;

    @Override
    public void load() {
        batteryRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                notifyListeners("battery", batteryInfo());
            }
        };
        IntentFilter bf = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        bf.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        reg(batteryRcv, bf);

        screenRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                notifyListeners("screenOff", new JSObject());
            }
        };
        reg(screenRcv, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        pkgRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                notifyListeners("appsChanged", new JSObject());
            }
        };
        IntentFilter pf = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addDataScheme("package");
        reg(pkgRcv, pf);
    }

    private void reg(BroadcastReceiver r, IntentFilter f) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                getContext().registerReceiver(r, f, Context.RECEIVER_EXPORTED);
            } else {
                getContext().registerReceiver(r, f);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void handleOnDestroy() {
        try {
            if (batteryRcv != null) getContext().unregisterReceiver(batteryRcv);
            if (screenRcv != null) getContext().unregisterReceiver(screenRcv);
            if (pkgRcv != null) getContext().unregisterReceiver(pkgRcv);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void handleOnNewIntent(Intent intent) {
        if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction())) {
            notifyListeners("home", new JSObject());
        }
    }

    private JSObject batteryInfo() {
        JSObject o = new JSObject();
        int pct = 100;
        boolean charging = false;
        boolean saver = false;
        try {
            Intent i = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                if (level >= 0 && scale > 0) {
                    pct = Math.round(level * 100f / scale);
                }
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
            }
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                saver = pm.isPowerSaveMode();
            }
        } catch (Exception e) {
            // ignore
        }
        o.put("level", pct);
        o.put("charging", charging);
        o.put("saver", saver);
        return o;
    }

    @PluginMethod
    public void getBattery(PluginCall call) {
        call.resolve(batteryInfo());
    }

    private String iconB64(Drawable d, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        boolean done = false;
        if (Build.VERSION.SDK_INT >= 26 && d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            Drawable bg = a.getBackground();
            Drawable fg = a.getForeground();
            int pad = size / 4;
            if (bg != null) {
                bg.setBounds(-pad, -pad, size + pad, size + pad);
                bg.draw(c);
            }
            if (fg != null) {
                fg.setBounds(-pad, -pad, size + pad, size + pad);
                fg.draw(c);
            }
            done = true;
        }
        if (!done) {
            d.setBounds(0, 0, size, size);
            d.draw(c);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }

    @PluginMethod
    public void getApps(final PluginCall call) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PackageManager pm = getContext().getPackageManager();
                    Intent main = new Intent(Intent.ACTION_MAIN, null);
                    main.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
                    Collections.sort(list, new ResolveInfo.DisplayNameComparator(pm));
                    String self = getContext().getPackageName();
                    HashSet<String> seen = new HashSet<String>();
                    JSArray arr = new JSArray();
                    for (ResolveInfo ri : list) {
                        String pkg = ri.activityInfo.packageName;
                        if (pkg.equals(self) || !seen.add(pkg)) {
                            continue;
                        }
                        JSObject o = new JSObject();
                        o.put("pkg", pkg);
                        o.put("label", String.valueOf(ri.loadLabel(pm)));
                        int cat = -1;
                        if (Build.VERSION.SDK_INT >= 26) {
                            cat = ri.activityInfo.applicationInfo.category;
                        }
                        o.put("cat", cat);
                        try {
                            o.put("icon", iconB64(ri.loadIcon(pm), 96));
                        } catch (Exception e) {
                            o.put("icon", "");
                        }
                        arr.put(o);
                    }
                    JSObject res = new JSObject();
                    res.put("apps", arr);
                    call.resolve(res);
                } catch (Exception e) {
                    call.reject("getApps: " + e.getMessage());
                }
            }
        }).start();
    }

    @PluginMethod
    public void launch(PluginCall call) {
        String pkg = call.getString("pkg");
        try {
            Intent i = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) {
                call.reject("Aucune activité de lancement");
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("launch: " + e.getMessage());
        }
    }

    @PluginMethod
    public void appInfo(PluginCall call) {
        String pkg = call.getString("pkg");
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("appInfo: " + e.getMessage());
        }
    }

    @PluginMethod
    public void uninstall(PluginCall call) {
        String pkg = call.getString("pkg");
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("uninstall: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url");
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("openUrl: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openCamera(PluginCall call) {
        try {
            Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("openCamera: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openHomeSettings(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_HOME_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            try {
                Intent j = new Intent(Settings.ACTION_SETTINGS);
                j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(j);
                call.resolve();
            } catch (Exception e2) {
                call.reject("openHomeSettings: " + e2.getMessage());
            }
        }
    }

    @PluginMethod
    public void toggleTorch(PluginCall call) {
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            String id = null;
            for (String cid : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(cid);
                Boolean flash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash) && facing != null
                        && facing.intValue() == CameraCharacteristics.LENS_FACING_BACK) {
                    id = cid;
                    break;
                }
            }
            if (id == null) {
                call.reject("Pas de flash");
                return;
            }
            torchOn = !torchOn;
            cm.setTorchMode(id, torchOn);
            JSObject r = new JSObject();
            r.put("on", torchOn);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("toggleTorch: " + e.getMessage());
        }
    }

    @PluginMethod
    public void haptic(PluginCall call) {
        final String kind = call.getString("kind", "light");
        try {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int c = "heavy".equals(kind)
                                ? HapticFeedbackConstants.LONG_PRESS
                                : HapticFeedbackConstants.KEYBOARD_TAP;
                        getBridge().getWebView().performHapticFeedback(c);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });
        } catch (Exception e) {
            // ignore
        }
        call.resolve();
    }
}
