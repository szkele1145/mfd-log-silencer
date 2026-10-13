# Publish MFD Log Silencer to Modrinth. Token comes from env MR_TOKEN.
# All literals in this file are ASCII on purpose (project path contains non-ASCII chars).
$ErrorActionPreference = 'Stop'

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jar  = Join-Path $root 'build\libs\mfd-log-silencer-1.0.0.jar'
$readmePath = Join-Path $root 'README.md'

if (-not $env:MR_TOKEN) { throw 'MR_TOKEN env var not set' }
if (-not (Test-Path $jar)) { throw "jar not found: $jar" }

$body = [System.IO.File]::ReadAllText($readmePath, [System.Text.Encoding]::UTF8)
$description = 'Silences the per-second INFO log spam of fixed Tweaked Controller (MFD-FixedController signals pos=...). WARN/ERROR still show. NeoForge 1.21.1.'
$changelog = 'Initial release. Denies INFO/DEBUG logs of fixed_tweaked_controller; WARN/ERROR pass through.'

Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(120)
$client.DefaultRequestHeaders.Add('Authorization', "Bearer $($env:MR_TOKEN)")
$client.DefaultRequestHeaders.Add('User-Agent', 'mfd-log-silencer-publisher/1.0')

$data = @{
    slug         = 'mfd-log-silencer'
    title        = 'MFD Log Silencer'
    description  = $description
    body         = $body
    license_id   = 'MIT'
    project_type = 'mod'
    categories   = @('utility')
    client_side  = 'optional'
    server_side  = 'optional'
    game_versions = @('1.21.1')
    loaders      = @('neoforge')
    initial_versions = @(
        @{
            name          = '1.0.0'
            version_number= '1.0.0'
            changelog     = $changelog
            dependencies  = @(@{ project_id = 'RmyppZtQ'; dependency_type = 'required' })
            game_versions = @('1.21.1')
            loaders       = @('neoforge')
            featured      = $true
            file_type     = 'primary'
            file_parts    = @('file0')
            release_channel = 'release'
        }
    )
} | ConvertTo-Json -Depth 6

$form = New-Object System.Net.Http.MultipartFormDataContent
$dataContent = New-Object System.Net.Http.StringContent($data, [System.Text.Encoding]::UTF8, 'application/json')
$form.Add($dataContent, 'data')
$fs = [System.IO.File]::OpenRead($jar)
$fileContent = New-Object System.Net.Http.StreamContent($fs)
$fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/octet-stream')
$form.Add($fileContent, 'file0', [System.IO.Path]::GetFileName($jar))

try {
    $resp = $client.PostAsync('https://api.modrinth.com/v2/project', $form).GetAwaiter().GetResult()
    $text = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $status = [int]$resp.StatusCode
    if ($status -lt 200 -or $status -ge 300) {
        throw "project create failed HTTP $status`: $text"
    }
    $project = $text | ConvertFrom-Json
    Write-Host ("PROJECT OK: id={0} slug={1}" -f $project.id, $project.slug)
    Write-Host ("URL: https://modrinth.com/mod/{0}" -f $project.slug)
} finally {
    $form.Dispose()
}

$client.Dispose()
