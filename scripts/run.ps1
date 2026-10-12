<#
.SYNOPSIS
  Run the url-shortener service with the dev profile.

.DESCRIPTION
  Runs .\mvnw.cmd -pl url-shortener spring-boot:run. With -Port, SERVER_PORT is set for the
  process so Spring Boot listens on that port; the previous value is restored when the script ends.
  The dev profile uses an H2 file database under ./data and the API key dev-only-key-do-not-use.

.PARAMETER Port
  Optional TCP port, 1 to 65535. Default: whatever the application properties say, normally 8080.

.EXAMPLE
  scripts/run.ps1
.EXAMPLE
  scripts/run.ps1 -Port 9090
#>
[CmdletBinding()]
param(
  [ValidateRange(1, 65535)]
  [int] $Port = 0
)

$ErrorActionPreference = 'Continue'

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot
if (-not (Test-Path (Join-Path $repoRoot 'mvnw.cmd'))) {
  Write-Host 'run: mvnw.cmd was not found at the repository root.' -ForegroundColor Red
  exit 1
}

$previousPort = $env:SERVER_PORT
try {
  if ($Port -gt 0) {
    $env:SERVER_PORT = "$Port"
    Write-Host "run: SERVER_PORT=$Port" -ForegroundColor Cyan
  }
  Write-Host 'run: > .\mvnw.cmd -pl url-shortener spring-boot:run' -ForegroundColor Cyan
  & .\mvnw.cmd -pl url-shortener spring-boot:run
  $exitCode = $LASTEXITCODE
} finally {
  $env:SERVER_PORT = $previousPort
}
exit $exitCode
