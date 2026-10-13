# Publish a new version of MFD Log Silencer to Modrinth. Token from env MR_TOKEN.
# Usage: MR_TOKEN=... powershell -File publish-version.ps1 -Version 1.1.0
param(
    [Parameter(Mandatory = $true)][string]$Version
)
$ErrorActionPreference = 'Stop'
if (-not $env:MR_TOKEN) { throw 'MR_TOKEN env var not set' }

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jar  = Join-Path $root ("build\libs\mfd-log-silencer-{0}.jar" -f $Version)
if (-not (Test-Path $jar)) { throw "jar not found: $jar" }

$changelog = @"
v$Version

- Fixed: the v1.0.0 filter used a LevelRangeFilter with (TRACE, INFO) which is an
  empty range under log4j2 intLevel semantics, so nothing was actually filtered.
  The filter now correctly denies INFO/DEBUG/TRACE and keeps WARN/ERROR
  (validated by a standalone JVM smoke test).
- Added: config file config/mfd-log-silencer.properties (custom logger list and
  per-logger threshold, e.g. silence other spammy mods or WARN too).
- Added: /mfdlogsilencer status | reload | about (status shows blocked count and
  recent samples; reload re-reads the config in-game).
- Added: blocked-message statistics with idempotent filter re-installation.
"@

Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.Proxy = New-Object System.Net.WebProxy($env:MR_PROXY)
$handler.UseProxy = $true
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(120)
$client.DefaultRequestHeaders.Add('Authorization', "Bearer $($env:MR_TOKEN)")
$client.DefaultRequestHeaders.Add('User-Agent', 'mfd-log-silencer-publisher/1.0')

$data = @{
    name          = $Version
    version_number= $Version
    changelog     = $changelog
    dependencies  = @(@{ project_id = 'RmyppZtQ'; dependency_type = 'required' })
    game_versions = @('1.21.1')
    loaders       = @('neoforge')
    featured      = $true
    file_type     = 'primary'
    file_parts    = @('file0')
    release_channel = 'release'
    project_id    = 'hwFic1er'
} | ConvertTo-Json -Depth 5

$form = New-Object System.Net.Http.MultipartFormDataContent
$dataContent = New-Object System.Net.Http.StringContent($data, [System.Text.Encoding]::UTF8, 'application/json')
$form.Add($dataContent, 'data')
$fs = [System.IO.File]::OpenRead($jar)
$fileContent = New-Object System.Net.Http.StreamContent($fs)
$fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/octet-stream')
$form.Add($fileContent, 'file0', [System.IO.Path]::GetFileName($jar))

try {
    $resp = $client.PostAsync('https://api.modrinth.com/v2/version', $form).GetAwaiter().GetResult()
    $text = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $status = [int]$resp.StatusCode
    if ($status -lt 200 -or $status -ge 300) {
        throw "version create failed HTTP $status`: $text"
    }
    $version = $text | ConvertFrom-Json
    Write-Host ("VERSION OK: id={0} number={1}" -f $version.id, $version.version_number)
} finally {
    $form.Dispose(); $client.Dispose()
}
