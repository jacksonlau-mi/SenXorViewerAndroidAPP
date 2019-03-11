/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameracommon;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.ImageView;

import com.serenegiant.encoder.MediaAudioEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaMuxerWrapper;
import com.serenegiant.encoder.MediaSurfaceEncoder;
import com.serenegiant.encoder.MediaVideoBufferEncoder;
import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.TemperatureCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.CameraViewInterface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.serenegiant.usb.UVCCamera.PIXEL_FORMAT_NV21;
import static com.serenegiant.usb.UVCCamera.PIXEL_FORMAT_RGB565;
import static com.serenegiant.usb.UVCCamera.PIXEL_FORMAT_RGBX;

abstract class AbstractUVCCameraHandler extends Handler {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "AbsUVCCameraHandler";

	public static final String TEMPERATURE_STRING = "com.meridianinno.senxorview.UPDATE_TEMP";

	// Thermal image size
	protected static int THERMAL_WIDTH = 32;
	protected static int THERMAL_HEIGHT = 32;

	public interface CameraCallback {
		public void onOpen();
		public void onClose();
		public void onStartPreview();
		public void onStopPreview();
		public void onStartRecording();
		public void onStopRecording();
		public void onError(final Exception e);
	}

	private static final int MSG_OPEN = 0;
	private static final int MSG_CLOSE = 1;
	private static final int MSG_PREVIEW_START = 2;
	private static final int MSG_PREVIEW_STOP = 3;
	private static final int MSG_CAPTURE_STILL = 4;
	private static final int MSG_CAPTURE_START = 5;
	private static final int MSG_CAPTURE_STOP = 6;
	private static final int MSG_MEDIA_UPDATE = 7;
	private static final int MSG_RELEASE = 9;
	private static final int MSG_SET_FRAME_CALLBACK = 10;

	private final WeakReference<AbstractUVCCameraHandler.CameraThread> mWeakThread;
	private volatile boolean mReleased;
	protected static volatile String display_temp;
	private static int mainLayoutHeight;
    private boolean isThermalEnable = false, isCmosEnable = false;
    private static ImageView mCrossHair;
    private static boolean misFlip;

    // Meridian mods
	public float getTemperature(int posX, int posY) {
		final CameraThread thread = mWeakThread.get();
		if (thread != null) {
			return thread.getTemperature(posX, posY);
		} else {
			return -1;
		}
	}
    // TODO: Find a better way to share MainActivity rss
	// Get rss from MainActivity.java (Really bad style at this moment... instead maybe we can make a resource class)
    public void setMainLayoutHeight(int height) {
        mainLayoutHeight = height;
    }

    public void setCaptureElement(String temp, ImageView CrossHair, boolean isFlip) {
		display_temp = temp;
		mCrossHair = CrossHair;
		misFlip = isFlip;
	}


	protected AbstractUVCCameraHandler(final CameraThread thread) {
		mWeakThread = new WeakReference<CameraThread>(thread);
	}

	public void setThermalEnable(boolean enable){
		this.isThermalEnable = enable;
		final CameraThread thread = mWeakThread.get();
		thread.setmIsThermalEnable(enable);
    }

	public void setCmosEnable(boolean enable){
		this.isCmosEnable = enable;
		final CameraThread thread = mWeakThread.get();
		thread.setmIsCmosEnable(enable);
	}

	public void SetMeridianParams(int numPalette, int xShift, int yShift){
		final CameraThread thread = mWeakThread.get();
		thread.SetMeridianParams(numPalette, xShift, yShift);
	}


	public void setParaletteHDR(boolean input){
		final CameraThread thread = mWeakThread.get();
		thread.setParaletteHDR(input);
	}

