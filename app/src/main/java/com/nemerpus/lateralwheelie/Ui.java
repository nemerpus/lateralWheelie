package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static int BG = Color.rgb(3, 7, 11);
    static int BG_2 = Color.rgb(5, 15, 24);
    static int PANEL = Color.rgb(7, 17, 26);
    static int CARD = Color.rgb(9, 23, 34);
    static int CARD_2 = Color.rgb(13, 31, 44);
    static int BORDER = Color.rgb(38, 66, 88);
    static int BORDER_HOT = Color.rgb(45, 125, 168);
    static int TEXT = Color.rgb(232, 241, 250);
    static int MUTED = Color.rgb(145, 169, 194);
    static final int BLUE = Color.rgb(35, 178, 255);
    static final int RED = Color.rgb(255, 48, 68);
    static final int GREEN = Color.rgb(43, 224, 184);
    static final int YELLOW = Color.rgb(255, 212, 53);
    static final int ORANGE = Color.rgb(255, 126, 31);

    private Ui() {}

    static void applyTheme(Context c) {
        if (AppPrefs.lightTheme(c)) {
            BG=Color.rgb(244,247,250); BG_2=Color.rgb(234,241,247); PANEL=Color.rgb(250,252,254);
            CARD=Color.rgb(255,255,255); CARD_2=Color.rgb(244,248,251); BORDER=Color.rgb(185,204,218);
            BORDER_HOT=Color.rgb(64,155,205); TEXT=Color.rgb(20,31,42); MUTED=Color.rgb(89,109,126);
        } else {
            BG=Color.rgb(3,7,11); BG_2=Color.rgb(5,15,24); PANEL=Color.rgb(7,17,26); CARD=Color.rgb(9,23,34);
            CARD_2=Color.rgb(13,31,44); BORDER=Color.rgb(38,66,88); BORDER_HOT=Color.rgb(45,125,168);
            TEXT=Color.rgb(232,241,250); MUTED=Color.rgb(145,169,194);
        }
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable appBackground(Context c) {
        int[] colors = AppPrefs.lightTheme(c) ? new int[]{Color.rgb(248,250,252),Color.rgb(235,243,249),Color.rgb(247,249,251)} : new int[]{Color.rgb(2,7,12),Color.rgb(5,18,29),Color.rgb(2,7,11)};
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return d;
    }

    static TextView text(Context c, String value, float sp, int color) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    static TextView title(Context c, String value, float sp) {
        TextView v = text(c, value, sp, TEXT);
        v.setTypeface(Typeface.create("sans", Typeface.BOLD));
        return v;
    }

    static GradientDrawable rounded(int fill, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable outlined(int fill, int stroke, float radiusDp, float strokeDp, Context c) {
        GradientDrawable d = rounded(fill, radiusDp, c);
        d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    static GradientDrawable panel(Context c) {
        int[] colors=AppPrefs.lightTheme(c)?new int[]{Color.rgb(255,255,255),Color.rgb(242,247,250)}:new int[]{Color.rgb(8,22,33),Color.rgb(5,14,22)};
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(c, 18));
        d.setStroke(dp(c, 1), BORDER);
        return d;
    }

    static GradientDrawable cardBackground(Context c) {
        int[] colors=AppPrefs.lightTheme(c)?new int[]{Color.rgb(255,255,255),Color.rgb(244,248,251)}:new int[]{Color.rgb(12,31,45),Color.rgb(6,17,26)};
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(c, 16));
        d.setStroke(dp(c, 1), BORDER);
        return d;
    }

    static TextView action(Context c, String label, int accent, boolean filled) {
        TextView v = title(c, label, 13);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(c, 52));
        v.setMinWidth(dp(c, 52));
        v.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        GradientDrawable normal;
        if (filled) {
            int deep = Color.rgb(Math.max(0, Color.red(accent) / 3), Math.max(0, Color.green(accent) / 3), Math.max(0, Color.blue(accent) / 3));
            normal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{deep, accent});
            normal.setCornerRadius(dp(c, 14));
            normal.setStroke(dp(c, 1), accent);
        } else {
            normal = outlined(CARD_2, accent == RED ? Color.rgb(132, 43, 53) : BORDER_HOT, 14, 1, c);
        }
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(Color.argb(78, 255, 255, 255)), normal, null);
        v.setBackground(ripple);
        v.setClickable(true);
        v.setFocusable(true);
        v.setContentDescription(label);
        return v;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setBackground(cardBackground(c));
        return l;
    }

    static LinearLayout.LayoutParams lpMatchWrap(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    static void margin(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            lp.setMargins(dp(v.getContext(), l), dp(v.getContext(), t), dp(v.getContext(), r), dp(v.getContext(), b));
            v.setLayoutParams(lp);
        }
    }

    static int heat(double angle) {
        double a = Math.max(0, Math.min(70, Math.abs(angle)));
        if (a <= 15) return mix(GREEN, Color.rgb(113, 232, 78), a / 15.0);
        if (a <= 30) return mix(Color.rgb(113, 232, 78), YELLOW, (a - 15) / 15.0);
        if (a <= 45) return mix(YELLOW, ORANGE, (a - 30) / 15.0);
        if (a <= 60) return mix(ORANGE, RED, (a - 45) / 15.0);
        return mix(RED, Color.rgb(255, 20, 40), (a - 60) / 10.0);
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }
}
