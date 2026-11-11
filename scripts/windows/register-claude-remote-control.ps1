<#
.SYNOPSIS
  ログオン時に claude remote-control を起動するタスクを、タスク スケジューラに登録する。

.DESCRIPTION
  start-claude-remote-control.ps1 を、サインインしたときに最小化したウィンドウで起動する。
  管理者権限は不要（自分のユーザーで、サインイン中だけ動くタスクとして登録する）。
  同じ名前のタスクがあれば上書きする。-Unregister で削除する。

.EXAMPLE
  pwsh -File .\register-claude-remote-control.ps1
  pwsh -File .\register-claude-remote-control.ps1 -WorkDir C:\dev\isucon2026 -TaskName 'Claude Remote Control (isucon2026)'
  pwsh -File .\register-claude-remote-control.ps1 -Unregister
#>
param(
  [string]$WorkDir = 'C:\dev\inv',
  [string]$TaskName = 'Claude Remote Control (inv)',
  [switch]$Unregister
)

$ErrorActionPreference = 'Stop'

if ($Unregister) {
  Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
  Write-Host "削除しました: $TaskName"
  return
}

$startScript = Join-Path $PSScriptRoot 'start-claude-remote-control.ps1'
$pwsh = (Get-Command pwsh).Source
$user = "$env:USERDOMAIN\$env:USERNAME"

# Windows Terminal ではなく conhost で開く。Windows Terminal だと -WindowStyle Minimized が効かないため
$arguments = @(
  "`"$pwsh`"", '-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Minimized',
  '-File', "`"$startScript`"", '-WorkDir', "`"$WorkDir`""
) -join ' '

$action = New-ScheduledTaskAction -Execute "$env:WINDIR\System32\conhost.exe" -Argument $arguments -WorkingDirectory $WorkDir
# ネットワークの準備を待つため、サインインの 30 秒後に起動する
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
$trigger.Delay = 'PT30S'
$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
# 既定の「3 日で停止」を外し、バッテリー駆動でも止めない。二重起動はしない
$settings = New-ScheduledTaskSettingsSet `
  -ExecutionTimeLimit ([TimeSpan]::Zero) `
  -AllowStartIfOnBatteries `
  -DontStopIfGoingOnBatteries `
  -StartWhenAvailable `
  -MultipleInstances IgnoreNew `
  -RestartCount 3 `
  -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings `
  -Description "ログオン時に $WorkDir で claude remote-control を起動し、スマホから新しいセッションを始められるようにする" `
  -Force | Out-Null

Write-Host "登録しました: $TaskName（$WorkDir）"
Write-Host "今すぐ起動するには: Start-ScheduledTask -TaskName '$TaskName'"
