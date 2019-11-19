package com.meridianinno.senxorviewer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import org.opencv.core.Mat;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.hardware.usb.UsbManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.graphics.Bitmap;

import com.meridianinno.Model.Temp_stat;
import com.meridianinno.facedetection.FaceDetect;
import com.meridianinno.android.colorbar.ColorBar;
import com.meridianinno.utility.ConversionHelper;
import com.meridianinno.utility.UpsampleThermal;
import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;
import com.serenegiant.widget.UVCCameraTextureView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static com.serenegiant.usb.UVCCamera.PIXEL_FORMAT_RGBX;
import static java.lang.Thread.sleep;

public class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent {
    private static final boolean DEBUG = true;	// TODO set false on release
    private static final String TAG = "MainActivity";
    private static final boolean DISPLAY_DEBUG_IMAGES = false;

    /**
     * set true if you want to record movie using MediaSurfaceEncoder
     * (writing frame data into Surface camera from MediaCodec
     *  by almost same way as USBCameratest2)
     * set false if you want to record movie using MediaVideoEncoder
     */
    private static final boolean USE_SURFACE_ENCODER = false;

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_WIDTH = 640;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_HEIGHT = 480;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = 0;

    /*
        thermal data resolution
     */
    public static final int THERMAL_DATA_WIDTH = 32;
    public static final int THERMAL_DATA_HEIGHT = 32;
    public static final int THERMAL_EXPANDED_WIDTH = FaceDetect.MaxThermalRows;
    public static final int THERMAL_EXPANDED_HEIGHT = FaceDetect.MaxThermalCols;

    // for accessing USB
    private USBMonitor mUSBMonitor;
    // Handler to execute camera related methods sequentially on private thread
    private UVCCameraHandler mCameraHandler;
    // for camera preview display
    private CameraViewInterface mUVCCameraView;
    // for annotation overlay
    private AnnotationView mAnnotationView;
    private View mTopBar;
    // Title String
    private TextView mAppTitleView;
    // UI buttons
    private ImageButton mCaptureButton;
    private View mSettingsButton;
    private ImageButton settingsButton;
    private View mGalleryButton;
    private ImageButton galleryButton;
    private ImageButton mDisplayModeButton;
    private ImageButton mRecordButton;
    private ImageButton mFlipButton;
    private View mCameraDetected;

    private UsbManager usbmanager;
    private boolean previewActive;

    // mScreenBitmap for duplicating image on screen
    // use PIXEL_FORMAT_RGBX/ARGB_8888 if we want callback image to contain thermal/cis. Use PIXEL_FORMAT_RGB565 / ARGB_565 if CIS only
    private Bitmap mScreenBitmap = Bitmap.createBitmap(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888);
    private int frameCallBackFormat = PIXEL_FORMAT_RGBX;    // PIXEL_FORMAT_RGBX must match mScreenBitmap format, e.g., Bitmap.Config.ARGB_8888

    private IFrameCallback mFrameCallBack = null;

    // CIS and/or Thermal mode
    private boolean mShowThermal;
    private boolean mShowCIS;

    private boolean isFlip;

    // Statistical text
    private TextView mTempTextView, mVersionTextView, mTempStd, mTempMin, mTempMax;

    // thermal shifting
    private boolean mShowThermalShiftEditMode;
    private TextView mThermalShiftLeft, mThermalShiftRight, mThermalShiftTop, mThermalShiftBottom;
    private int xThermalImageShift, yThermalImageShift;                                                     // amount of shift to thermal images in order to align with cis

    // Alarms
    private TextView mAlarmOverTemp, mAlarmUnderTemp;
    private Vibrator mVib;

    // Preferences
    private SharedPreferences mSP;
    private ConversionHelper mHelper;

    // Drag and Drop
    protected ImageView mCrosshair;
    private UVCCameraTextureView mCameraView;
    private RelativeLayout mActivityMain;

    private CheckBox mCheckBox;
    private ColorBar mColorBar;
    private int paletteNum = 1;

    private String tempStr = "";
    private Temp_stat temp_stat = new Temp_stat();

