package org.tvp.kirikiri2;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public final class KrTextInputView extends View implements View.OnKeyListener {
    public KrInputConnection inputConnection;
    public KrTextInputView(Context context) { super(context); }
    @Override public boolean onCheckIsTextEditor() { return true; }
    @Override public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
        KrInputConnection conn = new KrInputConnection(this, true);
        this.inputConnection = conn;
        editorInfo.imeOptions = 301989888;
        return conn;
    }
    @Override public boolean onKey(View view, int keyCode, KeyEvent event) {
        if (!event.isPrintingKey()) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && this.inputConnection != null) this.inputConnection.commitText(String.valueOf((char) event.getUnicodeChar()), 1);
        return true;
    }
    @Override public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        View view;
        if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK && (view = KR2Activity.mTextEdit) != null && view.getVisibility() == VISIBLE) KR2Activity.hideTextInput();
        return super.onKeyPreIme(keyCode, event);
    }
}
