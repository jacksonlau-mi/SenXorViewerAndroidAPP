# SenXorViewer

## Setup

**IMPORTANT: This project requires Android NDK, Revision 14b (March 2017)** 

1. (Download Android NDK r14b)[https://developer.android.com/ndk/downloads/older_releases.html] 

1. Extract the downloaded archive file to some location on your filesystem.

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

## Build

Android Studio will build thermalViewer-debug.apk due to the current directory name structure.
However, the apk application name is SenXorViewer.
In order to match the apk name and the app name, rename "thermalViewer-debug.apk" to "SenXorViewer.apk" after the Android Studio build process.
