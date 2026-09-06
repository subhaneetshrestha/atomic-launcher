#!/usr/bin/env fish
# atomic-launcher — Android toolchain setup for Arch/CachyOS with paru.
#
# Run once from the repo root:   fish scripts/setup-toolchain.fish
# Safe to re-run: every step is idempotent (--needed, existence checks).
# Requires: paru, sudo (you will be asked for your password).

set -g SDK /opt/android-sdk
set -g JDK /usr/lib/jvm/java-17-openjdk
set -g SDKM $SDK/cmdline-tools/latest/bin/sdkmanager
set -g AVDM $SDK/cmdline-tools/latest/bin/avdmanager

function step
    set_color -o cyan; echo; echo "==> $argv"; set_color normal
end
function die
    set_color -o red; echo "!! $argv"; set_color normal; exit 1
end
# Run a command with the android-sdk group active (group membership only
# takes effect in new logins; `sg` gives it to us now). Env is passed explicitly.
function as_sdk_group
    sg android-sdk -c "env JAVA_HOME=$JDK ANDROID_HOME=$SDK ANDROID_SDK_ROOT=$SDK $argv"
end

command -q paru; or die "paru is not installed"

step "1/7 Official repo packages: JDK 17, adb udev rules, Gradle (used once to generate the wrapper)"
paru -S --needed --noconfirm jdk17-openjdk android-udev gradle; or die "package install failed"
test -x $JDK/bin/java; or die "JDK 17 not found at $JDK"

step "2/7 AUR packages: Android SDK command-line tools, platform-tools, build-tools, platforms 36 + 37, emulator"
# android-platform = the current platform (API 37 at the time of writing); android-platform-36 = API 36.
paru -S --needed android-sdk-cmdline-tools-latest android-sdk-platform-tools android-sdk-build-tools android-platform android-platform-36 android-emulator; or die "AUR install failed"
test -x $SDKM; or die "sdkmanager not found at $SDKM"

step "3/7 Make $SDK writable for your user (android-sdk group)"
getent group android-sdk >/dev/null; or sudo groupadd android-sdk
if not id -nG | string match -q android-sdk
    sudo gpasswd -a $USER android-sdk; or die "could not add $USER to android-sdk"
    echo "Added $USER to the android-sdk group. It applies to new logins; this script uses 'sg' meanwhile."
end
sudo chgrp -R android-sdk $SDK
sudo chmod -R g+w $SDK
sudo find $SDK -type d -exec chmod g+s '{}' +
# adb access to physical devices
getent group adbusers >/dev/null; and not id -nG | string match -q adbusers; and sudo gpasswd -a $USER adbusers

step "4/7 sdkmanager: licenses, build-tools 36.0.0 (AGP 9.4 default), system images for API 26 and 36"
as_sdk_group "sh -c 'yes | $SDKM --licenses >/dev/null'"; or die "license acceptance failed"
as_sdk_group "$SDKM --install 'build-tools;36.0.0' 'system-images;android-26;google_apis;x86_64' 'system-images;android-36;google_apis;x86_64'"; or die "sdkmanager install failed"

step "5/7 AVDs: api26 and api36 (Pixel profile, stored in ~/.android/avd)"
set -lx JAVA_HOME $JDK
set -lx ANDROID_HOME $SDK
set -lx ANDROID_SDK_ROOT $SDK
for spec in "api26|system-images;android-26;google_apis;x86_64" "api36|system-images;android-36;google_apis;x86_64"
    set -l parts (string split '|' $spec)
    if test -d ~/.android/avd/$parts[1].avd
        echo "AVD $parts[1] already exists"
    else
        echo no | $AVDM create avd -n $parts[1] -k "$parts[2]" -d pixel; or die "creating AVD $parts[1] failed"
    end
end

step "6/7 Fish environment (universal variables, persist across shells)"
set -Ux JAVA_HOME $JDK
set -Ux ANDROID_HOME $SDK
set -Ux ANDROID_SDK_ROOT $SDK
for p in $SDK/emulator $SDK/cmdline-tools/latest/bin $SDK/platform-tools
    if not contains -- $p $fish_user_paths
        set -U fish_user_paths $p $fish_user_paths
    end
end

step "7/7 GitHub CLI (for creating the repo and pushing; authenticate afterwards with: gh auth login)"
paru -S --needed --noconfirm github-cli; or echo "github-cli install failed; install it later for pushing"

step "Verification"
echo "java:      "($JDK/bin/java -version 2>&1 | head -1)
echo "adb:       "($SDK/platform-tools/adb version | head -1)
echo "emulator:  "($SDK/emulator/emulator -version 2>/dev/null | head -1)
echo "avds:      "($SDK/emulator/emulator -list-avds | string join ', ')
echo "kvm:       "(test -r /dev/kvm -a -w /dev/kvm; and echo ok; or echo "NOT accessible")
echo "installed SDK packages:"
as_sdk_group "$SDKM --list_installed" | sed -n '/^  /p'
echo
set_color -o green; echo "Done. Open a new terminal (or run 'exec fish') so JAVA_HOME/ANDROID_HOME/PATH are active."; set_color normal
