#Requires -Version 5.1
Set-StrictMode -Version 2.0

function Find-DemoJava17 {
    param([string[]]$Candidates)
    foreach ($candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $java = Join-Path $candidate 'bin\java.exe'
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $java) -and (Test-Path -LiteralPath $release)) {
            $version = Get-Content -LiteralPath $release | Where-Object { $_ -match '^JAVA_VERSION="17(\.|\")' }
            if ($version) { return (Get-Item -LiteralPath $java).FullName }
        }
    }
    throw 'JDK 17 was not found. Install JDK 17 or set DEMO_JAVA_HOME for this process.'
}

function Find-DemoNacos {
    param([string[]]$Candidates)
    foreach ($candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $jar = Join-Path $candidate 'target\nacos-server.jar'
        if (Test-Path -LiteralPath $jar) {
            return [pscustomobject]@{ Home = (Get-Item -LiteralPath $candidate).FullName; Jar = (Get-Item -LiteralPath $jar).FullName }
        }
    }
    throw 'Nacos 2.x target\nacos-server.jar was not found. Set DEMO_NACOS_HOME for this process.'
}

function Find-DemoServiceJar {
    param([string]$Root, [string]$Name)
    $dir = Join-Path $Root "src\$Name\target"
    $found = @(Get-ChildItem -LiteralPath $dir -Filter '*.jar' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '(^original-|\.original$|-sources\.jar$|-javadoc\.jar$)' } |
        Sort-Object LastWriteTime -Descending)
    if ($found.Count -eq 0) { throw "Missing packaged jar for $Name. Run mvn -B package in src using JDK 17 first." }
    return $found[0].FullName
}

function Assert-DemoSecureJar {
    param([string]$Path)
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $zip.GetEntry('BOOT-INF/classes/security-boundary.version')
        if (-not $entry) { throw 'Old backend jar detected. Rebuild all six services with JDK 17: mvn -B package.' }
        $reader = New-Object IO.StreamReader($entry.Open())
        try { $version = $reader.ReadToEnd().Trim() } finally { $reader.Dispose() }
        if ($version -ne '1') { throw 'Backend security revision is unsupported. Rebuild all six services.' }
    } finally { $zip.Dispose() }
}

function Get-DemoPortAction {
    param([bool]$Listening, [bool]$Owned, [bool]$Ready, [bool]$PendingProcess = $false)
    if (-not $Listening -and $PendingProcess -and $Owned) { return 'wait' }
    if (-not $Listening) { return 'start' }
    if (-not $Owned) { throw 'Port is occupied by another or unverified process. It will not be stopped.' }
    if ($Ready) { return 'reuse' }
    return 'wait'
}

function Get-DemoInternalToken {
    param([string]$RunDirectory, [string]$ExplicitToken = '',
          [ValidateSet('demo-internal-token.xml','demo-jwt-secret.xml')][string]$FileName = 'demo-internal-token.xml')
    if ($ExplicitToken) {
        if ($ExplicitToken.Length -lt 32) { throw 'INTERNAL_TOKEN must contain at least 32 characters.' }
        return $ExplicitToken
    }
    $path = Join-Path $RunDirectory $FileName
    if (Test-Path -LiteralPath $path) {
        $secure = Import-Clixml -LiteralPath $path
        try { $value = (New-Object System.Net.NetworkCredential('', $secure)).Password }
        finally { if ($secure -is [Security.SecureString]) { $secure.Dispose() } }
        if ($value.Length -lt 32) { throw 'Stored internal token is invalid; do not reuse existing services.' }
        return $value
    }
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $value = [Convert]::ToBase64String($bytes)
    $secure = ConvertTo-SecureString -String $value -AsPlainText -Force
    try { $secure | Export-Clixml -LiteralPath $path -Encoding UTF8 }
    finally { $secure.Dispose() }
    return $value
}

function Get-DemoTextDigest {
    param([string]$Value)
    $hash = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-', '') }
    finally { $hash.Dispose() }
}

function Read-DemoProcessRecords {
    param([string]$Path)
    $result = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $result }
    # PS5.1 ConvertFrom-Json emits the JSON array as one pipeline object inside @(...).
    # Assign directly so foreach enumerates the array's records, not a nested array.
    $entries = ConvertFrom-Json -InputObject (Get-Content -LiteralPath $Path -Raw -Encoding UTF8)
    foreach ($entry in $entries) {
        if ($entry -and $null -ne $entry.PSObject.Properties['Name'] -and $entry.Name) {
            $result[[string]$entry.Name] = $entry
        }
    }
    return $result
}

function Get-DemoRecordedProcessId {
    param($Record, [string]$Artifact)
    if ($null -eq $Record) { return 0 }
    foreach ($field in @('ProcessId','StartedUtc','Artifact')) {
        if ($null -eq $Record.PSObject.Properties[$field]) { return 0 }
    }
    if (-not [string]::Equals([string]$Record.Artifact, $Artifact, [StringComparison]::OrdinalIgnoreCase)) { return 0 }
    $proc = Get-Process -Id ([int]$Record.ProcessId) -ErrorAction SilentlyContinue
    if (-not $proc) { return 0 }
    try {
        $elapsed = [Math]::Abs(($proc.StartTime.ToUniversalTime() - [datetime]::Parse($Record.StartedUtc).ToUniversalTime()).TotalSeconds)
        if ($elapsed -lt 2) { return [int]$proc.Id }
    } catch { }
    return 0
}

