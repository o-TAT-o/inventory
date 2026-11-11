<#
.SYNOPSIS
  claude remote-control を常駐させる。終了したときや、サーバーとの接続が切れたまま残ったときは起動し直す。

.DESCRIPTION
  スマホの Claude アプリや claude.ai/code から、指定したフォルダで新しいセッションを始められるようにする。
  ログオン時にタスク スケジューラから起動する前提（登録は register-claude-remote-control.ps1）。

  次の 2 つの場合に、少し待ってから起動し直す。
  - claude が終了した（ネットワークがまだつながっていない、更新で claude.exe が入れ替わった、など）
  - プロセスは残っているのに、サーバーとの接続（確立済みの TCP 接続）が StaleMinutes 分続けてない
    （正常に待ち受けている間は、常に 1 本以上つながっている）

  起動するときに、親プロセスがなくなって取り残された claude remote-control があれば止める。

  止めたいときは、このウィンドウを閉じるか、-Stop を付けて実行する。
  Stop-ScheduledTask はウィンドウ（conhost）しか止めず、中の claude が取り残されるので使わない。
  ログは %LOCALAPPDATA%\ClaudeRemoteControl\launcher.log に出す。

.PARAMETER WorkDir
  待ち受けるフォルダ。事前に一度 claude を起動して「信頼する」を選んでおくこと。

.PARAMETER RestartDelaySec
  終了してから起動し直すまでの秒数。

.PARAMETER StaleMinutes
  サーバーとの接続がこの分数続けてなければ、切れたとみなして起動し直す。

.PARAMETER CheckIntervalSec
  接続を確かめる間隔（秒）。

.PARAMETER Spawn
  スマホなどから始めたセッションの開き方。same-dir は WorkDir でそのまま開き、worktree はセッションごとに git worktree を作る。
  指定しておくと、初めてのフォルダでも起動時に選ぶ確認が出ない。

.PARAMETER ClaudePath
  claude.exe の場所。

.PARAMETER Stop
  このスクリプトで動いている待ち受けを、フォルダに関係なくすべて止めて終わる。
  ログオン時のタスクは残るので、次のサインインでまた起動する。

.EXAMPLE
  pwsh -File .\start-claude-remote-control.ps1 -Stop
#>
param(
  [string]$WorkDir = 'C:\dev\inv',
  [int]$RestartDelaySec = 30,
  [int]$StaleMinutes = 5,
  [int]$CheckIntervalSec = 30,
  [ValidateSet('same-dir', 'worktree')]
  [string]$Spawn = 'same-dir',
  [string]$ClaudePath = (Join-Path $env:USERPROFILE '.local\bin\claude.exe'),
  [switch]$Stop
)

$ErrorActionPreference = 'Stop'

$logDir = Join-Path $env:LOCALAPPDATA 'ClaudeRemoteControl'
$logFile = Join-Path $logDir 'launcher.log'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# claude の画面を崩さないよう、ログはファイルにだけ書く
function Write-Log([string]$Message) {
  $line = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
  Add-Content -Path $logFile -Value $line -Encoding utf8
}

# プロセス（と子プロセス）に、確立済みの TCP 接続が 1 本でもあるか
function Test-Connected([int]$ProcessId) {
  $ids = @($ProcessId) + @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" | ForEach-Object ProcessId)
  $conn = Get-NetTCPConnection -State Established -ErrorAction SilentlyContinue |
    Where-Object { $_.OwningProcess -in $ids } |
    Select-Object -First 1
  return [bool]$conn
}

# このスクリプトで動いているほかの待ち受けを、claude ごと止める。止めた数を返す
function Stop-Launcher {
  $targets = @(Get-CimInstance Win32_Process -Filter "Name='pwsh.exe'" |
    Where-Object { $_.ProcessId -ne $PID -and $_.CommandLine -match 'start-claude-remote-control\.ps1' })
  foreach ($t in $targets) {
    & taskkill.exe /PID $t.ProcessId /T /F | Out-Null
    Write-Log "停止: 待ち受けのスクリプト（PID $($t.ProcessId)）"
  }
  return $targets.Count
}

# 親プロセスがなくなって取り残された claude remote-control を止める。止めた数を返す
function Stop-OrphanedRemoteControl {
  $alive = @{}
  Get-Process | ForEach-Object { $alive[$_.Id] = $true }
  $targets = @(Get-CimInstance Win32_Process -Filter "Name='claude.exe'" |
    Where-Object { $_.CommandLine -match '\sremote-control(\s|$)' -and -not $alive.ContainsKey([int]$_.ParentProcessId) })
  foreach ($t in $targets) {
    & taskkill.exe /PID $t.ProcessId /T /F | Out-Null
    Write-Log "停止: 取り残された claude remote-control（PID $($t.ProcessId)）"
  }
  return $targets.Count
}

if ($Stop) {
  $count = (Stop-Launcher) + (Stop-OrphanedRemoteControl)
  Write-Host "止めた待ち受け: $count 件"
  return
}

$null = Stop-OrphanedRemoteControl

$Host.UI.RawUI.WindowTitle = "Claude Remote Control - $WorkDir"
Set-Location -LiteralPath $WorkDir

while ($true) {
  Write-Log "起動: $WorkDir"
  # 同じウィンドウで動かす。起動中は claude の画面（接続先の URL や QR コード）がここに出る
  $proc = Start-Process -FilePath $ClaudePath -ArgumentList 'remote-control', "--spawn=$Spawn" -NoNewWindow -PassThru
  $null = $proc.Handle  # 終了コードを後から読めるようにハンドルを保持する

  $lastConnected = Get-Date
  while (-not $proc.WaitForExit($CheckIntervalSec * 1000)) {
    if (Test-Connected $proc.Id) {
      $lastConnected = Get-Date
      continue
    }
    if (((Get-Date) - $lastConnected).TotalMinutes -ge $StaleMinutes) {
      Write-Log "サーバーとの接続が ${StaleMinutes} 分ないため、止めて起動し直します（PID $($proc.Id)）"
      & taskkill.exe /PID $proc.Id /T /F | Out-Null
      $proc.WaitForExit()
      break
    }
  }

  Write-Log "終了: exit code $($proc.ExitCode)。${RestartDelaySec} 秒後に起動し直します"
  Start-Sleep -Seconds $RestartDelaySec
}
