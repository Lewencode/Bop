package com.sahdeepsingh.Bop.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;

import com.sahdeepsingh.Bop.R;
import com.sahdeepsingh.Bop.playerMain.Main;

public class utils {
    public static Bitmap blurMyImage(Bitmap image, Context context) {
        if (null == image) return null;

        Bitmap bitmaplol = image.copy(image.getConfig(), true);
        RenderScript renderScript = RenderScript.create(context);
        Allocation tmpIn = Allocation.createFromBitmap(renderScript, image);
        Allocation tmpOut = Allocation.createFromBitmap(renderScript, bitmaplol);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        theIntrinsic.setRadius(77f);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(bitmaplol);
        renderScript.destroy();
        return bitmaplol;

    }

    public static Drawable getThemedIcon(Context c, Drawable drawable) {
        String theme = Main.settings.get("modes", "Day");
        if (theme.equals("Day"))
            drawable.mutate().setColorFilter(ContextCompat.getColor(c, R.color.md_grey_800), PorterDuff.Mode.MULTIPLY);

        return drawable;
    }

    public static void openCustomTabs(Context context, String url) {
        CustomTabsIntent.Builder builderq = new CustomTabsIntent.Builder();
        builderq.setToolbarColor(context.getResources().getColor(R.color.accent));
        builderq.addDefaultShareMenuItem().enableUrlBarHiding();
        CustomTabsIntent customTabsIntent = builderq.build();
        customTabsIntent.launchUrl(context, Uri.parse(url));
    }

    private static final int[] TEMP_ARRAY = new int[1];

    public static int getThemeAttrColor(Context context, int attr) {
        TEMP_ARRAY[0] = attr;
        TypedArray a = context.obtainStyledAttributes(null, TEMP_ARRAY);
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();
        }
    }

}
