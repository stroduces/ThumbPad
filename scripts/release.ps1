param([ValidateSet('android','windows','macos')][string]$Target='windows')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $root
function Run([string]$program,[string[]]$arguments){& $program @arguments;if($LASTEXITCODE -ne 0){throw "$program failed with $LASTEXITCODE"}}
New-Item -ItemType Directory -Force dist | Out-Null
$python=if(Get-Command python -ErrorAction SilentlyContinue){'python'}else{'python3'}
if($Target -eq 'android'){
  $launcher=if($IsWindows){'.\android\gradlew.bat'}else{'./android/gradlew'}
  Run $launcher @('-p','android','testDebugUnitTest','lintDebug','assembleDebug','assembleRelease')
  Copy-Item 'android/app/build/outputs/apk/debug/app-debug.apk' 'dist/ThumbPad-Android-debug.apk'
  Copy-Item 'android/app/build/outputs/apk/release/app-release-unsigned.apk' 'dist/ThumbPad-Android-release-unsigned.apk'
}else{
  Run 'cargo' @('test','--locked','--manifest-path','desktop/core/Cargo.toml')
  $npm=if($IsWindows){'npm.cmd'}else{'npm'}
  Run $npm @('ci','--prefix','desktop')
  $triple=if($Target -eq 'windows'){'x86_64-pc-windows-msvc'}elseif([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture -eq 'Arm64'){'aarch64-apple-darwin'}else{'x86_64-apple-darwin'}
  $bundle=if($Target -eq 'windows'){'nsis'}else{'dmg'}
  Run $npm @('--prefix','desktop','run','build','--','--target',$triple,'--bundles',$bundle)
  Run $python @('scripts/collect-artifacts.py',$triple)
}
Run $python @('scripts/inspect-artifacts.py','dist')
