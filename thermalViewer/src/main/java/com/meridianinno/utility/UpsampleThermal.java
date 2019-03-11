package com.meridianinno.utility;

import static com.meridianinno.utility.UpsampleThermal.UPSAMPLE_MODE.BILINEAR;
import static com.meridianinno.utility.UpsampleThermal.UPSAMPLE_MODE.NONE;

/**
 * Created by frankMac on 2/14/18.
 */

public class UpsampleThermal {
    
    public enum UPSAMPLE_MODE {NONE, BILINEAR};

    static public int[] upsample(int thermal_data[], int thermal_w, int thermal_h, int cmos_w, int cmos_h, UPSAMPLE_MODE upsampleMode){
        int[] gen_data = new int[cmos_w * cmos_h];
        final int width_ratio = cmos_w / thermal_w;
        final int height_ratio = cmos_h / thermal_h;
        if (upsampleMode == NONE){
            for (int i = 0; i < cmos_h; i++){
                int hOffset = (i / height_ratio) * thermal_w;
                for (int j = 0; j < cmos_w; j++){
                    gen_data[i*cmos_w + j] = thermal_data[hOffset + j/width_ratio];
                }
            }
        }else if (upsampleMode == BILINEAR){
            for (int i = 0; i < cmos_h; i++){
                for (int j = 0; j < cmos_w; j++){
                    int tx0 = j/width_ratio;
                    int ty0 = i/height_ratio;
                    int tx1 = tx0 + 1;
                    int ty1 = ty0 + 1;
                    if (tx1 == thermal_w){
                        tx1 = thermal_w-1;
                    }
                    if (ty1 == thermal_h){
                        ty1 = thermal_h-1;
                    }

                    int ty0Offset = ty0*thermal_w;
                    int ty1Offset = ty1*thermal_w;
                    int x_inter = j%width_ratio, y_inter = i%height_ratio;
                    int xNy0_inter = (thermal_data[ty0Offset + tx1] * x_inter) + (thermal_data[ty0Offset + tx0] * (width_ratio- x_inter));
                    xNy0_inter /= width_ratio;
                    int xNy1_inter = (thermal_data[ty1Offset + tx1] * x_inter) + (thermal_data[ty1Offset + tx0] * (width_ratio- x_inter));
                    xNy1_inter /= width_ratio;
                    int xNyN_inter = (xNy1_inter * y_inter + xNy0_inter * (height_ratio - y_inter)) / height_ratio;
                    gen_data[i*cmos_w + j] = xNyN_inter;
                }
            }
        }

        return gen_data;
    }

    static public int[] FlipBitmap(int thermal_data[],int thermal_w, int thermal_h) {
        int[] gen_data = new int[thermal_w * thermal_h];

        for (int h=0; h<thermal_h; h++) {
            for (int w = 0; w < thermal_w; w++) {
                gen_data[h * thermal_h + w] = thermal_data[h * thermal_h + (thermal_w - w)];
            }
        }

        // return transformed array
        return gen_data;
    }
}
