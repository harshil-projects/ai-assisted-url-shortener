<#
.SYNOPSIS
  Run the quality gates for a task and append the result to the ledger (lifecycle step 7).

.DESCRIPTION
  Runs .\mvnw.cmd -q spotless:apply, then .\mvnw.cmd -ntp verify (with -Pperf when -Perf is given).
  Output is shown live and teed to harness/evidence/T-NNN/verify-N.log, where N increments per run so
  red runs are kept as evidence. A gate record is then inserted at the end of the gates: list in
  harness/ledger.yaml with the current commit, the UTC timestamp, the overall result from the Maven
  exit code, and one result per gate key.

  On a red run the failing Maven goal ("Failed to execute goal ...") is mapped to a gate key:
    spotless -> format, checkstyle -> style, spotbugs -> sast, surefire -> tests
    (or traceability when the failing project is harness-check), jacoco -> coverage.
  Every other key is recorded as not-reached. perf is "skipped: opt-in profile not run" unless -Perf.
  secrets is pass when the traceability tests passed, because the regex secret scan runs inside them.

  Requires PowerShell 5.1 or later and git on PATH. The script exits with the Maven exit code.

.PARAMETER TaskId
  Task id in the form T-NNN, for example T-004.

.PARAMETER Perf
  Also run the opt-in perf profile (-Pperf) so RedirectLatencySmokeTest executes.

.EXAMPLE
  scripts/gate.ps1 T-004
.EXAMPLE
  scripts/gate.ps1 T-004 -Perf
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0)]
  [string] $TaskId,
  [switch] $Perf
)

$ErrorActionPreference = 'Continue'

function Fail([string] $Message) {
  Write-Host "gate: $Message" -ForegroundColor Red
  Write-Host 'Usage: scripts/gate.ps1 T-NNN [-Perf]    (example: scripts/gate.ps1 T-004)'
  exit 1
}

if ([string]::IsNullOrWhiteSpace($TaskId)) { Fail 'the task id is missing.' }
if ($TaskId -notmatch '^T-\d{3}$') { Fail "task id '$TaskId' does not match ^T-\d{3}$ (three digits, for example T-004)." }

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot
if (-not (Test-Path (Join-Path $repoRoot 'mvnw.cmd'))) { Fail 'mvnw.cmd was not found at the repository root.' }

$ledgerPath  = Join-Path $repoRoot 'harness\ledger.yaml'
$evidenceDir = Join-Path $repoRoot "harness\evidence\$TaskId"
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null

# Next log number: verify-N.log with N one above the highest existing.
$numbers = @(Get-ChildItem -Path $evidenceDir -Filter 'verify-*.log' -File |
  ForEach-Object { if ($_.BaseName -match '^verify-(\d+)$') { [int] $Matches[1] } })
$n = 1
if ($numbers.Count -gt 0) { $n = ($numbers | Measure-Object -Maximum).Maximum + 1 }
$logName = "verify-$n.log"
$logPath = Join-Path $evidenceDir $logName
$logRel  = "harness/evidence/$TaskId/$logName"

$utf8  = New-Object System.Text.UTF8Encoding($false)
$runAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')

$writer = New-Object System.IO.StreamWriter($logPath, $false, $utf8)

function Invoke-Logged([string] $CommandLine) {
  # cmd /c merges stderr into stdout, so Windows PowerShell never turns Maven's stderr into error records.
  Write-Host "gate: > $CommandLine" -ForegroundColor Cyan
  $script:writer.WriteLine("### $CommandLine")
  cmd /c "$CommandLine 2>&1" | ForEach-Object { Write-Host $_; $script:writer.WriteLine($_) }
  $script:writer.Flush()
  return $LASTEXITCODE
}

$verifyArgs = '-ntp verify'
if ($Perf) { $verifyArgs = '-ntp -Pperf verify' }

Write-Host "gate: $TaskId run $n at $runAt, log $logRel"
$writer.WriteLine("# gate $TaskId run $n at $runAt")

try {
  $formatExit = Invoke-Logged '.\mvnw.cmd -q spotless:apply'
  if ($formatExit -ne 0) {
    Write-Host 'gate: spotless:apply failed; continuing to verify so the log records the cause.' -ForegroundColor Yellow
  }
  $verifyExit = Invoke-Logged ".\mvnw.cmd $verifyArgs"
  $writer.WriteLine("# exit code $verifyExit")
} finally {
  $writer.Dispose()
}

# Map the result.
$logText = [System.IO.File]::ReadAllText($logPath)
$overall = 'fail'
if ($verifyExit -eq 0) { $overall = 'pass' }

$keys = @('format', 'style', 'sast', 'tests', 'coverage', 'traceability', 'secrets', 'perf')
$results = @{}
$failedGoal = $null
$perfSkipped = 'skipped: opt-in profile not run'

