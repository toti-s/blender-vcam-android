# Blender VCam — Android Controller

Use your phone as a handheld virtual camera in Blender. The app connects to the
**[VCam Bridge Blender add-on](https://github.com/toti-s/blender-vcam-bridge)** over Wi-Fi:

- **Live viewfinder** — the camera view streamed from Blender, with an optional thirds grid
- **Motion tracking** — gyro/accelerometer orientation, or walk around with ARCore 6-DoF tracking
- **Virtual joysticks** — move, strafe, lift and turn without moving the phone
- **Lens controls** — focal length slider (10–200 mm), depth of field on/off, focus distance, f-stop
- **Camera controls** — switch Blender cameras, recenter, level the horizon, motion smoothing
- **Recording** — start/stop keyframe recording on the Blender timeline, with live frame counter
- **Auto discovery** — finds Blender on the local network, or connect by IP manually

## Demo

Live capture of the app driving Blender's camera over Wi-Fi — phone viewfinder and
motion tracking on the phone, Blender on the desktop:

<video src="https://raw.githubusercontent.com/toti-s/blender-vcam-android/main/media/blender-vcam-demo.mp4" controls width="720"></video>

[Watch the demo video](media/blender-vcam-demo.mp4)

## Install

Download `BlenderVCam-1.0.apk` from the [latest release](https://github.com/toti-s/blender-vcam-android/releases/latest)
and sideload it on the phone (Android 8.0+, `minSdk 26`). Allow the camera permission when asked
(it is only used for ARCore tracking — the app works without it).

Or install from source with adb:

```powershell
adb install BlenderVCam-1.0.apk
```

## Build from source

Requirements: JDK 17, Android SDK (platform 35, build-tools 35.0.0), Gradle 8.9 (wrapper included).

```powershell
# debug build (auto-signed, straight to a connected device)
.\gradlew.bat assembleDebug

# release build (unsigned APK; sign it with your own keystore)
.\gradlew.bat assembleRelease
```

Set `sdk.dir` in `local.properties` (or `ANDROID_HOME`) to your Android SDK.

> **Release signing:** keep your release keystore and its passwords somewhere safe and **never
> commit them** (`.gitignore` already excludes `*.keystore` / `*.jks`). Android will only install
> an update if it is signed with the same key as the previous version.

## Use

1. In Blender install the [VCam Bridge add-on](https://github.com/toti-s/blender-vcam-bridge),
   open the N-panel **VCam** tab and click **Start Server**.
2. Phone and computer must be on the same Wi-Fi.
3. In the app tap **Search** and pick your machine, or type the IP:port shown in Blender.
4. Point the phone where you want the camera; use **Recenter** to snap Blender's camera to the
   phone's current pose.

## Protocol

The wire protocol is documented in the add-on README — plain TCP on port 9876 with
`[1 byte type][4 byte big-endian length][payload]` framing: JSON messages, 7× float32 poses, and
JPEG/PNG preview frames, plus UDP broadcast discovery.

## Project layout

```
app/src/main/java/com/blendervcam/controller/
  MainActivity.kt        # single-activity Compose UI host, permissions, AR install flow
  VCamViewModel.kt       # connection, live status, command sending, 60 Hz pose loop
  net/                   # TCP framing client + UDP discovery
  tracking/              # gyro orientation tracker, optional ARCore pose, quaternion math
  ui/                    # connect screen, camera HUD, joysticks, settings, theme
```

## License

[GPL-3.0-or-later](LICENSE)