    //================================================================================

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }
    @Override
    public Object onRetainNonConfigurationInstance() {
        return mUSBMonitor;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate:");
        try {
            sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        setContentView(R.layout.activity_main);
        mActivityMain = findViewById(R.id.activity_main);
        mCameraView = findViewById(R.id.camera_view);
        mTopBar = findViewById(R.id.top_bar);

        mUVCCameraView = (CameraViewInterface)mCameraView;
        mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);

        // annotation view
        mAnnotationView = findViewById(R.id.annotation_view);
        mAnnotationView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);
        if (!DISPLAY_DEBUG_IMAGES) {
            mAnnotationView.setZOrderOnTop(true);
        }

        //Buttons
        mSettingsButton = findViewById(R.id.settings_button);
        mSettingsButton.setOnClickListener(mOnClickListener);
        settingsButton = (ImageButton) findViewById(R.id.settings_button);
        settingsButton.setColorFilter(Color.LTGRAY);
        mGalleryButton = findViewById(R.id.gallery_button);
        mGalleryButton.setOnClickListener(mOnClickListener);
        mFlipButton = findViewById(R.id.flip_button);
        mFlipButton.setOnClickListener(mOnClickListener);
        mFlipButton.setVisibility(View.INVISIBLE);
        galleryButton = (ImageButton) findViewById(R.id.gallery_button);
        galleryButton.setColorFilter(Color.LTGRAY);
        mDisplayModeButton = (ImageButton)findViewById(R.id.display_mode_button);
        mDisplayModeButton.setOnClickListener(mOnClickListener);
        mDisplayModeButton.setColorFilter(Color.LTGRAY);
        mDisplayModeButton.setVisibility(View.INVISIBLE);
        mRecordButton = (ImageButton) findViewById(R.id.record_button);
        mRecordButton.setColorFilter(Color.LTGRAY);
        mRecordButton.setOnClickListener(mOnClickListener);
        mRecordButton.setVisibility(View.INVISIBLE);
        mCaptureButton = (ImageButton)findViewById(R.id.capture_button);
        mCaptureButton.setColorFilter(Color.LTGRAY);
        mCaptureButton.setOnClickListener(mOnClickListener);
        mCaptureButton.setVisibility(View.INVISIBLE);
        mThermalShiftLeft = (TextView) findViewById(R.id.thermal_shift_left);
        mThermalShiftLeft.setVisibility(View.INVISIBLE);
        mThermalShiftRight = (TextView) findViewById(R.id.thermal_shift_right);
        mThermalShiftRight.setVisibility(View.INVISIBLE);
        mThermalShiftTop = (TextView) findViewById(R.id.thermal_shift_top);
        mThermalShiftTop.setVisibility(View.INVISIBLE);
        mThermalShiftBottom = (TextView) findViewById(R.id.thermal_shift_bottom);
        mThermalShiftBottom.setVisibility(View.INVISIBLE);
        mVersionTextView = (TextView) findViewById(R.id.version);
        mVersionTextView.setText("V " + BuildConfig.VERSION_NAME);

        mTempTextView = (TextView) findViewById(R.id.title_temperature);
        mTempStd = (TextView) findViewById(R.id.stdev);
        mTempMin = (TextView) findViewById(R.id.min);
        mTempMax = (TextView) findViewById(R.id.max);
        mAppTitleView = (TextView) findViewById(R.id.app_title);

        mAlarmOverTemp = findViewById(R.id.over_temp_alarm);
        mAlarmUnderTemp = findViewById(R.id.under_temp_alarm);
        mVib = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        resetAlarmAppearance();

        mCameraDetected = findViewById(R.id.no_camera);
        mCrosshair = findViewById(R.id.crosshair);

        mCameraView.setOnDragListener(new MyDragListener());
        mCrosshair.setTag("DRAG CROSSHAIR");

        mCheckBox = findViewById(R.id.adaptiveCheckbox);
        mCheckBox.setVisibility(View.INVISIBLE);

        mColorBar = findViewById(R.id.colorBar);

        mColorBar.setColorBarPosition(50);
        mColorBar.setBarHeight(7);
        mColorBar.setColorSeeds(R.array.basic_colors);

        //   Set onTouchListener
        mThermalShiftLeft.setOnTouchListener(new MyTouchListener());
        mThermalShiftRight.setOnTouchListener(new MyTouchListener());
        mThermalShiftTop.setOnTouchListener(new MyTouchListener());
        mThermalShiftBottom.setOnTouchListener(new MyTouchListener());
        mAppTitleView.setOnTouchListener(new MyTouchListener());
        mCameraView.setOnTouchListener(new MyTouchListener());
        mCrosshair.setOnTouchListener(new MyTouchListener());
        mColorBar.setOnTouchListener(new MyTouchListener());

        // get preference settings
        mSP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // create the ConversionHelper
        mHelper = new ConversionHelper(
                getString(R.string.degree_c_value),
                getString(R.string.degree_f_value),
                getString(R.string.kelvin_value));
        // Thermal/CIS modes
        mShowCIS = true;
        mShowThermal = false;
        isFlip = false;

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
                USE_SURFACE_ENCODER ? 0 : 1, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);

        mCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean input) {
                mCameraHandler.setParaletteHDR(input);
            }
        });

        // create handler to receive callback frames
        mFrameCallBack = new IFrameCallback() {
            @Override
            public void onFrame(ByteBuffer frame, int[] thermal_data) {
                temp_stat = Temp_stat.calculateStat(thermal_data);

                // save a copy of the bitmap
                mScreenBitmap.copyPixelsFromBuffer(frame);  // note, format from frame to mScreenBitmap must match

                Boolean isOverTempAlarmEnabled = mSP.getBoolean(
                        getString(R.string.pref_over_temp_alarm_enabled_key), false);
                Boolean isUnderTempAlarmEnabled = mSP.getBoolean(
                        getString(R.string.pref_under_temp_alarm_enabled_key), false);
                if (isOverTempAlarmEnabled || isUnderTempAlarmEnabled)
                {
                    final double upperThreshold = Double.valueOf(mSP.getString(
                            getString(R.string.pref_over_temp_alarm_value_key), ""));
                    final double lowerThreshold = Double.valueOf(mSP.getString(
                            getString(R.string.pref_under_temp_alarm_value_key), ""));
                    final String strC = getString(R.string.degree_c_value);
                    final String strUnit = mSP.getString(
                            getString(R.string.pref_temp_unit_key), "");
                    final boolean isConvertUnit = !strC.equals(strUnit);
                    boolean isOverTemp = false;
                    boolean isUnderTemp = false;
                    int idx = 0;
                    while ((idx < thermal_data.length) && (
                            (isOverTempAlarmEnabled && !isOverTemp) ||
                                    (isUnderTempAlarmEnabled && !isUnderTemp)))  {

                        double temp = (double)thermal_data[idx]/10.0; // currently in Celsius
                        temp = isConvertUnit?
                                mHelper.convertTemperatureByUnitString(temp, strC, strUnit): temp;
                        isOverTemp = isOverTempAlarmEnabled && (isOverTemp || (temp > upperThreshold));
                        isUnderTemp = isUnderTempAlarmEnabled && (isUnderTemp || (temp < lowerThreshold));
                        idx++;
                    }
                    boolean isTriggerAlarmOverTemp = (isOverTempAlarmEnabled && isOverTemp);
                    boolean isTriggerAlarmUnderTemp = (isUnderTempAlarmEnabled && isUnderTemp);
                    runOnUiThread(new AlarmTask(isTriggerAlarmOverTemp, isTriggerAlarmUnderTemp));
                } else {
                    mAlarmOverTemp.setBackgroundColor(Color.TRANSPARENT);
                    mAlarmUnderTemp.setBackgroundColor(Color.TRANSPARENT);
                }

                // detect face
                List<FaceDetect.DetectResult> detectResults = new ArrayList<>();
                Bitmap detectedImage = null;
                if (isDetectFace() && mShowThermal) {
                    FaceDetect faceDetect = new FaceDetect();

                    int[] expanded_thermal = UpsampleThermal.upsample(thermal_data, THERMAL_DATA_WIDTH, THERMAL_DATA_HEIGHT, THERMAL_EXPANDED_WIDTH, THERMAL_EXPANDED_HEIGHT, UpsampleThermal.UPSAMPLE_MODE.BILINEAR);
                    Mat[] maskAndContours = faceDetect.getMonoChromeImageEx( expanded_thermal );
                    Mat contourImage = maskAndContours[1];
                    detectResults = faceDetect.processContours( contourImage );
                    Log.d(TAG, Integer.toString(detectResults.size()));
                    //printThermalData(thermal_data, detectResults);
                    if (DISPLAY_DEBUG_IMAGES) {
                        Mat monoChromeImage = maskAndContours[0];
                        detectedImage = faceDetect.matToBitmap(monoChromeImage);
                    }
                }

                if (DISPLAY_DEBUG_IMAGES) {    // should only be used during development
                    // draw bitmap to screen
                    mAnnotationView.drawBitmap(detectedImage != null ? detectedImage : mScreenBitmap);
                } else {
                    // draw annotation rects
                    if (mShowThermal) {
                        mAnnotationView.drawAnnotationRects(detectResults, PREVIEW_WIDTH, PREVIEW_HEIGHT, THERMAL_EXPANDED_WIDTH, THERMAL_EXPANDED_HEIGHT, xThermalImageShift, yThermalImageShift, isFlip);
                    }
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (DEBUG) Log.d(TAG, "onStart:");

        if(!mUSBMonitor.isRegistered())
            mUSBMonitor.register();
        if (mUVCCameraView != null)
            mUVCCameraView.onResume();
        // register to listen for temperature changes
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UVCCameraHandler.TEMPERATURE_STRING);
        this.registerReceiver(TemperatureBroadcastReceiver, intentFilter);

        // init opencv lib
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        }

        usbmanager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> uMap = usbmanager.getDeviceList();
        if(!uMap.isEmpty()){
            mCameraDetected.setVisibility(View.INVISIBLE);
            if(!previewActive) {
                previewActive = true;
                Log.d(TAG, "onStart cameraDialog.showDialog");
                CameraDialog.getInstance().showDialog(MainActivity.this);
                // re-start frame callback
                mCameraHandler.setFrameCallback(mFrameCallBack, frameCallBackFormat);
            }
        }else{
            mCameraDetected.setVisibility(View.VISIBLE);
            UI_setVisability(false);
            resetAlarmAppearance();
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume:");
        mShowThermalShiftEditMode = false;
        xThermalImageShift = 0;
        yThermalImageShift = 0;
        mThermalShiftLeft.setVisibility(View.INVISIBLE);
        mThermalShiftRight.setVisibility(View.INVISIBLE);
        mThermalShiftTop.setVisibility(View.INVISIBLE);
        mThermalShiftBottom.setVisibility(View.INVISIBLE);
        if(!previewActive) {
            previewActive = true;
            mCameraHandler.setFrameCallback(mFrameCallBack, frameCallBackFormat);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        if (DEBUG) Log.d(TAG, "onPause:");
        // stop frame callback
        if (previewActive) {
            // stop iFrameCallback
            mCameraHandler.setFrameCallback(null, -1);
            previewActive = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (DEBUG) Log.d(TAG, "onStop:");
        mCameraHandler.close();
        if (mUVCCameraView != null)
            mUVCCameraView.onPause();
        this.unregisterReceiver(TemperatureBroadcastReceiver);
        if(mUSBMonitor.isRegistered())
            mUSBMonitor.unregister();
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy:");
        mCameraHandler.close();
        UI_setVisability(false);
        resetAlarmAppearance();
        // unregister temperature listener
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        super.onDestroy();
    }

    // Listener
    //================================================================================
    // USB connection
    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        //when a USB device is plugged in
        @Override
        public void onAttach(final UsbDevice device) {
            Log.d(TAG, "USBMonitor onAttach");
            if(!previewActive) {
                // modify UI from UI thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCameraDetected.setVisibility(View.INVISIBLE);
                    }
                });
                previewActive = true;
                Log.d(TAG, "onAttach cameraDialog.showDialog");
                CameraDialog.getInstance().showDialog(MainActivity.this);
            }
        }

        //after a USB device has been plugged in and connected to
        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            Log.d(TAG, "USBMonitor onConnect:");
            try {
                if(mCameraHandler != null && !mCameraHandler.isOpened()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            UI_setVisability(true);
                            mCameraDetected.setVisibility(View.INVISIBLE);
                        }
                    });
                    mCameraHandler.open(ctrlBlock);
                    sleep(2500);
                    // Init Meridian Params
                    mCameraHandler.SetMeridianParams(getColorPaletteNum(), 0, 0);
                    startPreview();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            } catch (NullPointerException ne) {
                ne.printStackTrace();
                return;
            }
            previewActive = true;
        }

        //when a USB device has been disconnected from
        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            Log.d(TAG, "USBMonitor onDisconnect:");
            if (mCameraHandler != null) {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        if (mCameraHandler != null) {
                            // stop iFrameCallback
                            mCameraHandler.setFrameCallback(null, -1);
                            mCameraHandler.close();
                        }
                    }
                }, 0);
                // modify UI from UI thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCameraDetected.setVisibility(View.VISIBLE);
                        if (mAnnotationView != null) {
                            mAnnotationView.clearAnnotations();
                        }
                        if (mTempTextView != null) {
                            mTempTextView.setVisibility(View.INVISIBLE);
                        }
                    }
                });
                previewActive = false;
            }
        }
        @Override
        public void onDettach(final UsbDevice device) {
            Log.d(TAG, "USBMonitor onDettach:");
            // reset CameraDialog
            CameraDialog.resetDialog();
            previewActive = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UI_setVisability(false);
                }
            });
        }

        @Override
        public void onCancel(final UsbDevice device) {
            Log.d(TAG, "USBMonitor onCancel:");
        }
    };

    // onClick event handler list
    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(@NonNull final View view) {
            Log.d("OnClickListener",view.toString());
            switch (view.getId()) {
                case R.id.capture_button:
                    if (mCameraHandler.isOpened()) {
                        //take photo
                        if (checkPermissionWriteExternalStorage()) {
                            mCameraHandler.setCaptureElement(tempStr, mCrosshair,isFlip);
                            mCameraHandler.captureStill();
                        }
                    }
                    break;
                case R.id.record_button:
                    if(mCameraHandler.isOpened()){
                        //record video
                        if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
                            if (!mCameraHandler.isRecording()) {
                                mRecordButton.setColorFilter(0xffff0000);	// turn red
                                mCameraHandler.startRecording();
                            } else {
                                mRecordButton.setColorFilter(Color.LTGRAY);	// return to default color
                                mCameraHandler.stopRecording();
                                // restart callback
                                mCameraHandler.setFrameCallback(mFrameCallBack, frameCallBackFormat);
                            }
                        }
                    }
                    break;
                case R.id.settings_button:
                    openSettings();
                    break;
                case R.id.gallery_button:
                    openGallery();
                    break;
                case R.id.display_mode_button:
                    toggleCISThermalModes();
                    break;
                case R.id.flip_button:
                    // Button layout changing
                    RelativeLayout.LayoutParams params_bleft = (RelativeLayout.LayoutParams) mThermalShiftLeft.getLayoutParams();
                    RelativeLayout.LayoutParams params_bright = (RelativeLayout.LayoutParams) mThermalShiftRight.getLayoutParams();
                    // CIS Image view
                    mCameraView.setScaleX(-1 * mCameraView.getScaleX());
                    if(mCameraView.getScaleX() < 0) {
                        params_bleft.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                        params_bright.addRule(RelativeLayout.ALIGN_PARENT_LEFT);

                        params_bleft.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                        params_bright.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);

                    } else {
                        params_bleft.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                        params_bright.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

                        params_bleft.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                        params_bright.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    }
                    // Annotation View
                    isFlip = !isFlip;

                    // Crosshair mark
                    if(mCrosshair.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
                        Log.d(TAG, Float.toString(mCameraView.getWidth()));
                        mCrosshair.setX(mCameraView.getWidth() - (mCrosshair.getX() - mTopBar.getWidth()) + mCrosshair.getWidth());
                    }
                    else
                        mCrosshair.setX(mCameraView.getWidth() - (mCrosshair.getX() + mCrosshair.getWidth()));
                    break;
            }
        }
    };

    /**
     * onTouch event handler, mainly for UVCCameraTextureView
     */
    private final class MyTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            final int id = view.getId();
            if(id == R.id.camera_view || id == R.id.crosshair) {
                if (motionEvent.getAction() != MotionEvent.ACTION_DOWN) {
                    return false;
                }
                int pointX = (int) motionEvent.getX() - mCrosshair.getWidth() / 2;
                int pointY = (int) motionEvent.getY() - mCrosshair.getHeight() / 2;
                //mPos.setText("(" + motionEvent.getRawX() + "," + motionEvent.getRawY() + ")");
                View focusView = view;
                mCrosshair.setVisibility(View.INVISIBLE);
                // focusView setting
                if (view instanceof UVCCameraTextureView) {
                    //mTextView.setText("ViewGroup instance onTouch()");
                    focusView = mCrosshair;
                    mCrosshair.setX(pointX);
                    mCrosshair.setY(pointY);
                }

                // Setup drag shadow
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    ClipData data = ClipData.newPlainText("", "");
                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(focusView);
                    focusView.startDrag(data, shadowBuilder, focusView, 0);
                }
            }
            else {
                switch (id) {
                    case R.id.colorBar:
                        if (motionEvent.getAction() != MotionEvent.ACTION_DOWN) {
                            return false;
                        }
                        paletteNum = (paletteNum + 1) % getResources().getStringArray(R.array.pref_color_palette_entries).length;
                        setColorBar(paletteNum);
                        //mCameraHandler.stopPreview();
                        changePreference(getString(R.string.pref_color_palette_key),Integer.toString(paletteNum));
                        mCameraHandler.SetMeridianParams(paletteNum, xThermalImageShift, yThermalImageShift);
                        break;
                    case R.id.app_title:
                        if (motionEvent.getAction() != MotionEvent.ACTION_DOWN) {
                            return false;
                        }
                        if (mShowCIS && mShowThermal) {
                            mShowThermalShiftEditMode = !mShowThermalShiftEditMode;

                            if (mShowThermalShiftEditMode) {
                                mThermalShiftLeft.setText(Integer.toString(xThermalImageShift));
                                mThermalShiftLeft.setVisibility(View.VISIBLE);
                                mThermalShiftRight.setText(Integer.toString(xThermalImageShift));
                                mThermalShiftRight.setVisibility(View.VISIBLE);
                                mThermalShiftTop.setText(Integer.toString(yThermalImageShift));
                                mThermalShiftTop.setVisibility(View.VISIBLE);
                                mThermalShiftBottom.setText(Integer.toString(yThermalImageShift));
                                mThermalShiftBottom.setVisibility(View.VISIBLE);
                            } else {
                                // save current shift values to Preferences
                                changePreference("x_thermal_shift", xThermalImageShift);
                                changePreference("y_thermal_shift", yThermalImageShift);

                                mThermalShiftLeft.setVisibility(View.INVISIBLE);
                                mThermalShiftRight.setVisibility(View.INVISIBLE);
                                mThermalShiftTop.setVisibility(View.INVISIBLE);
                                mThermalShiftBottom.setVisibility(View.INVISIBLE);
                            }
                        }
                        break;
                    case R.id.thermal_shift_left:
                        xThermalImageShift--;
                        if (xThermalImageShift <= -PREVIEW_WIDTH) {
                            xThermalImageShift = -PREVIEW_WIDTH + 1;
                        }
                        mCameraHandler.SetMeridianParams(getColorPaletteNum(), xThermalImageShift, yThermalImageShift);
                        mThermalShiftLeft.setText(Integer.toString(xThermalImageShift));
                        mThermalShiftRight.setText(Integer.toString(xThermalImageShift));
                        Log.d(TAG, "Thermal shift x by -1 to "+xThermalImageShift);
                        break;
                    case R.id.thermal_shift_right:
                        xThermalImageShift++;
                        if (xThermalImageShift >= PREVIEW_WIDTH) {
                            xThermalImageShift = PREVIEW_WIDTH - 1;
                        }
                        mCameraHandler.SetMeridianParams(getColorPaletteNum(), xThermalImageShift, yThermalImageShift);
                        mThermalShiftLeft.setText(Integer.toString(xThermalImageShift));
                        mThermalShiftRight.setText(Integer.toString(xThermalImageShift));
                        Log.d(TAG, "Thermal shift x by +1 to "+xThermalImageShift);
                        break;
                    case R.id.thermal_shift_top:
                        yThermalImageShift--;
                        if (yThermalImageShift <= -PREVIEW_HEIGHT) {
                            yThermalImageShift = -PREVIEW_HEIGHT + 1;
                        }
                        mCameraHandler.SetMeridianParams(getColorPaletteNum(), xThermalImageShift, yThermalImageShift);
                        mThermalShiftTop.setText(Integer.toString(yThermalImageShift));
                        mThermalShiftBottom.setText(Integer.toString(yThermalImageShift));
                        Log.d(TAG, "Thermal shift y by -1 to "+yThermalImageShift);
                        break;
                    case R.id.thermal_shift_bottom:
                        yThermalImageShift++;
                        if (yThermalImageShift >= PREVIEW_HEIGHT) {
                            yThermalImageShift = PREVIEW_HEIGHT - 1;
                        }
                        mCameraHandler.SetMeridianParams(getColorPaletteNum(), xThermalImageShift, yThermalImageShift);
                        mThermalShiftTop.setText(Integer.toString(yThermalImageShift));
                        mThermalShiftBottom.setText(Integer.toString(yThermalImageShift));
                        Log.d(TAG, "Thermal shift y by +1 to "+yThermalImageShift);
                        break;
                }
            }
            // The flag saying whether keep this event after running the trigger codes, true means will be happened again.
            return true;
        }
    }

    /**
     * onDrag event handler, mainly for crosshair
     */
    private final class MyDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            final View view = (View) event.getLocalState();
            ViewGroup owner = (ViewGroup) view.getParent();
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
                    break;
                case DragEvent.ACTION_DRAG_LOCATION:
                    if(previewActive) {
                        int xVal = (int) event.getX();
                        int yVal = (int) event.getY();
                        tempStr = getTempAtPoint(xVal, yVal, v.getWidth(), v.getHeight(), !isShowCelsius());
                        mTempTextView.setText(String.valueOf(tempStr));
                    }
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    break;
                case DragEvent.ACTION_DROP:
                    if(view.getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
                        // Crosshair mark UI position
                        if (!isFlip)
                            view.setX((event.getX() - view.getWidth() / 2));
                        else
                            view.setX((v.getWidth() - event.getX() - view.getWidth() / 2));
                        view.setY((event.getY() - view.getHeight() / 2) + (owner.getHeight() - v.getHeight()) / 2);
                    } else if(view.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
                        if (!isFlip) {
                            view.setX((event.getX() - view.getWidth() / 2) + mTopBar.getWidth());
                        }
                        else
                            view.setX(v.getWidth() - event.getX() - view.getWidth() / 2 + mTopBar.getWidth());
                        view.setY((event.getY() - view.getHeight() / 2) + (owner.getHeight() - v.getHeight()) / 2);
                    } else {
                        Log.d("MyDragListener_ondrag()","Orientation undefined");
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    view.post(new Runnable() {                  // Has to be Runnable()
                        public void run() {
                            view.setVisibility(View.VISIBLE);
                        }
                    });
                    break;
                default:
                    break;
            }
            return true;
        }
    }
