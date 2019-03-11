package com.meridianinno.utility;

import android.app.Application;
import android.content.Context;
import android.widget.Toast;

import com.meridianinno.senxorviewer.R;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.annotation.ReportsCrashes;

/**
 * Created by timsheu on 7/15/16.
 */


@ReportsCrashes(
        mailTo = "jacksonlau@meridianinno.com",
        customReportContent = {
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.PHONE_MODEL,
                ReportField.CUSTOM_DATA,
                ReportField.STACK_TRACE,
                ReportField.LOGCAT
        }
//        mode = ReportingInteractionMode.TOAST
)

public class CrashReport extends Application {
    private static final String TAG = "CrashReport";
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        int toastTextID = R.string.crash_toast_text;
        if (ACRA.isInitialised()){
            Toast.makeText(this, toastTextID, Toast.LENGTH_LONG).show();
        }
        ACRA.init(this);
    }
}
