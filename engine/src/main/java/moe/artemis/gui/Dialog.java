package moe.artemis.gui;

import android.app.Activity;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import org.tvp.kirikiri2.KrDialogStyle;

/**
 * Artemis 引擎的 Java 弹窗桥接。
 *
 * <p>某些 Artemis 游戏在脚本需要确认/输入对话框时调用 moe.artemis.gui.Dialog.Show(...)。
 * 缺少此类会导致原生侧等待永远不会到来的结果，表现为游戏卡死在确认点。</p>
 *
 * <p>弹窗样式统一使用 KrDialogStyle，与 KRKR 弹窗视觉一致。</p>
 */
public final class Dialog {
    private static final String TAG = "ArtemisDialog";
    private static final Map<Integer, Dialog> INSTANCES = new HashMap<>();
    private static int seed;

    private final Activity activity;
    private final String title;
    private final String message;
    private final boolean cancelable;
    private final boolean textField;
    private final long context;

    public Dialog(Activity activity, String title, String message, boolean cancelable, boolean textField, long context) {
        this.activity = activity;
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
        this.cancelable = cancelable;
        this.textField = textField;
        this.context = context;
        activity.runOnUiThread(this::showInternal);
    }

    private native void OnClose(int result, String text, long context);

    public static void Release(int id) {
        synchronized (INSTANCES) {
            INSTANCES.remove(id);
        }
    }

    public static int Show(Activity activity, String title, String message, boolean cancelable, boolean textField, long context) {
        if (activity == null) return 0;
        int id;
        synchronized (INSTANCES) {
            id = ++seed;
            INSTANCES.put(id, new Dialog(activity, title, message, cancelable, textField, context));
        }
        return id;
    }

    private void showInternal() {
        try {
            if (activity.isFinishing()) {
                close(0, "");
                return;
            }
            String[] buttons = cancelable ? new String[]{"OK", "Cancel"} : new String[]{"OK"};
            // textField 模式下显示输入框，初始文本为 message；非 textField 模式下 message 作为正文
            String initialText = textField ? message : null;
            String dialogMessage = textField ? null : message;
            KrDialogStyle.showInputBox(activity, title, dialogMessage, initialText, buttons, cancelable,
                    (which, text) -> close(which == 0 ? 1 : 0, text));
        } catch (Throwable t) {
            Log.e(TAG, "show Artemis dialog failed", t);
            close(0, "");
        }
    }

    private void close(int result, String text) {
        try {
            OnClose(result, text, context);
        } catch (Throwable t) {
            Log.e(TAG, "notify Artemis dialog close failed", t);
        }
    }
}
