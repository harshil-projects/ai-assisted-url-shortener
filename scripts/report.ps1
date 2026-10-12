<#
.SYNOPSIS
  Regenerate the traceability report and snapshot it into docs/reports/ (lifecycle step 10).

.DESCRIPTION
  Runs .\mvnw.cmd -q -pl harness-check test, then copies harness-check/target/traceability.md to
  docs/reports/traceability.md, creating the folder when needed. The report is deterministic and
  carries no timestamps, so the copy only changes when the harness data changes.

  Exits with the Maven exit code. When the harness-check tests fail, the report is still copied if
  it was produced, and a warning says the snapshot reflects a failing state.

.EXAMPLE
  scripts/report.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Continue'

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot
if (-not (Test-Path (Join-Path $repoRoot 'mvnw.cmd'))) {
  Write-Host 'report: mvnw.cmd was not found at the repository root.' -ForegroundColor Red
  exit 1
}

Write-Host 'report: > .\mvnw.cmd -q -pl harness-check test' -ForegroundColor Cyan
cmd /c ".\mvnw.cmd -q -pl harness-check test 2>&1" | ForEach-Object { Write-Host $_ }
$mavenExit = $LASTEXITCODE

$source    = Join-Path $repoRoot 'harness-check\target\traceability.md'
$targetDir = Join-Path $repoRoot 'docs\reports'
$target    = Join-Path $targetDir 'traceability.md'

if (-not (Test-Path $source)) {
  Write-Host "report: harness-check/target/traceability.md was not produced (Maven exit code $mavenExit)." -ForegroundColor Red
  if ($mavenExit -eq 0) { exit 1 }
  exit $mavenExit
}

New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
Copy-Item -Path $source -Destination $target -Force
Write-Host 'report: copied harness-check/target/traceability.md to docs/reports/traceability.md' -ForegroundColor Green

if ($mavenExit -ne 0) {
  Write-Host "report: the harness-check tests failed (exit code $mavenExit); the snapshot reflects a failing state." -ForegroundColor Yellow
}
exit $mavenExit
