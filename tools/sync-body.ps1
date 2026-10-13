# Sync project body (README) to Modrinth. Token from env MR_TOKEN.
$ErrorActionPreference = 'Stop'
if (-not $env:MR_TOKEN) { throw 'MR_TOKEN env var not set' }

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$readme = [System.IO.File]::ReadAllText((Join-Path $root 'README.md'), [System.Text.Encoding]::UTF8)
$intro = 'Silences the per-second INFO log spam of fixed Tweaked Controller (MFD-FixedController signals pos=...). WARN/ERROR still show. Config file + /mfdlogsilencer status|reload|reset|about commands. NeoForge 1.21.1.'
$body = $intro + "`n`n---`n`n" + $readme

$payload = @{ body = $body } | ConvertTo-Json
$bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)

Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.Proxy = New-Object System.Net.WebProxy('http://127.0.0.1:65532')
$handler.UseProxy = $true
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(60)
$client.DefaultRequestHeaders.Add('Authorization', "Bearer $($env:MR_TOKEN)")
$client.DefaultRequestHeaders.Add('User-Agent', 'mfd-log-silencer-publisher/1.0')

$content = New-Object System.Net.Http.ByteArrayContent(, $bytes)
$content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/json')

try {
    $msg = New-Object System.Net.Http.HttpRequestMessage((New-Object System.Net.Http.HttpMethod('PATCH')), 'https://api.modrinth.com/v2/project/hwFic1er')
    $msg.Content = $content
    $resp = $client.SendAsync($msg).GetAwaiter().GetResult()
    $status = [int]$resp.StatusCode
    Write-Host "HTTP $status"
    if ($status -ne 204) {
        Write-Host ($resp.Content.ReadAsStringAsync().GetAwaiter().GetResult())
        throw 'body update failed'
    }
} finally {
    $client.Dispose()
}
Write-Host 'BODY OK'
