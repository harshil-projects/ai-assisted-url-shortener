<#
.SYNOPSIS
  Snapshot the raw AI diff for a task before the engineer edits anything (lifecycle step 5).

.DESCRIPTION
  Creates harness/evidence/T-NNN/, stages the whole working tree, writes the staged diff to
  harness/evidence/T-NNN/ai-raw.patch, unstages again, then prints the changed file list
  (git status --porcelain) and a ready-to-paste YAML files block for the ledger entry.

  Any partial staging you had before running this script is reset. Run it straight after the
  AI has finished drafting and before you touch a file.

  Requires PowerShell 5.1 or later and git on PATH. No other tools.

.PARAMETER TaskId
  Task id in the form T-NNN, for example T-004.

.EXAMPLE
  scripts/snap.ps1 T-004
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0)]
  [string] $TaskId
)

$ErrorActionPreference = 'Continue'

function Fail([string] $Message) {
  Write-Host "snap: $Message" -ForegroundColor Red
  Write-Host 'Usage: scripts/snap.ps1 T-NNN    (example: scripts/snap.ps1 T-004)'
  exit 1
}

if ([string]::IsNullOrWhiteSpace($TaskId)) { Fail 'the task id is missing.' }
if ($TaskId -notmatch '^T-\d{3}$') { Fail "task id '$TaskId' does not match ^T-\d{3}$ (three digits, for example T-004)." }

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

& git rev-parse --is-inside-work-tree 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "not inside a git repository: $repoRoot" }

$patchRel    = "harness/evidence/$TaskId/ai-raw.patch"
$evidenceDir = Join-Path $repoRoot "harness\evidence\$TaskId"
$patchPath   = Join-Path $evidenceDir 'ai-raw.patch'
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null

# Stage everything except an earlier snapshot of this task, so the patch never contains itself.
& git add -A -- . ":(exclude)$patchRel"
if ($LASTEXITCODE -ne 0) { Fail 'git add -A failed; see the git message above. Nothing was written.' }

# git writes the patch itself, so PowerShell never re-encodes the bytes.
& git diff --cached --output="$patchPath"
$diffExit = $LASTEXITCODE

# Unstage again whatever happened, so the working tree is exactly as the AI left it.
& git reset -q
if ($LASTEXITCODE -ne 0) { Write-Host 'snap: git reset failed; run "git reset" by hand before continuing.' -ForegroundColor Yellow }
if ($diffExit -ne 0) { Fail 'git diff --cached failed; see the git message above.' }

$patchLines = 0
if (Test-Path $patchPath) { $patchLines = @(Get-Content -Path $patchPath).Count }
if ($patchLines -eq 0) {
  Write-Host "snap: the working tree has no changes; $patchRel is empty." -ForegroundColor Yellow
} else {
  Write-Host "snap: wrote $patchRel ($patchLines lines)." -ForegroundColor Green
}

$status = @(& git status --porcelain)
Write-Host ''
Write-Host 'Changed files (git status --porcelain):'
if ($status.Count -eq 0) {
  Write-Host '  (none)'
} else {
  $status | ForEach-Object { Write-Host "  $_" }
}

# Porcelain lines are "XY path" or "XY old -> new" for renames. Take the final path.
$paths = $status | ForEach-Object {
  $p = $_.Substring(3)
  if ($p -match ' -> ') { $p = ($p -split ' -> ')[-1] }
  $p.Trim('"')
} | Where-Object { $_ -and $_ -ne $patchRel }

Write-Host ''
Write-Host "Ledger stub for the entry of $TaskId (paste under files: and set each status to generated, edited, or rejected):"
Write-Host "    raw_output: $patchRel"
Write-Host '    files:'
if (@($paths).Count -eq 0) {
  Write-Host '      []'
} else {
  $paths | ForEach-Object { Write-Host "      - { path: $_, status: generated }" }
}
exit 0
