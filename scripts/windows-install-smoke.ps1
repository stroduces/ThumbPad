$ErrorActionPreference='Stop'
$workspaceRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$installDir=Join-Path $workspaceRoot 'local-smoke\installed'
if (-not $installDir.StartsWith($workspaceRoot+[IO.Path]::DirectorySeparatorChar)) { throw 'Install directory must stay within workspace' }
$setup=Join-Path $workspaceRoot 'dist\ThumbPad-Windows-Setup.exe'
$process=Start-Process -FilePath $setup -ArgumentList @('/S',"/D=$installDir") -Wait -PassThru -WindowStyle Hidden
if($process.ExitCode -ne 0){throw "Install failed: $($process.ExitCode)"}
if(-not(Test-Path -LiteralPath (Join-Path $installDir 'thumbpad.exe'))){throw 'Installed executable missing'}
$uninstall=Get-ChildItem -LiteralPath $installDir -Filter '*uninstall*.exe' | Select-Object -First 1
if(-not $uninstall){throw 'Clean uninstaller missing'}
$process=Start-Process -FilePath $uninstall.FullName -ArgumentList '/S' -Wait -PassThru -WindowStyle Hidden
if($process.ExitCode -ne 0){throw 'Uninstall failed'}
Start-Sleep -Seconds 3
if(Test-Path -LiteralPath (Join-Path $installDir 'thumbpad.exe')){throw 'Installed app remained after uninstall'}
Write-Output 'PASS: per-user silent installation, executable, clean uninstallation'
