package org.cocos2dx.lib;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class ResizeLayout extends FrameLayout {
    private boolean mEnableForceDoLayout;
    private final Handler mLayoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRequestLayout = new Runnable() {
        @Override
        public void run() {
            requestLayout();
            invalidate();
        }
    };

    public ResizeLayout(Context context) {
        super(context);
        this.mEnableForceDoLayout = false;
    }

    public ResizeLayout(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mEnableForceDoLayout = false;
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (this.mEnableForceDoLayout) {
            mLayoutHandler.removeCallbacks(mRequestLayout);
            mLayoutHandler.postDelayed(mRequestLayout, 41L);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mLayoutHandler.removeCallbacks(mRequestLayout);
        super.onDetachedFromWindow();
    }

    public void setEnableForceDoLayout(boolean enable) {
        this.mEnableForceDoLayout = enable;
    }
}
