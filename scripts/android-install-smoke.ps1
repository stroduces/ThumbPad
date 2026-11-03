param([string]$Adb='adb')
$ErrorActionPreference='Stop'
& $Adb install -r 'dist/ThumbPad-Android-debug.apk'
if($LASTEXITCODE -ne 0){throw 'APK installation failed'}
& $Adb shell am start -W -n app.thumbpad/.MainActivity
if($LASTEXITCODE -ne 0){throw 'Activity launch failed'}
Write-Output 'Installed and launched. Run android/gradlew -p android connectedDebugAndroidTest for interactive Compose checks.'
