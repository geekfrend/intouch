# InTouch

InTouch shows who you're due to call and logs your phone and WhatsApp calls, so the people
you care about don't drift out of reach. Android only, built for a Galaxy Z Flip 6 first.

This repo currently holds **InTouch Lab**, a throwaway test app that checks the risky parts
of the design on a real phone before the full app is built:

- reading the call history, and how long an unanswered call is recorded as
- starting a WhatsApp voice call directly
- what WhatsApp call notifications look like
- a widget on the home screen and the Flip's cover screen, and opening the app there

## Install the test app on your phone

1. On your phone, open this repo's **Releases** page and the **test-build** release.
2. Download `InTouch-Lab.apk` and open it. Android asks once to allow installs from your browser.
3. Open **InTouch Lab** and work through sections 1–7, then tap **Share results**.

Every push to `main` builds a new version and replaces the `test-build` release. New builds
install over the old one.

## Build on your own Linux machine (optional)

Needs Java 17 and Android's command-line SDK tools (no Android Studio):

```bash
sudo apt install openjdk-17-jdk unzip
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*_latest.zip && mv cmdline-tools latest
export ANDROID_HOME=~/android-sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Then, in this repo:

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
./gradlew assembleDebug
# Install over USB (turn on USB debugging in Developer options first):
~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If the command-line tools link above has moved, get the current "Command line tools only"
link from https://developer.android.com/studio#command-line-tools-only.
