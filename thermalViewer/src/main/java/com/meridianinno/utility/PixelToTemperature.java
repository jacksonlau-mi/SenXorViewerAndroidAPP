package com.meridianinno.utility;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by frankMac on 1/27/18.
 */

public class PixelToTemperature {

    private static final String TAG = PixelToTemperature.class.getName();

    private static final int R_INDX = 0;
    private static final int G_INDX = 1;
    private static final int B_INDX = 2;

    private static final float COLOR_OFFSET_TO_TEMPERATURE_OFFSET = 60f;    // camera firmware uses 600 COLOR OFFSET = 60 degrees C

    private static final float MAX_TEMPERATURE = (59f * 20f + 19f)/10f - COLOR_OFFSET_TO_TEMPERATURE_OFFSET;    // 59.9C

    private static Map<Long, Float> mTempLookupMap = generateTemperatureLookupMap();

    private static Map<Long, Float> generateTemperatureLookupMap () {
        int RGB_ColorPalette[][] = generateRGBColorPalette();
        Map<Long, Float> map = new HashMap<>();

        // 1200 temperature values, mapped to 0 to 120 degrees C in 0.1 degree increment
        // some are duplicate RGB values, so 1060 map entries will be generated
        for (int i = 0; i < 60; i++) {
            for (int j = 0; j < 20; j++) {
                // temperature
                float temp = ((float)(i*20 + j) / 10f) - COLOR_OFFSET_TO_TEMPERATURE_OFFSET;
                // note, because of color offset, colors for negative temperatures should not be possible, but leave as negative to indicate error

                byte RVal = (byte) ((float) RGB_ColorPalette[i][R_INDX] + ((float) RGB_ColorPalette[i + 1][R_INDX] - (float) RGB_ColorPalette[i][R_INDX]) / 20 * j);
                byte GVal = (byte) ((float) RGB_ColorPalette[i][G_INDX] + ((float) RGB_ColorPalette[i + 1][G_INDX] - (float) RGB_ColorPalette[i][G_INDX]) / 20 * j);
                byte BVal = (byte) ((float) RGB_ColorPalette[i][B_INDX] + ((float) RGB_ColorPalette[i + 1][B_INDX] - (float) RGB_ColorPalette[i][B_INDX]) / 20 * j);

                // form rgb from 3 bytes of R, G, and B
                long rgb = ((long)RVal << 16)&0x00FF0000 | ((long)GVal << 8)&0x00FF00 | (long)BVal & 0x00FF;
                rgb &= 0x00FFFFFF;
                Log.v(TAG, "TempLookupMap rgb="+Long.toHexString(rgb).toUpperCase()+" r="+RVal+" g="+GVal+" b="+BVal+ " temp="+temp);

                // warn if already in the map. Override with the higher temp
                if (map.containsKey(rgb)) {
                    Log.v(TAG, "RGB value of "+Long.toHexString(rgb)+" override previous mapped value "+map.get(rgb)+" to "+temp+" degrees");
                }
                // add to map
                map.put(new Long(rgb), new Float(temp));

            }
        }

        Log.d(TAG, "generateTemperatureLookupMap # of entries = "+map.size());
        return map;
    }

    /*
        pixelRGB - alpha, R, G, B
        Attempt to find from temperatureLookupMap,
        if not found, use the nearest value based on (R - Rmap)^2 + (G - Gmap)^2 _ (B - Bmap)^2
     */
    public static float getTemperatureFromRGB(long pixelARGB) {

        pixelARGB = pixelARGB & 0x00FFFFFF;
        Float temperature = mTempLookupMap.get(pixelARGB);
        if (temperature != null) {
            Log.d(TAG, "getTemperatureFromRGB from lookupMap. pixel:"+Long.toHexString(pixelARGB) + " = "+ temperature);
            return temperature;
        }

        // find nearest value
        temperature = Float.NaN;
        long smallestDiff = Long.MAX_VALUE;
        long mapRGB = 0;
        for (Map.Entry<Long, Float> entry : mTempLookupMap.entrySet()) {
            // if negative temperature, don't use it to calculate nearest value
            if (entry.getValue() < 0) {
                continue;
            }

            long pix2RGB = entry.getKey() & 0x00FFFFFF;
            long curDiff = rgbDistance(pixelARGB, pix2RGB);
            if (curDiff < smallestDiff) {
                smallestDiff = curDiff;
                temperature = entry.getValue();
                mapRGB = pix2RGB;    // for debugging
            }
        }

        // if temperature is not found, set to default unknown value
        if (temperature == Float.NaN) {
            temperature = 20.0f;
        }

        Log.d(TAG, "getTemperatureFromRGB from closest color. pixel:"+Long.toHexString(pixelARGB).toUpperCase() + ". closest RGB: "
                            + Long.toHexString(mapRGB).toUpperCase() +" temp="+temperature);
        return temperature;
    }

