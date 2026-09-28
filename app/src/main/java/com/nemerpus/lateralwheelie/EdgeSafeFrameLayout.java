package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.util.AttributeSet;
import android.view.DisplayCutout;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/**
 * Keeps interactive content out of status/navigation bars and display cutouts.
 * The window background still draws edge-to-edge behind system chrome.
 */
public class EdgeSafeFrameLayout extends FrameLayout {
    public EdgeSafeFrameLayout(Context c) { super(c); init(); }
    public EdgeSafeFrameLayout(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets gestures = insets.getInsets(WindowInsets.Type.mandatorySystemGestures());
                left = Math.max(bars.left, gestures.left);
                top = bars.top;
                right = Math.max(bars.right, gestures.right);
                bottom = Math.max(bars.bottom, gestures.bottom);
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }
            }
            int side = Ui.dp(getContext(), 8);
            setPadding(left + side, top + Ui.dp(getContext(), 4), right + side, bottom + Ui.dp(getContext(), 6));
            return insets;
        });
    }
}
