package com.ies_net.artemis;

import android.app.NativeActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

public class ArtemisActivity extends NativeActivity {
    public void DownloadExpansionFiles(String value) {
    }

    public void DownloadResource(String a, String b, String c) {
    }

    public native void EmulateKeyEvent(int keyCode, int action);

    public native void ExecuteTag(String tag);

    public void InAppBilling(String a, String b, boolean c, boolean d) {
        OnFinishPurchase(1, "", "", "", "", 1, "");
    }

    public native void OnFinishPurchase(int result, String a, String b, String c, String d, int e, String f);

    public native void OnFinishVideo();

    public native void OnReadyPlayAssetDelivery(int a, int b, int c);

    public void PlayVideo(String path, int offset, int length, int volume, int skip) {
        Intent intent = new Intent(getApplicationContext(), VideoViewActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra("PATH", path);
        intent.putExtra("OFFSET", offset);
        intent.putExtra("LENGTH", length);
        intent.putExtra("VOLUME", volume);
        intent.putExtra("SKIP", skip);
        startActivityForResult(intent, 1);
        overridePendingTransition(0, 0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getAction() == 0 && keyCode == 66) {
            EmulateKeyEvent(13, 2);
        } else if (event.getAction() == 0 && keyCode == 59) {
            EmulateKeyEvent(115, 2);
        } else if (event.getAction() == 0 && keyCode == 60) {
            EmulateKeyEvent(115, 2);
        } else if (event.getAction() == 0 && keyCode == 113) {
            EmulateKeyEvent(140, 2);
        } else if (event.getAction() == 0 && keyCode == 114) {
            EmulateKeyEvent(140, 2);
        } else if (event.getAction() == 0 && keyCode == 62) {
            EmulateKeyEvent(32, 2);
        } else if (event.getAction() == 0 && keyCode == 21) {
            EmulateKeyEvent(37, 2);
        } else if (event.getAction() == 0 && keyCode == 19) {
            EmulateKeyEvent(38, 2);
        } else if (event.getAction() == 0 && keyCode == 22) {
            EmulateKeyEvent(39, 2);
        } else if (event.getAction() == 0 && keyCode == 20) {
            EmulateKeyEvent(40, 2);
        } else if (event.getAction() == 0 && keyCode == 29) {
            EmulateKeyEvent(143, 2);
        } else if (event.getAction() == 0 && keyCode == 47) {
            EmulateKeyEvent(83, 2);
        } else if (event.getAction() == 0 && keyCode == 40) {
            EmulateKeyEvent(76, 2);
        } else if (event.getAction() == 0 && keyCode == 50) {
            EmulateKeyEvent(86, 2);
        } else if (event.getAction() == 0 && keyCode == 8) {
            EmulateKeyEvent(112, 2);
        } else if (event.getAction() == 0 && keyCode == 9) {
            EmulateKeyEvent(113, 2);
        } else if (event.getAction() == 0 && keyCode == 10) {
            EmulateKeyEvent(114, 2);
        } else if (event.getAction() == 0 && keyCode == 11) {
            EmulateKeyEvent(115, 2);
        } else if (event.getAction() == 0 && keyCode == 12) {
            EmulateKeyEvent(116, 2);
        } else if (event.getAction() == 0 && keyCode == 13) {
            EmulateKeyEvent(117, 2);
        } else if (event.getAction() == 0 && keyCode == 14) {
            EmulateKeyEvent(118, 2);
        } else if (event.getAction() == 0 && keyCode == 15) {
            EmulateKeyEvent(119, 2);
        } else if (event.getAction() == 0 && keyCode == 23) {
            EmulateKeyEvent(13, 2);
        } else if (event.getAction() == 0 && keyCode == 17) {
            EmulateKeyEvent(122, 2);
        } else if (event.getAction() == 0 && keyCode == 18) {
            EmulateKeyEvent(140, 2);
        } else if (event.getAction() == 0 && keyCode == 98) {
            EmulateKeyEvent(1, 2);
        } else if (event.getAction() == 0 && keyCode == 96) {
            EmulateKeyEvent(139, 2);
        } else if (event.getAction() == 0 && keyCode == 97) {
            EmulateKeyEvent(32, 2);
        } else if (event.getAction() == 0 && keyCode == 99) {
            EmulateKeyEvent(123, 2);
        } else if (event.getAction() == 0 && keyCode == 100) {
            EmulateKeyEvent(1, 2);
        } else if (event.getAction() == 0 && keyCode == 101) {
            EmulateKeyEvent(140, 1);
        } else if (event.getAction() == 1 && keyCode == 101) {
            EmulateKeyEvent(140, 0);
        } else if (event.getAction() == 0 && keyCode == 102) {
            EmulateKeyEvent(83, 2);
        } else if (event.getAction() == 0 && keyCode == 103) {
            EmulateKeyEvent(76, 2);
        } else if (event.getAction() == 0 && keyCode == 105) {
            EmulateKeyEvent(124, 2);
        } else if (event.getAction() == 0 && keyCode == 106) {
            EmulateKeyEvent(143, 2);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 1) {
            OnFinishVideo();
        }
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(1024);
        getWindow().addFlags(128);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Intent old = getIntent();
        if (old == null || intent == null) return;
        String oldPath = old.getStringExtra("path");
        String newPath = intent.getStringExtra("path");
        if (oldPath == null || oldPath.equals(newPath) || newPath == null) return;
        Toast.makeText(this, "已有游戏在运行，请先存档并退出游戏后再启动新游戏", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        getWindow().getDecorView().setSystemUiVisibility(5894);
    }
}