//================================================================================
    // Temperature trigger related functions
    /**
     * Task to run on UI thread for alarms
     */
    class AlarmTask implements Runnable {
        boolean mIsTriggerAlarmOverTemp, mIsTriggerAlarmUnderTemp;
        AlarmTask(boolean isTriggerOver, boolean isTriggerUnder) {
            mIsTriggerAlarmOverTemp = isTriggerOver;
            mIsTriggerAlarmUnderTemp = isTriggerUnder;
        }
        public void run() {
            mAlarmOverTemp.setBackgroundColor(mIsTriggerAlarmOverTemp? Color.RED : Color.TRANSPARENT);
            mAlarmUnderTemp.setBackgroundColor(mIsTriggerAlarmUnderTemp? Color.CYAN : Color.TRANSPARENT);
            if (mIsTriggerAlarmOverTemp || mIsTriggerAlarmUnderTemp) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    mVib.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                else
                    mVib.vibrate(200);
            }
        }
    }

    // Temperature text broadcast receiver
    private BroadcastReceiver TemperatureBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int view_width = mCameraView.getWidth();
            final int view_height = mCameraView.getHeight();
            final int layout_height = mActivityMain.getHeight();
            final Temp_stat tmpStat = new Temp_stat(temp_stat);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                if(mCameraView.getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
                    if (!isFlip)
                        tempStr = getTempAtPoint((int) (mCrosshair.getX() + mCrosshair.getWidth() / 2), (int) (mCrosshair.getY() + mCrosshair.getHeight() / 2 - (layout_height - view_height) / 2), view_width, view_height, !isShowCelsius());
                    else
                        tempStr = getTempAtPoint((int) (view_width - (mCrosshair.getX() + mCrosshair.getWidth() / 2)), (int) (mCrosshair.getY() + mCrosshair.getHeight() / 2 - (layout_height - view_height) / 2), view_width, view_height, !isShowCelsius());
                } else {
                    if (!isFlip)
                        tempStr = getTempAtPoint((int) (mCrosshair.getX() + mCrosshair.getWidth() / 2 - mTopBar.getWidth()
                        ), (int) (mCrosshair.getY() + mCrosshair.getHeight() / 2 - (layout_height - view_height) / 2), view_width, view_height, !isShowCelsius());
                    else
                        tempStr = getTempAtPoint((int) (view_width - (mCrosshair.getX() + mCrosshair.getWidth() / 2) + mTopBar.getWidth()), (int) (mCrosshair.getY() + mCrosshair.getHeight() / 2 - (layout_height - view_height) / 2), view_width, view_height, !isShowCelsius());
                }
                mTempTextView.setText(String.valueOf(tempStr));

                mTempStd.setText("Std: " + String.format("%.2f", tmpStat.getStdev()));
                mTempMin.setText("Min: " + String.format("%.1f",Double.valueOf(Float.toString(tmpStat.getMin())) / 10) + "\u00B0C");
                mTempMax.setText("Max: " + String.format("%.1f",Double.valueOf(Float.toString(tmpStat.getMax())) / 10) + "\u00B0C");
                }
            });
        }
    };

    /**
     * to access from CameraDialog
     * @return
     */
    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (DEBUG) Log.v(TAG, "onDialogResult:canceled=" + canceled);
    }

    //================================================================================
    private boolean isActive() {
        return mCameraHandler != null && mCameraHandler.isOpened();
    }

    private boolean checkSupportFlag(final int flag) {
        return mCameraHandler != null && mCameraHandler.checkSupportFlag(flag);
    }

    private int getValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.getValue(flag) : 0;
    }

    private int setValue(final int flag, final int value) {
        return mCameraHandler != null ? mCameraHandler.setValue(flag, value) : 0;
    }

    private int resetValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.resetValue(flag) : 0;
    }

    private void setColorBar(int palette) {
        switch (palette) {
            case 2:
                mColorBar.setColorSeeds(R.array.heatiron_colors);
                break;
            case 3:
                mColorBar.setColorSeeds(R.array.viridis_colors);
                break;
            case 4:
                mColorBar.setColorSeeds(R.array.magma_colors);
                break;
            case 5:
                mColorBar.setColorSeeds(R.array.parula_colors);
                break;
            case 6:
                mColorBar.setColorSeeds(R.array.bezier_colors);
                break;
            case 7:
                mColorBar.setColorSeeds(R.array.brewerblues_colors);
                break;
            case 8:
                mColorBar.setColorSeeds(R.array.bw_colors);
                break;
            case 9:
                mColorBar.setColorSeeds(R.array.autumn_colors);
                break;
            case 10:
                mColorBar.setColorSeeds(R.array.cool_colors);
                break;
            case 11:
                mColorBar.setColorSeeds(R.array.hsv_colors);
                break;
            case 12:
                mColorBar.setColorSeeds(R.array.jet_colors);
                break;
            case 13:
                mColorBar.setColorSeeds(R.array.spring_colors);
                break;
            default:
                mColorBar.setColorSeeds(R.array.basic_colors);
        }
    }

    private int getColorPaletteNum() {
        String s1 = mSP.getString("pref_color_palette", getString(R.string.pref_color_palette_default));
        try {
            paletteNum =  Integer.parseInt(s1);
        } catch (NullPointerException npe) {
            Log.d("Null pointer: %s",npe.getMessage());
        }
        // Update color bar for any changes of palette number
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setColorBar(paletteNum);
            }
        });
        return paletteNum;
    }

    /**
     *
     * @param x  the x coordinate of the selected pixel
     * @param y  the y coordinate of the selected pixel
     * @return   the String representation of the selected pixel's temperature based on color
     */
    public String getTempAtPoint(int x, int y, int viewWidth, int viewHeight, boolean useFahrenheit){
        // calculate actualX, actualY that correspond to the correct pixel on the image
        int actualX =  (int) (x * (float) PREVIEW_WIDTH / (float) viewWidth );
        int actualY =  (int) (y * (float) PREVIEW_HEIGHT / (float) viewHeight );

        // check if x,y is inside mScreenBitmap
        if (actualX >= PREVIEW_WIDTH) {
            actualX = PREVIEW_WIDTH - 1;
        }
        if (actualY >= PREVIEW_HEIGHT) {
            actualY =PREVIEW_HEIGHT - 1;
        }

        float temp = mCameraHandler.getTemperature(actualX, actualY);

        String suffix;
        if (useFahrenheit) {
            temp = temp * 9.0f/5.0f + 32;
            suffix = "F";
        } else {
            suffix = "C";
        }

        return String.format("%2.1f\u00B0"+suffix, temp);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void UI_setVisability(boolean visable) {
        previewActive = visable;
        if(visable) {
            mCaptureButton.setVisibility(View.VISIBLE);
            mRecordButton.setVisibility(View.VISIBLE);
            mDisplayModeButton.setVisibility(View.VISIBLE);
            mTempTextView.setVisibility(View.VISIBLE);
            mCrosshair.setVisibility(View.VISIBLE);
            mAlarmOverTemp.setVisibility(View.VISIBLE);
            mAlarmUnderTemp.setVisibility(View.VISIBLE);
            mFlipButton.setVisibility(View.VISIBLE);
            mCheckBox.setVisibility(View.VISIBLE);
            mColorBar.setVisibility(View.VISIBLE);
            mTempMin.setVisibility(View.VISIBLE);
            mTempMax.setVisibility(View.VISIBLE);
            mTempStd.setVisibility(View.VISIBLE);
        }
        else {
            mCaptureButton.setVisibility(View.INVISIBLE);
            mRecordButton.setVisibility(View.INVISIBLE);
            mDisplayModeButton.setVisibility(View.INVISIBLE);
            mTempTextView.setVisibility(View.INVISIBLE);
            mCrosshair.setVisibility(View.INVISIBLE);
            mAlarmOverTemp.setVisibility(View.INVISIBLE);
            mAlarmUnderTemp.setVisibility(View.INVISIBLE);
            mFlipButton.setVisibility(View.INVISIBLE);
            mCheckBox.setVisibility(View.INVISIBLE);
            mColorBar.setVisibility(View.INVISIBLE);
            mTempMin.setVisibility(View.INVISIBLE);
            mTempMax.setVisibility(View.INVISIBLE);
            mTempStd.setVisibility(View.INVISIBLE);
        }
    }

    private void resetAlarmAppearance() {
        mAlarmOverTemp.setVisibility(View.INVISIBLE);
        mAlarmOverTemp.setBackgroundColor(Color.TRANSPARENT);
        mAlarmUnderTemp.setVisibility(View.INVISIBLE);
        mAlarmUnderTemp.setBackgroundColor(Color.TRANSPARENT);
    }

    private final void openSettings(){
        Intent i = new Intent(this, SettingsActivity.class);
        startActivity(i);
    }

    private final void openGallery(){
        Intent galleryIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY);
        startActivity(galleryIntent);
    }

    private final void toggleCISThermalModes(){
        if (mShowCIS && mShowThermal) {
            mShowCIS = true;
            mShowThermal = false;
            //mCrosshair.setVisibility(View.INVISIBLE);
            // save current shift values to Preferences
            changePreference("x_thermal_shift", xThermalImageShift);
            changePreference("y_thermal_shift", yThermalImageShift);
            // disable edit mode
            if (mShowThermalShiftEditMode) {
                mShowThermalShiftEditMode = false;
                mThermalShiftLeft.setVisibility(View.INVISIBLE);
                mThermalShiftRight.setVisibility(View.INVISIBLE);
                mThermalShiftTop.setVisibility(View.INVISIBLE);
                mThermalShiftBottom.setVisibility(View.INVISIBLE);
            }
        } else if (mShowCIS && mShowThermal == false) {
            mShowCIS = false;
            mShowThermal = true;
        } else if (mShowCIS == false && mShowThermal) {
            mShowCIS = true;
            mShowThermal = true;
        } else {
            // Basically will not happened
            mShowCIS = true;
            mShowThermal = false;
        }

        // if cis+thermal, add thermal shifts
        if (mShowCIS && mShowThermal) {
            xThermalImageShift = mSP.getInt("x_thermal_shift", 0);
            yThermalImageShift = mSP.getInt("y_thermal_shift", 0);
        } else {
            xThermalImageShift = 0;
            yThermalImageShift = 0;
        }

        Log.d(TAG, "Toggle to showCIS="+mShowCIS+" showThermal="+mShowThermal+" enableGrayScaleMode="+getColorPaletteNum()
                +" xShift="+xThermalImageShift+" yShift="+yThermalImageShift);
        if (mCameraHandler != null){
            mCameraHandler.setThermalEnable(mShowThermal);
            mCameraHandler.setCmosEnable(mShowCIS);
            mCameraHandler.SetMeridianParams(getColorPaletteNum(), xThermalImageShift, yThermalImageShift);
        }

        // remember in preferences TODO
        //changePreference("show_cis", mShowCIS);
        //changePreference("show_thermal", mShowThermal);

        // hide/show temperature
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mTempTextView != null) {
                    mTempTextView.setVisibility(View.VISIBLE);
                }
                // clear all previous annotations
                if (mAnnotationView != null) {
                    mAnnotationView.clearAnnotations();
                }
                if(mShowThermal)
                    mCheckBox.setVisibility(View.VISIBLE);
                else
                    mCheckBox.setVisibility(View.INVISIBLE);
            }
        });

    }

    private void startPreview() {
        final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
        if(st != null) {
            mCameraHandler.startPreview(new Surface(st));

            // set frame callback
            mCameraHandler.setFrameCallback(mFrameCallBack, frameCallBackFormat);
            mCameraHandler.setMainLayoutHeight(mActivityMain.getHeight());          // Pass main layout height to cameraHandler class
            // show buttons
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UI_setVisability(true);
                }
            });
            previewActive = true;

            if (mCameraHandler != null) {
                mCameraHandler.setThermalEnable(mShowThermal);
                mCameraHandler.setCmosEnable(mShowCIS);
            }
        }
        else
            Log.d("startPreview()","SurfaceTexture is null.");
    }

    // For sharedPreference
    //================================================================================
    // TODO 1: throw exception if the key is not found in SharedPreference
    // TODO 2: what about Kelvin?
    private boolean isShowCelsius() {
        return getString(R.string.degree_c_value).equals(mSP.getString(
                getString(R.string.pref_temp_unit_key), getString(R.string.degree_c_value)));
    }

    // TODO: throw exception if the key is not found in SharedPreference
    private boolean isDetectFace() {
        return mSP.getBoolean(
                getString(R.string.pref_face_detect_enabled_key), false);
    }

    private void changePreference(String key, int value) {
        SharedPreferences.Editor editor = mSP.edit();
        editor.putInt(key, value);
        editor.commit();
    }
    // Overloading
    private void changePreference(String key, String value) {
        SharedPreferences.Editor editor = mSP.edit();
        editor.putString(key, value);
        editor.commit();
    }

    // For OpenCV initialization
    //================================================================================
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.d(TAG, "OpenCV intialized successfully");

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
}
