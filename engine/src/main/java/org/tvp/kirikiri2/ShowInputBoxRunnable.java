package org.tvp.kirikiri2;

import bridge.NativeBridge;
import org.cocos2dx.lib.Cocos2dxGLSurfaceView;

public final class ShowInputBoxRunnable implements Runnable {
    public final String inputText;

    public ShowInputBoxRunnable(String inputText) {
        this.inputText = inputText;
    }

    @Override
    public void run() {
        Cocos2dxGLSurfaceView gl =
                KR2Activity.sInstance != null ? KR2Activity.sInstance.getGLSurfaceView() : null;
        if (gl != null) {
            try {
                gl.onPause();
            } catch (Throwable ignored) {
            }
        }
        KrDialogModel dialog = KR2Activity.mDialogMessage;
        NativeBridge.onKrkrDialogShown();
        KR2Activity.mCurrentDialog = KrDialogStyle.showInputBox(
                KR2Activity.sInstance,
                dialog.title,
                dialog.message,
                inputText,
                dialog.buttons,
                (which, text) -> {
                    KR2Activity.mCurrentDialog = null;
                    if (gl != null) {
                        try {
                            gl.onResume();
                        } catch (Throwable ignored) {
                        }
                    }
                    NativeBridge.onKrkrDialogDismissed();
                    KR2Activity.notifyDialogResult(which, text);
                });
    }
}
