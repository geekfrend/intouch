# InTouch

InTouch shows who you're due to call and logs your phone and WhatsApp calls automatically,
so the people you care about don't drift out of reach. Android only, built for a Galaxy Z Flip 6
first, including a widget and the full app on the cover screen.

The product spec lives in Claude Docs ("InTouch — Product Spec v1 (Android)"). Requirement numbers
in code comments (FR-1 to FR-22) refer to it.

## Install on your phone

1. On your phone, open this repo's **Releases** page and the **latest** release.
2. Download `InTouch.apk` and open it. Android asks once to allow installs from your browser.
3. Open **InTouch** and follow the setup screen.

Every push to `main` runs the unit tests, builds a new version and replaces the `latest` release.
New builds install over the old one and keep your data.

**Notification access is greyed out?** Android restricts it for apps installed from a browser.
Open App info for InTouch, tap ⋮ in the top corner, choose **Allow restricted settings**, then try again.

## How it's organised

| Folder (`app/src/main/java/com/geekfrend/intouch/`) | What's there |
| --- | --- |
| `domain/` | Pure rules with unit tests: due status, stats, phone-number matching, WhatsApp call detection |
| `data/` | Room database, settings, and the repository every part of the app goes through |
| `device/` | Reading contacts and call history, building call intents |
| `notify/` | The WhatsApp call-notification listener |
| `work/` | Background refresh every few hours and just after midnight |
| `widget/` | Home-screen and cover-screen widgets (RemoteViews) |
| `ui/` | Screens, dialogs, navigation, view model, text formatting, theme |

Design notes for the later visual design pass: [docs/DESIGN.md](docs/DESIGN.md).

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
./gradlew testDebugUnitTest assembleDebug
# Install over USB (turn on USB debugging in Developer options first):
~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If the command-line tools link has moved, get the current "Command line tools only" link from
https://developer.android.com/studio#command-line-tools-only.
