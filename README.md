# SenXorViewer
The SenXorViewer APP is the release version of the android project from Meridian Innovation Limited for **XCAM** series [SenXorViewer](https://play.google.com/store/apps/details?id=com.meridianinno.senxorviewer&hl=zh-TW).
Details please check the SenXorViewer Android App Note.

## Setup

**IMPORTANT: This project requires Android NDK, Revision 14b (March 2017)** 

1. (Download Android NDK r14b)[https://developer.android.com/ndk/downloads/older_releases.html] 

1. Extract the downloaded archive file to somewhere on your filesystem.

1. Launch Android Studio. 

1. Modify `local.properties` to point to the location of Android NDK r14b.
```
# Linux (TODO)

# Windows. Assuming NDK r14b is at C:\Users\Lenovo\android-ndk-r14b
ndk.dir= C\:\\Users\\Lenovo\\android-ndk-r14b
```

1. Click `File -> New -> Import Project`, and open the SenXorViewer directory (i.e. this repository)

1. Refer to [this guide](https://futurestud.io/tutorials/how-to-debug-your-android-app-over-wifi-without-root) for how to debug over Wifi, since any SenXor devices will occupy the USB port.

1. By default, the x86 version of the opencv library is not in the build (since most mobile devices won't use it). To debug on a Mac, you may need to include the x86 opencv library by:
```
cp -r SenXorViewer/opencv_jniLibs/x86 ThermalViewer/thermalViewer/src/main/jniLibs
```