if ($overall -eq 'pass') {
  foreach ($k in $keys) { $results[$k] = 'pass' }
  if (-not $Perf) { $results['perf'] = $perfSkipped }
} else {
  foreach ($k in $keys) { $results[$k] = 'not-reached' }
  if (-not $Perf) { $results['perf'] = $perfSkipped }
  $m = [regex]::Match($logText, 'Failed to execute goal (?<coord>\S+)(?: \((?<exec>[^)]*)\))? on project (?<project>[^:\s]+)')
  if ($m.Success) {
    $coord    = $m.Groups['coord'].Value
    $project  = $m.Groups['project'].Value
    $parts    = $coord -split ':'
    $artifact = $coord
    if ($parts.Count -ge 2) { $artifact = $parts[1] }
    $failedGoal = "$coord on project $project"
    $key = $null
    switch -Regex ($artifact) {
      'spotless'          { $key = 'format'; break }
      'checkstyle'        { $key = 'style'; break }
      'spotbugs'          { $key = 'sast'; break }
      'surefire|failsafe' { if ($project -eq 'harness-check') { $key = 'traceability' } else { $key = 'tests' }; break }
      'jacoco'            { $key = 'coverage'; break }
    }
    if ($key) { $results[$key] = 'fail' }
    if ($Perf -and $key -eq 'tests') {
      $perfLine = [regex]::IsMatch($logText, '(?m)^.*RedirectLatencySmokeTest.*$')
      $perfFail = [regex]::IsMatch($logText, '(?m)^.*(FAIL|ERROR).*RedirectLatencySmokeTest.*$|^.*RedirectLatencySmokeTest.*(FAIL|ERROR).*$')
      if ($perfLine -and $perfFail) { $results['perf'] = 'fail' }
    }
  }
}

# Commit and tree state.
$commit = (& git rev-parse --verify HEAD 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace("$commit")) { $commit = 'uncommitted' }
$commit = "$commit".Trim()
$tree = 'clean'
if (@(& git status --porcelain 2>$null).Count -gt 0) { $tree = 'dirty' }

# YAML block. Values holding a colon or hash are quoted so they stay strings.
function Quote([string] $Value) {
  if ($Value -match '[:#"]') { return '"' + $Value.Replace('"', '\"') + '"' }
  return $Value
}

$block = New-Object System.Collections.Generic.List[string]
$block.Add("  - task: $TaskId")
$block.Add("    commit: `"$commit`"")
$block.Add("    tree: $tree")
$block.Add("    run_at: `"$runAt`"")
$block.Add("    overall: $overall")
$block.Add('    results:')
foreach ($k in $keys) { $block.Add("      ${k}: $(Quote $results[$k])") }
if ($failedGoal) { $block.Add("    failed_goal: $(Quote $failedGoal)") }
$block.Add("    log: $logRel")

# Insert the block at the end of the gates: list. Plain line handling, no YAML parser.
if (-not (Test-Path $ledgerPath)) {
  New-Item -ItemType Directory -Path (Split-Path -Parent $ledgerPath) -Force | Out-Null
  [System.IO.File]::WriteAllText($ledgerPath, "entries: []`ngates: []`nsignoffs: []`n", $utf8)
}
$raw = [System.IO.File]::ReadAllText($ledgerPath)
$nl = "`n"
if ($raw -match "`r`n") { $nl = "`r`n" }
$lines = New-Object System.Collections.Generic.List[string]
$lines.AddRange([string[]] @($raw -split "`r?`n"))
while ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -eq '') { $lines.RemoveAt($lines.Count - 1) }

$gatesIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -match '^gates:\s*(\[\s*\])?\s*(#.*)?$') { $gatesIdx = $i; break }
}
if ($gatesIdx -lt 0) {
  $lines.Add('gates:')
  $insertAt = $lines.Count
} else {
  if ($lines[$gatesIdx] -match '^gates:\s*\[\s*\]') { $lines[$gatesIdx] = 'gates:' }
  $insertAt = $lines.Count
  for ($j = $gatesIdx + 1; $j -lt $lines.Count; $j++) {
    if ($lines[$j] -match '^[A-Za-z_][A-Za-z0-9_-]*:') { $insertAt = $j; break }
  }
}
$lines.InsertRange($insertAt, $block)
[System.IO.File]::WriteAllText($ledgerPath, (($lines -join $nl) + $nl), $utf8)

Write-Host ''
Write-Host "gate: $TaskId run $n overall $overall (commit $commit, tree $tree)" -ForegroundColor $(if ($overall -eq 'pass') { 'Green' } else { 'Red' })
foreach ($k in $keys) { Write-Host ("  {0,-13} {1}" -f $k, $results[$k]) }
if ($failedGoal) { Write-Host "  failed goal   $failedGoal" }
Write-Host "  log           $logRel"
Write-Host '  ledger        harness/ledger.yaml (gate record appended)'
exit $verifyExit