    public static float getMaxTemperature() {
        return MAX_TEMPERATURE;
    }


    /*
        from https://stackoverflow.com/questions/3373860/convert-a-bitmap-to-grayscale-in-android
    */
    public static Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    //
    //  return (R2 - R1)^2 + (G2 - G1)^2 _ (B2 - B1)^2
    //
    private static long rgbDistance(long pix1, long pix2) {
        long sum = 0;
        long diff;
        diff =  ((pix2 & 0x00FF0000) >> 16) - ((pix1 & 0x00FF0000) >> 16);
        sum += diff * diff;
        diff = ((pix2 & 0x00FF00) >> 8) - ((pix1 & 0x00FF00) >> 8);
        sum += diff * diff;
        diff = (pix2 & 0x00FF) - (pix1 & 0x00FF);
        sum += diff * diff;

        return sum;
    }

    //
    //  RGB palette used by camera firmware to map from temperature to pixel
    //
    private static int[][] generateRGBColorPalette() {
        int palette[][] = {
            {176, 0, 240},    //	0.25
            {142, 0, 240},    //	0.75
            {108, 0, 240},    //	1.25
            {65, 0, 240},    //	1.75
            {0, 0, 235},    //	2.25
            {0, 0, 218},    //	2.75
            {0, 0, 201},    //	3.25
            {0, 0, 184},    //	3.75
            {0, 0, 163},    //	4.25
            {0, 19, 133},    //	4.75
            {0, 48, 110},    //	5.25
            {0, 74, 104},    //	5.75
            {0, 100, 110},    //	6.25
            {0, 97, 119},    //	6.75
            {0, 104, 151},    //	7.25
            {0, 119, 160},    //	7.75
            {0, 136, 160},    //	8.25
            {0, 153, 168},    //	8.75
            {0, 170, 170},    //	9.25
            {0, 189, 189},    //	9.75
            {0, 206, 206},    //	10.25
            {0, 219, 219},    //	10.75
            {0, 228, 228},    //	11.25
            {0, 236, 236},    //	11.75
            {0, 240, 225},    //	12.25
            {0, 235, 204},    //	12.75
            {0, 232, 155},    //	13.25
            {0, 225, 117},    //	13.75
            {0, 217, 119},    //	14.25
            {0, 200, 119},    //	14.75
            {0, 184, 110},    //	15.25
            {0, 174, 80},    //	15.75
            {0, 157, 80},    //	16.25
            {0, 140, 80},    //	16.75
            {0, 133, 72},    //	17.25
            {0, 140, 34},    //	17.75
            {25, 142, 0},    //	18.25
            {55, 160, 0},    //	18.75
            {65, 177, 0},    //	19.25
            {82, 194, 0},    //	19.75
            {104, 206, 0},    //	20.25
            {131, 214, 0},    //	20.75
            {157, 223, 0},    //	21.25
            {189, 231, 0},    //	21.75
            {231, 232, 0},    //	22.25
            {224, 223, 0},    //	22.75
            {224, 206, 0},    //	23.25
            {224, 180, 0},    //	23.75
            {224, 153, 0},    //	24.25
            {224, 128, 0},    //	24.75
            {224, 102, 0},    //	25.25
            {224, 76, 0},    //	25.75
            {224, 51, 0},    //	26.25
            {219, 17, 0},    //	26.75
            {206, 0, 0},    //	27.25
            {183, 0, 0},    //	27.75
            {157, 0, 0},    //	28.25
            {131, 0, 0},    //	28.75
            {104, 0, 0},    //	29.25
            {80, 0, 0},    //	29.75
            {80, 0, 0},    //	29.75
        };
        return palette;
    }
}
