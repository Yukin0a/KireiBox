package org.tvp.kirikiri2;

import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import org.cocos2dx.lib.Cocos2dxActivity;

public final class ShowTextInputRunnable implements Runnable {
    public int x;
    public int y;
    public int width;
    public int height;
    @Override public void run() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height + 15);
        lp.leftMargin = x;
        lp.topMargin = y;
        View view = KR2Activity.mTextEdit;
        if (view == null) {
            KrTextInputView v = new KrTextInputView(KR2Activity.sInstance);
            v.setFocusableInTouchMode(true);
            v.setFocusable(true);
            v.setOnKeyListener(v);
            KR2Activity.mTextEdit = v;
            KR2Activity.sInstance.mFrameLayout.addView(KR2Activity.mTextEdit, lp);
        } else {
            view.setLayoutParams(lp);
        }
        KR2Activity.mTextEdit.setVisibility(View.VISIBLE);
        KR2Activity.mTextEdit.requestFocus();
        ((InputMethodManager) Cocos2dxActivity.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(KR2Activity.mTextEdit, 0);
    }
}
