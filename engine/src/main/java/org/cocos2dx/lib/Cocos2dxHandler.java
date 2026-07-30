package org.cocos2dx.lib;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import java.lang.ref.WeakReference;

public class Cocos2dxHandler extends Handler {
    public static final int HANDLER_SHOW_DIALOG = 1;
    public static final int HANDLER_SHOW_TEXT_INPUT = 1;
    public static final int HANDLER_HIDE_TEXT_INPUT = 2;

    private final WeakReference<Cocos2dxActivity> mActivity;

    public static class DialogMessage {
        public String message;
        public String titile;

        public DialogMessage(String title, String message) {
            this.titile = title;
            this.message = message;
        }
    }

    public Cocos2dxHandler(Cocos2dxActivity activity) {
        this.mActivity = new WeakReference<>(activity);
    }

    private void showDialog(Message message) {
        Cocos2dxActivity activity = this.mActivity.get();
        if (activity == null) return;
        DialogMessage dialogMessage = (DialogMessage) message.obj;
        new AlertDialog.Builder(activity)
                .setTitle(dialogMessage.titile)
                .setMessage(dialogMessage.message)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                    }
                })
                .create()
                .show();
    }

    @Override
    public void handleMessage(Message message) {
        if (message.what == HANDLER_SHOW_DIALOG) {
            showDialog(message);
        }
    }
}
