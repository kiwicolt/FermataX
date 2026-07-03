## Building the project

### Requirements

- Install the latest Android SDK or Android Studio from https://developer.android.com/studio/
- Set `ANDROID_SDK_ROOT` to your Android SDK directory.

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
```

On Windows PowerShell:

```powershell
$env:ANDROID_SDK_ROOT="C:\Users\<user>\AppData\Local\Android\Sdk"
```

### Clone

```bash
git clone --recurse-submodules https://github.com/chuoinho/FermataX.git
cd FermataX
```

If you cloned the repository without submodules, run:

```bash
git submodule update --init --recursive
```

### Build AAB

```bash
./gradlew :fermata:bundleAutoRelease
```

Output:

```text
fermata/build/outputs/bundle/autoRelease/
```

### Build Universal APK

```bash
./gradlew :fermata:packageAutoReleaseUniversalApk
```

Output:

```text
fermata/build/outputs/apk_from_bundle/autoRelease/
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.
