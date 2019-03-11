package com.meridianinno.senxorviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;

import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.meridianinno.facedetection.FaceDetect;
import com.serenegiant.encoder.IVideoEncoder;
import com.serenegiant.widget.CameraViewInterface;

import java.util.List;

import static com.meridianinno.facedetection.FaceDetect.MatBuffer;

/**
 * Created by frankMac on 1/29/18.
 */

public class AnnotationView extends SurfaceView implements SurfaceHolder.Callback, CameraViewInterface {

    private final String TAG = AnnotationView.class.getName();

    private final SurfaceHolder mHolder;
    private final Context mContext;
    private final Paint paint;
    private Surface mAnnotationSurface = null;

    private double mRequestedAspect = -1.0;
    private boolean mHasSurface;
    private Callback mCallback;

    public AnnotationView(Context context) {
        this(context, null, 0);
    }

    public AnnotationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnnotationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mHolder = this.getHolder();
        mContext = context;

        mHolder.setFormat(PixelFormat.TRANSPARENT);
        mHolder.addCallback(this);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
    }

    public Surface getSurface() {
        return mAnnotationSurface;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    public void drawBitmap(Bitmap bitmap) {
        if (mAnnotationSurface != null && mAnnotationSurface.isValid()) {
            final Canvas canvas = mHolder.lockCanvas();
            if (canvas != null) {
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, canvas.getWidth(), canvas.getHeight(), true);;
                canvas.drawBitmap(scaledBitmap, 0, 0, new Paint());
                mHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    public void drawAnnotationRects(List<FaceDetect.DetectResult> detectResults, int prevWidth, int prevHeight, int thermalWidth, int thermalHeight, int xThermalImageShift, int yThermalImageShift, boolean isFlip) {
        if (mAnnotationSurface != null && mAnnotationSurface.isValid()) {
            final Canvas canvas = mHolder.lockCanvas();
            if (canvas != null) {
                // erase previous rects
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);

                for (FaceDetect.DetectResult detectResult : detectResults) {
                    if (!detectResult.isFace) {
                        continue;
                    }

                    final float sx = canvas.getWidth() / (float) prevWidth;
                    final float sy = canvas.getHeight() / (float) prevHeight;
                    final float xThermalScale = prevWidth/thermalWidth;      // 640/32, thermal has 1:1 aspect ratio
                    final float yThermalScale = prevHeight/thermalHeight;      // 480/32, thermal has 1:1 aspect ratio


                    // convert to srcWidth/srcHeight coordinates, e.g., 640 x 480 coordinates
                    int left = (int)(((float)detectResult.rect.x - MatBuffer + 0.5) * xThermalScale) + xThermalImageShift;
                    int top =  (int)(((float)detectResult.rect.y - MatBuffer + 0.5) * yThermalScale) + yThermalImageShift;
                    int right = left + (int)(detectResult.rect.width * xThermalScale);
                    int bottom = top + (int)(detectResult.rect.height * yThermalScale);

                    // convert to canvas coordinates
                    left = (int) (left * sx + 0.5);
                    top = (int) (top * sy + 0.5);
                    right = (int) (right * sx + 0.5);
                    bottom = (int) (bottom * sy + 0.5);

                    if(isFlip) {
                        int temp = left;
                        left = canvas.getWidth() - right;
                        right = canvas.getWidth() - temp;
                    }

                    // draw
                    //Log.d(TAG, "draw rect "+left+", "+top+", "+right+", "+bottom);
                    canvas.drawRect(left, top, right, bottom, paint);
                }

                mHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    public void clearAnnotations() {
        if (mAnnotationSurface != null && mAnnotationSurface.isValid()) {
            final Canvas canvas = mHolder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);

                mHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    // SurfaceHolder.Callback methods
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mAnnotationSurface = mHolder.getSurface();
        mHasSurface = mAnnotationSurface != null ? true : false;
        if (mCallback != null) {
            mCallback.onSurfaceCreated(this, getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mAnnotationSurface = mHolder.getSurface();
        if (mCallback != null) {
            mCallback.onSurfaceChanged(this, getSurface(), width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mAnnotationSurface = null;
        mHasSurface = false;
        if (mCallback != null) {
            mCallback.onSurfaceDestroy(this, getSurface());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mRequestedAspect > 0) {
            int initialWidth = MeasureSpec.getSize(widthMeasureSpec);
            int initialHeight = MeasureSpec.getSize(heightMeasureSpec);

            final int horizPadding = getPaddingLeft() + getPaddingRight();
            final int vertPadding = getPaddingTop() + getPaddingBottom();
            initialWidth -= horizPadding;
            initialHeight -= vertPadding;

            final double viewAspectRatio = (double)initialWidth / initialHeight;
            final double aspectDiff = mRequestedAspect / viewAspectRatio - 1;

            if (Math.abs(aspectDiff) > 0.01) {
                if (aspectDiff > 0) {
                    // width priority decision
                    initialHeight = (int) (initialWidth / mRequestedAspect);
                } else {
                    // height priority decison
                    initialWidth = (int) (initialHeight * mRequestedAspect);
                }
                initialWidth += horizPadding;
                initialHeight += vertPadding;
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(initialWidth, MeasureSpec.EXACTLY);
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(initialHeight, MeasureSpec.EXACTLY);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    //
    //  CameraViewInterface
    //
    @Override
    public void setAspectRatio(double aspectRatio) {
        if (aspectRatio < 0) {
            throw new IllegalArgumentException();
        }
        if (mRequestedAspect != aspectRatio) {
            mRequestedAspect = aspectRatio;
            requestLayout();
        }
   }

    @Override
    public void setAspectRatio(int width, int height) {
        setAspectRatio(width / (double)height);
    }

    @Override
    public double getAspectRatio() {
        return mRequestedAspect;
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public SurfaceTexture getSurfaceTexture() {
        return null;
    }

    @Override
    public boolean hasSurface() {
        return mHasSurface;
    }

    @Override
    public void setVideoEncoder(IVideoEncoder encoder) {

    }

    @Override
    public Bitmap captureStillImage() {
        return null;
    }
}