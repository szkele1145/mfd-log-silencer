# Upload project icon to Modrinth. Token from env MR_TOKEN.
$ErrorActionPreference = 'Stop'
if (-not $env:MR_TOKEN) { throw 'MR_TOKEN env var not set' }
if (-not $env:MR_PROXY) { $env:MR_PROXY = 'http://127.0.0.1:65532' }

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$icon = Join-Path $root 'src\main\resources\icon.png'
$projectId = 'hwFic1er'

Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.Proxy = New-Object System.Net.WebProxy($env:MR_PROXY)
$handler.UseProxy = $true
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(60)
$client.DefaultRequestHeaders.Add('Authorization', "Bearer $($env:MR_TOKEN)")
$client.DefaultRequestHeaders.Add('User-Agent', 'mfd-log-silencer-publisher/1.0')

$form = New-Object System.Net.Http.MultipartFormDataContent
$fs = [System.IO.File]::OpenRead($icon)
$fileContent = New-Object System.Net.Http.StreamContent($fs)
$fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('image/png')
$form.Add($fileContent, 'icon', 'icon.png')

$method = New-Object System.Net.Http.HttpMethod('PATCH')
$msg = New-Object System.Net.Http.HttpRequestMessage($method, "https://api.modrinth.com/v2/project/$projectId/icon`?ext=png")
$msg.Content = $form

try {
    $resp = $client.SendAsync($msg).GetAwaiter().GetResult()
    $text = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $status = [int]$resp.StatusCode
    Write-Host "HTTP $status $text"
    if ($status -lt 200 -or $status -ge 300) { throw "icon upload failed" }
} finally {
    $form.Dispose(); $msg.Dispose(); $client.Dispose()
}
Write-Host 'ICON OK'