function Test-DemoFrontendBinding {
    param($Record)
    if ($null -eq $Record -or $null -eq $Record.PSObject.Properties['GatewayTarget']) { return $false }
    return ([string]$Record.GatewayTarget -eq 'http://127.0.0.1:9000')
}

function Test-DemoCommandOwnership {
    param([string]$CommandLine, [string]$Artifact)
    if (-not $CommandLine -or -not $Artifact) { return $false }
    # Do not treat service.jar.old or another checkout as this artifact.
    $pattern = '(?i)(?:^|[\s"])' + [regex]::Escape($Artifact) + '(?=[\s"]|$)'
    return [regex]::IsMatch($CommandLine, $pattern)
}

function ConvertTo-DemoArgument {
    param([AllowEmptyString()][string]$Value)
    # Windows CommandLineToArgvW quoting, including embedded quotes and trailing backslashes.
    $result = '"'
    $slashes = 0
    foreach ($char in $Value.ToCharArray()) {
        if ($char -eq '\') { $slashes++; continue }
        if ($char -eq '"') {
            $result += ('\' * ($slashes * 2 + 1)) + '"'
        } else {
            $result += ('\' * $slashes) + $char
        }
        $slashes = 0
    }
    return $result + ('\' * ($slashes * 2)) + '"'
}

function Start-DemoChild {
    param([string]$FilePath, [AllowEmptyCollection()][string[]]$Arguments,
          [string]$WorkingDirectory, [string]$LogPrefix, [hashtable]$Environment = @{})
    $saved = @{}
    try {
        foreach ($key in $Environment.Keys) {
            $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, [string]$Environment[$key], 'Process')
        }
        $argsText = (@($Arguments | ForEach-Object { ConvertTo-DemoArgument $_ }) -join ' ')
        $p = Start-Process -FilePath $FilePath -ArgumentList $argsText -WorkingDirectory $WorkingDirectory `
            -RedirectStandardOutput ($LogPrefix + '.out.log') -RedirectStandardError ($LogPrefix + '.err.log') `
            -WindowStyle Hidden -PassThru
        # Retain the native process handle so ExitCode remains readable after a short-lived child exits (Windows PowerShell 5.1).
        $null = $p.Handle
        return $p
    } finally {
        foreach ($key in $saved.Keys) {
            [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process')
        }
    }
}

function Wait-DemoReady {
    param([string]$Name, [scriptblock]$Probe, [int]$TimeoutSeconds = 120,
          [int]$PollMilliseconds = 1000, [int]$ProcessId = 0)
    $watch = [Diagnostics.Stopwatch]::StartNew()
    do {
        if (& $Probe) { return }
        if ($ProcessId -gt 0 -and -not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
            throw "$Name exited before becoming ready. See the current demo log directory."
        }
        if ($watch.Elapsed.TotalSeconds -ge $TimeoutSeconds) { break }
        Start-Sleep -Milliseconds $PollMilliseconds
    } while ($true)
    throw "$Name did not become ready within $TimeoutSeconds seconds. See the current demo log directory."
}

function Get-DemoHttpJson {
    param([string]$Uri)
    try {
        $r = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($r.StatusCode -ne 200) { return $null }
        $body = $r.Content
        if ($body -is [byte[]]) { $body = [Text.Encoding]::UTF8.GetString($body) }
        # -InputObject parses one JSON document; piping byte[] parses each number separately on Windows PowerShell 5.1.
        return (ConvertFrom-Json -InputObject ([string]$body))
    } catch { return $null }
}

function Test-DemoServiceHealth {
    param([int]$Port)
    $j = Get-DemoHttpJson "http://127.0.0.1:$Port/actuator/health"
    return ($null -ne $j -and $null -ne $j.PSObject.Properties['status'] -and $j.status -eq 'UP')
}

function Test-DemoNacosReady {
    $j = Get-DemoHttpJson 'http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=50'
    return ($null -ne $j -and $null -ne $j.PSObject.Properties['count'])
}

function Test-DemoFrontendReady {
    param([string]$Marker)
    try {
        $r = Invoke-WebRequest 'http://127.0.0.1:5173/' -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($r.StatusCode -ne 200) { return $false }
        if ($Marker -and -not $r.Content.Contains($Marker)) { return $false }
        $j = Get-DemoHttpJson 'http://127.0.0.1:5173/api/products?page=1&size=1'
        return ($null -ne $j -and $null -ne $j.PSObject.Properties['code'] -and $j.code -eq 0)
    } catch { return $false }
}

function Get-DemoListener {
    param([int]$Port)
    # Avoid slow per-port Get-NetTCPConnection queries on Windows.
    foreach ($line in @(netstat.exe -ano -p tcp)) {
        if ($line -match '^\s*TCP\s+\S+:(\d+)\s+\S+\s+LISTENING\s+(\d+)\s*$') {
            if ([int]$matches[1] -eq $Port) { return [int]$matches[2] }
        }
    }
    return 0
}

function Get-DemoCommandLine {
    param([int]$ProcessId)
    $p = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue
    if ($p) { return [string]$p.CommandLine }
    return ''
}

Export-ModuleMember -Function Find-DemoJava17,Find-DemoNacos,Find-DemoServiceJar,Assert-DemoSecureJar,Get-DemoPortAction,Get-DemoInternalToken,Get-DemoTextDigest,Read-DemoProcessRecords,Get-DemoRecordedProcessId,Test-DemoFrontendBinding,Test-DemoCommandOwnership,Start-DemoChild,Wait-DemoReady,Get-DemoHttpJson,Test-DemoServiceHealth,Test-DemoNacosReady,Test-DemoFrontendReady,Get-DemoListener,Get-DemoCommandLine