	public int getWidth() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getWidth() : 0;
	}

	public int getHeight() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getHeight() : 0;
	}

	public boolean isOpened() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isCameraOpened();
	}

	public boolean isPreviewing() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isPreviewing();
	}

	public boolean isRecording() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isRecording();
	}

	public boolean isEqual(final UsbDevice device) {
		final CameraThread thread = mWeakThread.get();
		return (thread != null) && thread.isEqual(device);
	}

	protected boolean isCameraThread() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && (thread.getId() == Thread.currentThread().getId());
	}

	protected boolean isReleased() {
		final CameraThread thread = mWeakThread.get();
		return mReleased || (thread == null);
	}

	protected void checkReleased() {
		if (isReleased()) {
			throw new IllegalStateException("already released");
		}
	}

	public void open(final USBMonitor.UsbControlBlock ctrlBlock) {
		checkReleased();
		sendMessage(obtainMessage(MSG_OPEN, ctrlBlock));
	}

	public void close() {
		if (DEBUG) Log.v(TAG, "close:");
		if (isOpened()) {
			stopPreview();
			sendEmptyMessage(MSG_CLOSE);
		}
		if (DEBUG) Log.v(TAG, "close:finished");
	}

	public void resize(final int width, final int height) {
		checkReleased();
		throw new UnsupportedOperationException("does not support now");
	}

	protected void startPreview(final Object surface) {
		checkReleased();
		if (!((surface instanceof SurfaceHolder) || (surface instanceof Surface) || (surface instanceof SurfaceTexture))) {
			throw new IllegalArgumentException("surface should be one of SurfaceHolder, Surface or SurfaceTexture");
		}
		sendMessage(obtainMessage(MSG_PREVIEW_START, surface));
	}

	public void stopPreview() {
		if (DEBUG) Log.v(TAG, "stopPreview:");
		removeMessages(MSG_PREVIEW_START);
		stopRecording();
		if (isPreviewing()) {
			final CameraThread thread = mWeakThread.get();
			if (thread == null) return;
			synchronized (thread.mSync) {
				sendEmptyMessage(MSG_PREVIEW_STOP);
				if (!isCameraThread()) {
					// wait for actually preview stopped to avoid releasing Surface/SurfaceTexture
					// while preview is still running.
					// therefore this method will take a time to execute
					try {
						thread.mSync.wait();
					} catch (final InterruptedException e) {
					}
				}
			}
		}
		if (DEBUG) Log.v(TAG, "stopPreview:finished");
	}

	protected void captureStill() {
		checkReleased();
		sendEmptyMessage(MSG_CAPTURE_STILL);
	}

	protected void captureStill(final String path) {
		checkReleased();
		sendMessage(obtainMessage(MSG_CAPTURE_STILL, path));
	}

	public void startRecording() {
		checkReleased();
		sendEmptyMessage(MSG_CAPTURE_START);
	}

	public void stopRecording() {
		sendEmptyMessage(MSG_CAPTURE_STOP);
	}

	public void release() {
		mReleased = true;
		close();
		sendEmptyMessage(MSG_RELEASE);
	}

	public void setFrameCallback(IFrameCallback frameCallback, int pixelFormat) {
		if (!isReleased()) {
			sendMessage(obtainMessage(MSG_SET_FRAME_CALLBACK, pixelFormat, -1, frameCallback));
		} else {
			Log.e(TAG, "Thread already released");
		}
	}

	public void addCallback(final CameraCallback callback) {
		checkReleased();
		if (!mReleased && (callback != null)) {
			final CameraThread thread = mWeakThread.get();
			if (thread != null) {
				thread.mCallbacks.add(callback);
			}
		}
	}

	public void removeCallback(final CameraCallback callback) {
		if (callback != null) {
			final CameraThread thread = mWeakThread.get();
			if (thread != null) {
				thread.mCallbacks.remove(callback);
			}
		}
	}

	protected void updateMedia(final String path) {
		sendMessage(obtainMessage(MSG_MEDIA_UPDATE, path));
	}

	public boolean checkSupportFlag(final long flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.mUVCCamera != null && thread.mUVCCamera.checkSupportFlag(flag);
	}

	public int getValue(final int flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera = thread != null ? thread.mUVCCamera : null;
		if (camera != null) {
			if (flag == UVCCamera.PU_BRIGHTNESS) {
				return camera.getBrightness();
			} else if (flag == UVCCamera.PU_CONTRAST) {
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	public int setValue(final int flag, final int value) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera = thread != null ? thread.mUVCCamera : null;
		if (camera != null) {
			if (flag == UVCCamera.PU_BRIGHTNESS) {
				camera.setBrightness(value);
				return camera.getBrightness();
			} else if (flag == UVCCamera.PU_CONTRAST) {
				camera.setContrast(value);
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	public int resetValue(final int flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera = thread != null ? thread.mUVCCamera : null;
		if (camera != null) {
			if (flag == UVCCamera.PU_BRIGHTNESS) {
				camera.resetBrightness();
				return camera.getBrightness();
			} else if (flag == UVCCamera.PU_CONTRAST) {
				camera.resetContrast();
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	@Override
	public void handleMessage(final Message msg) {
		final CameraThread thread = mWeakThread.get();
		if (thread == null) return;
		switch (msg.what) {
		case MSG_OPEN:
			thread.handleOpen((USBMonitor.UsbControlBlock)msg.obj);
			break;
		case MSG_CLOSE:
			thread.handleClose();
			break;
		case MSG_PREVIEW_START:
			thread.handleStartPreview(msg.obj);
			break;
		case MSG_PREVIEW_STOP:
			thread.handleStopPreview();
			break;
		case MSG_CAPTURE_STILL:
			thread.handleCaptureStill((String)msg.obj);
			break;
		case MSG_CAPTURE_START:
			thread.handleStartRecording();
			break;
		case MSG_CAPTURE_STOP:
			thread.handleStopRecording();
			break;
		case MSG_MEDIA_UPDATE:
			thread.handleUpdateMedia((String)msg.obj);
			break;
		case MSG_RELEASE:
			thread.handleRelease();
			break;
		case MSG_SET_FRAME_CALLBACK:
			thread.handleSetFrameCallback((IFrameCallback)msg.obj, msg.arg1);
			break;

		default:
			throw new RuntimeException("unsupported message:what=" + msg.what);
		}
	}

	static final class CameraThread extends Thread {
		private static final String TAG_THREAD = "CameraThread";
		private final Object mSync = new Object();
		private final Class<? extends AbstractUVCCameraHandler> mHandlerClass;
		private final WeakReference<Activity> mWeakParent;
		private final WeakReference<CameraViewInterface> mWeakCameraView;
		private final int mEncoderType;
		private final Set<CameraCallback> mCallbacks = new CopyOnWriteArraySet<CameraCallback>();
		private int mWidth, mHeight, mPreviewMode;
		private float mBandwidthFactor;
		private boolean mIsPreviewing;
		private boolean mIsRecording;
        private boolean mIsThermalEnable, mIsCmosEnable;
		/**
		 * shutter sound
		 */
		private SoundPool mSoundPool;
		private int mSoundId;
		private AbstractUVCCameraHandler mHandler;
		/**
		 * for accessing UVC camera
		 */
		private UVCCamera mUVCCamera;
		/**
		 * muxer for audio/video recording
		 */
		private MediaMuxerWrapper mMuxer;
		private MediaVideoBufferEncoder mVideoEncoder;

		//
		//	thermal_data info
		//
		private int mThermalWidth;
		private int mThermalHeight;
		private int[] mThermal_data;

		/**
		 *
		 * @param clazz Class extends AbstractUVCCameraHandler
		 * @param parent parent Activity
		 * @param cameraView for still capturing
		 * @param encoderType 0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
		 * @param width
		 * @param height
		 * @param format either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
		 * @param bandwidthFactor
		 */
		CameraThread(final Class<? extends AbstractUVCCameraHandler> clazz,
			final Activity parent, final CameraViewInterface cameraView,
			final int encoderType, final int width, final int height, final int format,
			final float bandwidthFactor) {

			super("CameraThread");
			mHandlerClass = clazz;
			mEncoderType = encoderType;
			mWidth = width;
			mHeight = height;
			mPreviewMode = format;
			mBandwidthFactor = bandwidthFactor;
			mWeakParent = new WeakReference<Activity>(parent);
			mWeakCameraView = new WeakReference<CameraViewInterface>(cameraView);
			loadShutterSound(parent);
			//
			mThermalWidth = THERMAL_WIDTH;
			mThermalHeight = THERMAL_HEIGHT;
			mThermal_data = new int[mThermalWidth*mThermalHeight];
		}

		@Override
		protected void finalize() throws Throwable {
			Log.i(TAG, "CameraThread#finalize");
			super.finalize();
		}

		public void setmIsThermalEnable(boolean enable){
            this.mIsThermalEnable = enable;
            if (mUVCCamera != null){
                mUVCCamera.enableThermalLayer(enable);
            }
        }

		public void setmIsCmosEnable(boolean enable){
			this.mIsCmosEnable = enable;
			if (mUVCCamera != null){
				mUVCCamera.enableCmosLayer(enable);
			}
		}

		public void SetMeridianParams(int numPalette, int xShift, int yShift){
			if (mUVCCamera != null){
				mUVCCamera.SetMeridianParams(numPalette, xShift, yShift);
			}
		}

        public void setParaletteHDR(boolean input){
            if (mUVCCamera != null){
                mUVCCamera.setParaletteHDR(input);
            }
        }

		public AbstractUVCCameraHandler getHandler() {
			if (DEBUG) Log.v(TAG_THREAD, "getHandler:");
			synchronized (mSync) {
				if (mHandler == null)
				try {
					mSync.wait();
				} catch (final InterruptedException e) {
				}
			}
			return mHandler;
		}

		public int getWidth() {
			synchronized (mSync) {
				return mWidth;
			}
		}

		public int getHeight() {
			synchronized (mSync) {
				return mHeight;
			}
		}

		public boolean isCameraOpened() {
			synchronized (mSync) {
				return mUVCCamera != null;
			}
		}

		public boolean isPreviewing() {
			synchronized (mSync) {
				return mUVCCamera != null && mIsPreviewing;
			}
		}

		public boolean isRecording() {
			synchronized (mSync) {
				return (mUVCCamera != null) && (mMuxer != null);
			}
		}

		public boolean isEqual(final UsbDevice device) {
			return (mUVCCamera != null) && (mUVCCamera.getDevice() != null) && mUVCCamera.getDevice().equals(device);
		}

		public void handleOpen(final USBMonitor.UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG_THREAD, "handleOpen:");
			handleClose();
			try {
				final UVCCamera camera = new UVCCamera();
				camera.open(ctrlBlock);
				synchronized (mSync) {
					mUVCCamera = camera;
				}
				callOnOpen();
			} catch (final Exception e) {
				callOnError(e);
			}
			if (DEBUG) Log.i(TAG, "supportedSize:" + (mUVCCamera != null ? mUVCCamera.getSupportedSize() : null));
		}

		public void handleClose() {
			if (DEBUG) Log.v(TAG_THREAD, "handleClose:");
			handleStopRecording();
			final UVCCamera camera;
			synchronized (mSync) {
				camera = mUVCCamera;
				mUVCCamera = null;
			}
			if (camera != null) {
				camera.stopPreview();
				camera.destroy();
				callOnClose();
			}
		}

        private Bitmap putTxt2Bitmap(Bitmap source) {
			Bitmap bmCrosshair,bmCrosshair_cpy, result;
			Matrix m = new Matrix();

			result = Bitmap.createBitmap(source);
            //result = Bitmap.createBitmap(source.getWidth(),source.getHeight(),source.getConfig());
			bmCrosshair = ((BitmapDrawable) mCrossHair.getDrawable()).getBitmap();
			bmCrosshair_cpy = bmCrosshair.copy(bmCrosshair.getConfig(), true);

			Canvas canvas = new Canvas(result);
            Paint paintTmp = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintTmp.setTextSize(30);
            paintTmp.setStyle(Paint.Style.FILL);
            paintTmp.setStrokeWidth(1.0f);
            Rect rectText = new Rect();
            paintTmp.getTextBounds(display_temp, 0, display_temp.length(), rectText);

			if(misFlip) {
				m.setScale(-1.0f, 1.0f);
				m.postTranslate(canvas.getWidth(),0);
				canvas.drawBitmap(result,m,null);
			}
			canvas.drawBitmap(bmCrosshair_cpy, mCrossHair.getX(), (int)(mCrossHair.getY() + mCrossHair.getHeight()/2 - (mainLayoutHeight - result.getHeight())/2), null);
			//	Draw text
			canvas.drawText(display_temp,canvas.getWidth() - rectText.width(), rectText.height(), paintTmp);
            return result;
        }


		public void handleStartPreview(final Object surface) {
			if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:");
			if ((mUVCCamera == null) || mIsPreviewing) return;
			try {
				mUVCCamera.setPreviewSize(mWidth, mHeight, mPreviewMode, mBandwidthFactor);
                mUVCCamera.enableThermalLayer(mIsThermalEnable);
			} catch (final IllegalArgumentException e) {
				try {
					// fallback to YUV mode
					mUVCCamera.setPreviewSize(mWidth, mHeight, UVCCamera.DEFAULT_PREVIEW_MODE, mBandwidthFactor);
				} catch (final IllegalArgumentException e1) {
					callOnError(e1);
					return;
				}
			}
			if (surface instanceof SurfaceHolder) {
				mUVCCamera.setPreviewDisplay((SurfaceHolder)surface);
			} else if (surface instanceof Surface) {
				mUVCCamera.setPreviewDisplay((Surface)surface);
			} else {
				mUVCCamera.setPreviewTexture((SurfaceTexture)surface);
			}
			mUVCCamera.startPreview();
			mUVCCamera.setTemperatureCallback(mTemperatureCallback);
			mUVCCamera.updateCameraParams();
			synchronized (mSync) {
				mIsPreviewing = true;
			}
			callOnStartPreview();
		}

		public void handleStopPreview() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:");
			if (mIsPreviewing) {
				if (mUVCCamera != null) {
					mUVCCamera.stopPreview();
				}
				synchronized (mSync) {
					mIsPreviewing = false;
					mSync.notifyAll();
				}
				callOnStopPreview();
			}
			if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:finished");
		}

		public void handleCaptureStill(final String path) {
			if (DEBUG) Log.v(TAG_THREAD, "handleCaptureStill:");
			final Activity parent = mWeakParent.get();
			if (parent == null) return;
			mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);	// play shutter sound
			try {
				final Bitmap bitmapWithTmp = putTxt2Bitmap(mWeakCameraView.get().captureStillImage());

				// get buffered output stream for saving a captured still image as a file on external storage.
				// the file name is came from current time.
				// You should use extension name as same as CompressFormat when calling Bitmap#compress.
				final File outputFile = TextUtils.isEmpty(path)
					? MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png")
					: new File(path);
				final BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
				try {
					try {
						//bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                        bitmapWithTmp.compress(Bitmap.CompressFormat.PNG, 100, os);
						os.flush();
						mHandler.sendMessage(mHandler.obtainMessage(MSG_MEDIA_UPDATE, outputFile.getPath()));
					} catch (final IOException e) {
					}
				} finally {
					os.close();
				}
			} catch (final Exception e) {
				callOnError(e);
			}
		}


		public void handleStartRecording() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStartRecording:");
			try {
				if ((mUVCCamera == null) || (mMuxer != null)) return;
				final MediaMuxerWrapper muxer = new MediaMuxerWrapper(".mp4");	// if you record audio only, ".m4a" is also OK.
				MediaVideoBufferEncoder videoEncoder = null;
				switch (mEncoderType) {
				case 1:	// for video capturing using MediaVideoEncoder
					new MediaVideoEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
					break;
				case 2:	// for video capturing using MediaVideoBufferEncoder
					videoEncoder = new MediaVideoBufferEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
					break;
				// case 0:	// for video capturing using MediaSurfaceEncoder
				default:
					new MediaSurfaceEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
					break;
				}
				if (true) {
					// for audio capturing
					new MediaAudioEncoder(muxer, mMediaEncoderListener);
				}
				muxer.prepare();
				muxer.startRecording();
				if (videoEncoder != null) {
					mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21);
				}
				synchronized (mSync) {
					mMuxer = muxer;
					mVideoEncoder = videoEncoder;
				}
				callOnStartRecording();
			} catch (final IOException e) {
				callOnError(e);
				Log.e(TAG, "startCapture:", e);
			}
		}

		public void handleStopRecording() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStopRecording:mMuxer=" + mMuxer);
			final MediaMuxerWrapper muxer;
			synchronized (mSync) {
				muxer = mMuxer;
				mMuxer = null;
				mVideoEncoder = null;
				if (mUVCCamera != null) {
					mUVCCamera.stopCapture();
				}
			}
			try {
				mWeakCameraView.get().setVideoEncoder(null);
			} catch (final Exception e) {
				// ignore
			}
			if (muxer != null) {
				muxer.stopRecording();
				mUVCCamera.setFrameCallback(null, 0);
				// you should not wait here
				callOnStopRecording();
			}
		}

		private final IFrameCallback mIFrameCallback = new IFrameCallback() {
			@Override
			public void onFrame(final ByteBuffer frame, int[] thermal_data) {
				final MediaVideoBufferEncoder videoEncoder;
				synchronized (mSync) {
					videoEncoder = mVideoEncoder;
				}
				if (videoEncoder != null) {
					videoEncoder.frameAvailableSoon();
					videoEncoder.encode(frame);
				}
			}
		};

		private final TemperatureCallback mTemperatureCallback = new TemperatureCallback() {
			private Intent intent = new Intent(TEMPERATURE_STRING);
			@Override
			public void updateTemperature(int temperature, int[] thermal_data) {
				intent.putExtra("Temperature", temperature);
				//avgTemperature = temperature;
				// copy thermal_data to internal structure
				if (thermal_data != null) {
					System.arraycopy(thermal_data, 0, mThermal_data, 0, thermal_data.length);
				} else {
					Log.w(TAG, "Null thermal_data");
				}
				mWeakParent.get().sendBroadcast(intent);
			}
		};

		public float getTemperature(int posX, int posY) {
			// translate from CIS position into thermal position
			posX = (int) (posX / ((float)mWidth / (float)mThermalWidth));
			posY = (int) (posY / ((float)mHeight / (float)mThermalHeight));

			if (posX < 0) posX = 0;
			if (posX >= mThermalWidth) posX = mThermalWidth - 1;
			if (posY < 0) posY = 0;
			if (posY >= mThermalHeight) posY = mThermalHeight - 1;

			return (float)mThermal_data[posY*mThermalWidth + posX] / 10;
		}

		public void handleUpdateMedia(final String path) {
			if (DEBUG) Log.v(TAG_THREAD, "handleUpdateMedia:path=" + path);
			final Activity parent = mWeakParent.get();
			final boolean released = (mHandler == null) || mHandler.mReleased;
			if (parent != null && parent.getApplicationContext() != null) {
				try {
					if (DEBUG) Log.i(TAG, "MediaScannerConnection#scanFile");
					MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{ path }, null, null);
				} catch (final Exception e) {
					Log.e(TAG, "handleUpdateMedia:", e);
				}
				if (released || parent.isDestroyed())
					handleRelease();
			} else {
				Log.w(TAG, "MainActivity already destroyed");
				// give up to add this movie to MediaStore now.
				// Seeing this movie on Gallery app etc. will take a lot of time.
				handleRelease();
			}
		}

		public void handleRelease() {
			if (DEBUG) Log.v(TAG_THREAD, "handleRelease:mIsRecording=" + mIsRecording);
			handleClose();
			mCallbacks.clear();
			if (!mIsRecording) {
				mHandler.mReleased = true;
				Looper.myLooper().quit();
			}
			if (DEBUG) Log.v(TAG_THREAD, "handleRelease:finished");
		}

		/*
			pixelFormat, see UVCCamera.PIXEL_FORMAT_XYZ
				ex, UVCCamera.PIXEL_FORMAT_RGB565
		 */
		public void handleSetFrameCallback(final IFrameCallback frameCallback, int pixelFormat) {
			if (mUVCCamera != null) {
				if (pixelFormat < 0 || pixelFormat > PIXEL_FORMAT_NV21) {
					pixelFormat = PIXEL_FORMAT_RGBX;	// default to RGBX if format not specified
					Log.w(TAG, "handleSetFrameCallback not pixelFormat specified, default to "+pixelFormat);
				}

				mUVCCamera.setFrameCallback(frameCallback, pixelFormat);
			}
		}

		private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
			@Override
			public void onPrepared(final MediaEncoder encoder) {
				if (DEBUG) Log.v(TAG, "onPrepared:encoder=" + encoder);
				mIsRecording = true;
				if (encoder instanceof MediaVideoEncoder)
				try {
					mWeakCameraView.get().setVideoEncoder((MediaVideoEncoder)encoder);
				} catch (final Exception e) {
					Log.e(TAG, "onPrepared:", e);
				}
				if (encoder instanceof MediaSurfaceEncoder)
				try {
					mWeakCameraView.get().setVideoEncoder((MediaSurfaceEncoder)encoder);
					mUVCCamera.startCapture(((MediaSurfaceEncoder)encoder).getInputSurface());
				} catch (final Exception e) {
					Log.e(TAG, "onPrepared:", e);
				}
			}

			@Override
			public void onStopped(final MediaEncoder encoder) {
				if (DEBUG) Log.v(TAG_THREAD, "onStopped:encoder=" + encoder);
				if ((encoder instanceof MediaVideoEncoder)
					|| (encoder instanceof MediaSurfaceEncoder))
				try {
					mIsRecording = false;
					final Activity parent = mWeakParent.get();
					mWeakCameraView.get().setVideoEncoder(null);
					synchronized (mSync) {
						if (mUVCCamera != null) {
							mUVCCamera.stopCapture();
						}
					}
					final String path = encoder.getOutputPath();
					if (!TextUtils.isEmpty(path)) {
						mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_MEDIA_UPDATE, path), 1000);
					} else {
						final boolean released = (mHandler == null) || mHandler.mReleased;
						if (released || parent == null || parent.isDestroyed()) {
							handleRelease();
						}
					}
				} catch (final Exception e) {
					Log.e(TAG, "onPrepared:", e);
				}
			}
		};

		/**
		 * prepare and load shutter sound for still image capturing
		 */
		@SuppressWarnings("deprecation")
		private void loadShutterSound(final Context context) {
	    	// get system stream type using reflection
	        int streamType;
	        try {
	            final Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
	            final Field sseField = audioSystemClass.getDeclaredField("STREAM_SYSTEM_ENFORCED");
	            streamType = sseField.getInt(null);
	        } catch (final Exception e) {
	        	streamType = AudioManager.STREAM_SYSTEM;	// set appropriate according to your app policy
	        }
	        if (mSoundPool != null) {
	        	try {
	        		mSoundPool.release();
	        	} catch (final Exception e) {
	        	}
	        	mSoundPool = null;
	        }
	        // load shutter sound from resource
		    mSoundPool = new SoundPool(2, streamType, 0);
		    mSoundId = mSoundPool.load(context, R.raw.camera_click, 1);
		}

		@Override
		public void run() {
			Looper.prepare();
			AbstractUVCCameraHandler handler = null;
			try {
				final Constructor<? extends AbstractUVCCameraHandler> constructor = mHandlerClass.getDeclaredConstructor(CameraThread.class);
				handler = constructor.newInstance(this);
			} catch (final NoSuchMethodException e) {
				Log.w(TAG, e);
			} catch (final IllegalAccessException e) {
				Log.w(TAG, e);
			} catch (final InstantiationException e) {
				Log.w(TAG, e);
			} catch (final InvocationTargetException e) {
				Log.w(TAG, e);
			}
			if (handler != null) {
				synchronized (mSync) {
					mHandler = handler;
					mSync.notifyAll();
				}
				Looper.loop();
				if (mSoundPool != null) {
					mSoundPool.release();
					mSoundPool = null;
				}
				if (mHandler != null) {
					mHandler.mReleased = true;
				}
			}
			mCallbacks.clear();
			synchronized (mSync) {
				mHandler = null;
				mSync.notifyAll();
			}
		}

		private void callOnOpen() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onOpen();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnClose() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onClose();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStartPreview() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStartPreview();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStopPreview() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStopPreview();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStartRecording() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStartRecording();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStopRecording() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStopRecording();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnError(final Exception e) {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onError(e);
				} catch (final Exception e1) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}
	}
}
