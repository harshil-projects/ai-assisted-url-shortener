<#
.SYNOPSIS
  Walk the API against a running service and print one "step: expected -> actual" line per call.

.DESCRIPTION
  Steps, in order:
    GET  /actuator/health                       expects 200
    POST /api/v1/links {"url":"https://example.com/docs"} with the key, expects 201
    GET  /{code} without following the redirect, expects 302 and shows the Location header
    GET  /api/v1/links/{code}/stats with the key, expects 200
    POST /api/v1/links without the key, expects 401
    GET  /nope-404, expects 404

  Works on Windows PowerShell 5.1 and PowerShell 7. Uses Invoke-WebRequest with -SkipHttpErrorCheck
  when the cmdlet supports it and try/catch otherwise. The redirect step uses HttpWebRequest with
  AllowAutoRedirect off, because Windows PowerShell 5.1 throws on a 3xx when -MaximumRedirection is 0.
  Exits 1 when any step did not return the expected status.

.PARAMETER BaseUrl
  Base URL of the running service. Default http://localhost:8080.

.PARAMETER ApiKey
  Value for the X-API-Key header. Default dev-only-key-do-not-use, the dev profile default.

.EXAMPLE
  scripts/demo.ps1
.EXAMPLE
  scripts/demo.ps1 -BaseUrl http://localhost:9090 -ApiKey my-local-key
#>
[CmdletBinding()]
param(
  [string] $BaseUrl = 'http://localhost:8080',
  [string] $ApiKey = 'dev-only-key-do-not-use'
)

$ErrorActionPreference = 'Continue'
$BaseUrl = $BaseUrl.TrimEnd('/')
$canSkipErrorCheck = (Get-Command Invoke-WebRequest).Parameters.ContainsKey('SkipHttpErrorCheck')

function Get-HeaderValue($Response, [string] $Name) {
  if ($null -eq $Response -or $null -eq $Response.Headers) { return $null }
  try {
    $v = $Response.Headers[$Name]
    if ($null -ne $v) { return (@($v) -join ' ') }
  } catch { }
  try {
    $p = $Response.Headers.PSObject.Properties[$Name]
    if ($null -ne $p -and $null -ne $p.Value) { return "$($p.Value)" }
  } catch { }
  return $null
}

# Invoke-WebRequest path: used for every step except the redirect.
function Send-WithCmdlet([string] $Method, [string] $Url, [hashtable] $Headers, [string] $Body) {
  $request = @{ Uri = $Url; Method = $Method; Headers = $Headers; UseBasicParsing = $true; TimeoutSec = 15; ErrorAction = 'Stop' }
  if ($Body) { $request['Body'] = $Body; $request['ContentType'] = 'application/json' }
  if ($script:canSkipErrorCheck) { $request['SkipHttpErrorCheck'] = $true }
  $out = @{ Status = $null; Location = $null; Content = $null; Note = '' }
  try {
    $response = Invoke-WebRequest @request
    $out.Status   = [int] $response.StatusCode
    $out.Location = Get-HeaderValue $response 'Location'
    $out.Content  = $response.Content
  } catch {
    $errorResponse = $_.Exception.Response
    if ($null -ne $errorResponse -and $null -ne $errorResponse.StatusCode) {
      $out.Status   = [int] $errorResponse.StatusCode
      $out.Location = Get-HeaderValue $errorResponse 'Location'
    } else {
      $out.Note = $_.Exception.Message
    }
  }
  return $out
}

# HttpWebRequest path: the redirect step, with auto-redirect off so the 302 itself is observed.
function Send-WithoutRedirect([string] $Method, [string] $Url, [hashtable] $Headers) {
  $out = @{ Status = $null; Location = $null; Content = $null; Note = '' }
  try {
    $web = [System.Net.HttpWebRequest]::Create($Url)
    $web.Method = $Method
    $web.AllowAutoRedirect = $false
    $web.Timeout = 15000
    foreach ($name in $Headers.Keys) { $web.Headers[$name] = $Headers[$name] }
    $raw = $web.GetResponse()
    $out.Status   = [int] $raw.StatusCode
    $out.Location = $raw.Headers['Location']
    $raw.Close()
  } catch {
    $ex = $_.Exception
    if ($null -ne $ex.InnerException -and $null -ne $ex.InnerException.Response) { $ex = $ex.InnerException }
    if ($null -ne $ex.Response) {
      $out.Status   = [int] $ex.Response.StatusCode
      $out.Location = $ex.Response.Headers['Location']
      $ex.Response.Close()
    } else {
      $out.Note = $ex.Message
    }
  }
  return $out
}

function Invoke-Step {
  param(
    [string] $Label,
    [string] $Method,
    [string] $Url,
    [int] $Expected,
    [hashtable] $Headers = @{},
    [string] $Body = $null,
    [switch] $NoRedirect
  )
  if ($NoRedirect) { $r = Send-WithoutRedirect $Method $Url $Headers }
  else { $r = Send-WithCmdlet $Method $Url $Headers $Body }

  $actual = 'no response'
  if ($null -ne $r.Status) { $actual = "$($r.Status)" }
  elseif ($r.Note) { $actual = "no response ($($r.Note))" }
  $ok = ($r.Status -eq $Expected)
  $line = "{0} {1}: {2} -> {3}" -f $(if ($ok) { 'ok  ' } else { 'FAIL' }), $Label, $Expected, $actual
  if ($r.Location) { $line += "    Location: $($r.Location)" }
  Write-Host $line -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
  return [pscustomobject]@{ Ok = $ok; Status = $r.Status; Content = $r.Content; Location = $r.Location }
}

Write-Host "demo: base URL $BaseUrl" -ForegroundColor Cyan
$steps = New-Object System.Collections.Generic.List[object]
$authHeaders = @{ 'X-API-Key' = $ApiKey }
$createBody  = '{"url":"https://example.com/docs"}'

$steps.Add((Invoke-Step -Label 'GET /actuator/health' -Method GET -Url "$BaseUrl/actuator/health" -Expected 200))

$create = Invoke-Step -Label 'POST /api/v1/links' -Method POST -Url "$BaseUrl/api/v1/links" -Expected 201 -Headers $authHeaders -Body $createBody
$steps.Add($create)

$code = $null
if ($create.Content) {
  try { $code = ($create.Content | ConvertFrom-Json).code } catch { $code = $null }
}
if (-not $code) {
  $code = 'unknown'
  Write-Host 'demo: the create response had no code; the redirect and stats steps use "unknown" and will not match.' -ForegroundColor Yellow
}

$steps.Add((Invoke-Step -Label "GET /$code (redirect not followed)" -Method GET -Url "$BaseUrl/$code" -Expected 302 -NoRedirect))
$steps.Add((Invoke-Step -Label "GET /api/v1/links/$code/stats" -Method GET -Url "$BaseUrl/api/v1/links/$code/stats" -Expected 200 -Headers $authHeaders))
$steps.Add((Invoke-Step -Label 'POST /api/v1/links without X-API-Key' -Method POST -Url "$BaseUrl/api/v1/links" -Expected 401 -Body $createBody))
$steps.Add((Invoke-Step -Label 'GET /nope-404' -Method GET -Url "$BaseUrl/nope-404" -Expected 404))

$failed = @($steps | Where-Object { -not $_.Ok }).Count
Write-Host ''
Write-Host "demo: $($steps.Count - $failed) of $($steps.Count) steps returned the expected status." -ForegroundColor $(if ($failed -eq 0) { 'Green' } else { 'Red' })
if ($failed -eq 0) { exit 0 }
exit 1
