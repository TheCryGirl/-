#Requires -Version 5.1
# Behavioral tests. No application services or database are changed.
$ErrorActionPreference = 'Stop'
$module = Join-Path (Split-Path -Parent $PSScriptRoot) 'DemoLauncher.psm1'
if (-not (Test-Path -LiteralPath $module)) { throw 'RED: demo launcher module is not implemented yet.' }
Import-Module $module -Force
$repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$temp = Join-Path $repo ('.run\demo-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp -Force | Out-Null
$passed = 0
function Assert-Equal($Actual, $Expected, [string]$Name) {
    if ($Actual -ne $Expected) { throw "$Name expected [$Expected], got [$Actual]" }
}
function Assert-Throws([scriptblock]$Code, [string]$Pattern) {
    $caught = $false
    try { & $Code | Out-Null } catch { if ($_.Exception.Message -notmatch $Pattern) { throw }; $caught = $true }
    if (-not $caught) { throw "Expected error matching $Pattern" }
}
function Test-Case([string]$Name, [scriptblock]$Code) {
    & $Code
    $script:passed++
    Write-Host "PASS $Name"
}
try {
    Test-Case 'Double-click entry script parses in Windows PowerShell 5.1' {
        $entry = Join-Path (Split-Path -Parent $PSScriptRoot) 'demo.ps1'
        $tokens = $null
        $parseErrors = $null
        [Management.Automation.Language.Parser]::ParseFile($entry, [ref]$tokens, [ref]$parseErrors) | Out-Null
        if (@($parseErrors).Count -gt 0) { throw ('Entry script parse errors: ' + (($parseErrors | ForEach-Object { $_.Message }) -join '; ')) }
    }
    Test-Case 'JDK17 is selected instead of an earlier JDK24 candidate' {
        foreach ($version in @(24,17)) {
            $d = Join-Path $temp "jdk-$version"
            New-Item -ItemType Directory -Path (Join-Path $d 'bin') -Force | Out-Null
            Set-Content -LiteralPath (Join-Path $d 'release') -Value ('JAVA_VERSION="' + $version + '.0.1"')
            Set-Content -LiteralPath (Join-Path $d 'bin\java.exe') -Value ''
        }
        $j = Find-DemoJava17 -Candidates @((Join-Path $temp 'jdk-24'), (Join-Path $temp 'jdk-17'))
        Assert-Equal $j (Join-Path $temp 'jdk-17\bin\java.exe') 'Java selection'
    }
    Test-Case 'Missing JDK17 fails before starting children' {
        Assert-Throws { Find-DemoJava17 -Candidates @((Join-Path $temp 'jdk-24')) } 'JDK 17'
    }
    Test-Case 'Nacos jar-only installation is found without startup.cmd' {
        $n = Join-Path $temp 'nacos with spaces'
        New-Item -ItemType Directory -Path (Join-Path $n 'target') -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $n 'target\nacos-server.jar') -Value ''
        $found = Find-DemoNacos -Candidates @($n)
        Assert-Equal $found.Home $n 'Nacos home'
        Assert-Equal $found.Jar (Join-Path $n 'target\nacos-server.jar') 'Nacos jar'
    }
    Test-Case 'Missing Nacos fails explicitly instead of silently skipping it' {
        Assert-Throws { Find-DemoNacos -Candidates @((Join-Path $temp 'not-installed')) } 'Nacos'
    }
    Test-Case 'Executable jar lookup excludes original sources and javadoc files' {
        $r = Join-Path $temp 'repo'
        $target = Join-Path $r 'src\gateway\target'
        New-Item -ItemType Directory -Path $target -Force | Out-Null
        foreach ($n in @('gateway-1.jar','original-gateway-1.jar','gateway-1-sources.jar','gateway-1-javadoc.jar')) {
            Set-Content -LiteralPath (Join-Path $target $n) -Value ''
        }
        Assert-Equal (Find-DemoServiceJar -Root $r -Name 'gateway') (Join-Path $target 'gateway-1.jar') 'Jar lookup'
    }
    Test-Case 'Missing service jar gives a build prerequisite error' {
        Assert-Throws { Find-DemoServiceJar -Root $temp -Name 'order-service' } 'order-service'
    }
    Test-Case 'Unknown port owner is refused and never killed' {
        Assert-Throws { Get-DemoPortAction -Listening $true -Owned $false -Ready $true } 'occupied'
    }
    Test-Case 'Existing ready owned service is reused' {
        Assert-Equal (Get-DemoPortAction -Listening $true -Owned $true -Ready $true) 'reuse' 'Port action'
    }
    Test-Case 'Owned service still booting is waited for instead of duplicated' {
        Assert-Equal (Get-DemoPortAction -Listening $true -Owned $true -Ready $false) 'wait' 'Port action'
        Assert-Equal (Get-DemoPortAction -Listening $false -Owned $false -Ready $false) 'start' 'Port action'
    }
    Test-Case 'Command ownership requires exact artifact path, not a prefix match' {
        Assert-Equal (Test-DemoCommandOwnership -CommandLine 'java -jar "C:\demo folder\service.jar"' -Artifact 'C:\demo folder\service.jar') $true 'Owned command'
        Assert-Equal (Test-DemoCommandOwnership -CommandLine 'java -jar "C:\demo folder\service.jar.old"' -Artifact 'C:\demo folder\service.jar') $false 'Prefix is not ownership'
    }
    Test-Case 'Readiness polling returns after probe succeeds and times out when false' {
        $state = @{ Count = 0 }
        Wait-DemoReady -Name 'fixture' -Probe { $state.Count++; return $state.Count -eq 3 } -TimeoutSeconds 2 -PollMilliseconds 1
        Assert-Equal $state.Count 3 'Probe calls'
        Assert-Throws { Wait-DemoReady -Name 'fixture' -Probe { $false } -TimeoutSeconds 1 -PollMilliseconds 30 } 'fixture'
    }
    Test-Case 'Windows argument quoting survives spaces Chinese quotes and trailing slash' {
        $child = Join-Path $temp 'argument child.ps1'
        Set-Content -LiteralPath $child -Encoding UTF8 -Value 'param([string]$One,[string]$Two,[string]$Three) ; [Console]::OutputEncoding = New-Object Text.UTF8Encoding($false); @($One,$Two,$Three) | ConvertTo-Json -Compress'
        $exe = Join-Path $PSHOME 'powershell.exe'
        if (-not (Test-Path $exe)) { $exe = Join-Path $PSHOME 'pwsh.exe' }
        $values = @('C:\demo folder\中文\', 'a"b', '')
        $p = Start-DemoChild -FilePath $exe -Arguments (@('-NoProfile','-File',$child) + $values) -WorkingDirectory $temp -LogPrefix (Join-Path $temp 'args') -Environment @{}
        $p.WaitForExit()
        Assert-Equal $p.ExitCode 0 'Child exit'
        $got = Get-Content -LiteralPath (Join-Path $temp 'args.out.log') -Raw -Encoding UTF8 | ConvertFrom-Json
        for ($i=0; $i -lt 3; $i++) { Assert-Equal $got[$i] $values[$i] "Argument $i" }
    }
    Test-Case 'Credential environment is child-only and parent value is restored' {
        $exe = Join-Path $PSHOME 'powershell.exe'
        if (-not (Test-Path $exe)) { $exe = Join-Path $PSHOME 'pwsh.exe' }
        $env:DEMO_TEST_SECRET = 'parent-value'
        $secret = [guid]::NewGuid().ToString('N')
        $p = Start-DemoChild -FilePath $exe -Arguments @('-NoProfile','-Command','if ($env:DEMO_TEST_SECRET -eq "parent-value") { exit 8 }; if (-not $env:DEMO_TEST_SECRET) { exit 9 }; Write-Output "child environment present"') -WorkingDirectory $temp -LogPrefix (Join-Path $temp 'env') -Environment @{ DEMO_TEST_SECRET = $secret }
        $p.WaitForExit()
        Assert-Equal $p.ExitCode 0 'Child inherited temporary env'
        Assert-Equal $env:DEMO_TEST_SECRET 'parent-value' 'Parent env restored'
        $log = Get-Content -LiteralPath (Join-Path $temp 'env.out.log') -Raw
        Assert-Equal ($log.Contains($secret)) $false 'No secret in log'
        Assert-Equal ($p.StartInfo.Arguments.Contains($secret)) $false 'No secret in arguments'
        Remove-Item Env:\DEMO_TEST_SECRET
    }
    Test-Case 'Binary JSON health response is decoded instead of becoming a byte-number array' {
        $serverFile = Join-Path $temp 'health-fixture.js'
        $portFile = Join-Path $temp 'health-port.txt'
        Set-Content -LiteralPath $serverFile -Encoding UTF8 -Value 'const http=require("http"),fs=require("fs");const s=http.createServer((q,r)=>{r.writeHead(200,{"Content-Type":"application/octet-stream"});r.end(JSON.stringify(q.url==="/actuator/health"?{status:"UP"}:{}))});s.listen(0,"127.0.0.1",()=>fs.writeFileSync(process.argv[2],String(s.address().port)));'
        $nodeExe = (Get-Command node.exe -ErrorAction Stop).Source
        $fixture = Start-DemoChild -FilePath $nodeExe -Arguments @($serverFile,$portFile) -WorkingDirectory $temp -LogPrefix (Join-Path $temp 'health-fixture') -Environment @{}
        try {
            Wait-DemoReady -Name 'local HTTP fixture' -Probe { Test-Path -LiteralPath $portFile } -TimeoutSeconds 10 -PollMilliseconds 50 -ProcessId $fixture.Id
            $fixturePort = [int](Get-Content -LiteralPath $portFile -Raw)
            $json = Get-DemoHttpJson "http://127.0.0.1:$fixturePort/actuator/health"
            Assert-Equal $json.status 'UP' 'Decoded JSON status'
            Assert-Equal (Test-DemoServiceHealth $fixturePort) $true 'Service readiness'
            $empty = Get-DemoHttpJson "http://127.0.0.1:$fixturePort/empty"
            Assert-Equal ($null -ne $empty.PSObject.Properties['status']) $false 'Missing status fixture'
        } finally {
            if (-not $fixture.HasExited) { $fixture.Kill(); $fixture.WaitForExit() }
        }
    }
    Test-Case 'A recorded process still booting before binding its port is waited for' {
        Assert-Equal (Get-DemoPortAction -Listening $false -Owned $true -Ready $false -PendingProcess $true) 'wait' 'Pending process must not duplicate'
        $self = Get-Process -Id $PID
        $record = [pscustomobject]@{ ProcessId=$PID; StartedUtc=$self.StartTime.ToUniversalTime().ToString('o'); Artifact='C:\fixture\boot.jar' }
        Assert-Equal (Get-DemoRecordedProcessId -Record $record -Artifact 'C:\fixture\boot.jar') $PID 'Live record'
        Assert-Equal (Get-DemoRecordedProcessId -Record $record -Artifact 'C:\other\boot.jar') 0 'Wrong artifact rejected'
        $record.StartedUtc = $self.StartTime.AddMinutes(-1).ToUniversalTime().ToString('o')
        Assert-Equal (Get-DemoRecordedProcessId -Record $record -Artifact 'C:\fixture\boot.jar') 0 'Reused PID rejected'
    }
    Test-Case 'Existing frontend must have a verified local gateway binding' {
        $record = [pscustomobject]@{ GatewayTarget='http://127.0.0.1:9000' }
        Assert-Equal (Test-DemoFrontendBinding -Record $record) $true 'Local target'
        $record.GatewayTarget = 'http://127.0.0.1:19000'
        Assert-Equal (Test-DemoFrontendBinding -Record $record) $false 'Docker target rejected'
        Assert-Equal (Test-DemoFrontendBinding -Record ([pscustomobject]@{Name='frontend'})) $false 'Unknown target rejected'
    }
    Test-Case 'PowerShell5 JSON state restores separate named records rather than one array key' {
        $statePath = Join-Path $temp 'state.json'
        $self = Get-Process -Id $PID
        $entries = @(
            [pscustomobject]@{Name='nacos';ProcessId=$PID;StartedUtc=$self.StartTime.ToUniversalTime().ToString('o');Artifact='C:\fixture\nacos.jar'},
            [pscustomobject]@{Name='frontend';ProcessId=$PID;StartedUtc=$self.StartTime.ToUniversalTime().ToString('o');Artifact='C:\fixture\vite.js';GatewayTarget='http://127.0.0.1:9000'}
        )
        ConvertTo-Json -InputObject $entries | Set-Content -LiteralPath $statePath -Encoding UTF8
        $got = Read-DemoProcessRecords -Path $statePath
        Assert-Equal $got.Count 2 'State record count'
        Assert-Equal $got['frontend'].Name 'frontend' 'Frontend key restored'
        Assert-Equal (Test-DemoFrontendBinding -Record $got['frontend']) $true 'Frontend target restored'
        Assert-Equal (Get-DemoRecordedProcessId -Record $got['nacos'] -Artifact 'C:\fixture\nacos.jar') $PID 'Nacos identity restored'
    }
    Test-Case 'Internal cluster token persists only as user-encrypted XML and is stable across launches' {
        $tokenDir = Join-Path $temp 'token-state'
        New-Item -ItemType Directory -Path $tokenDir -Force | Out-Null
        $one = Get-DemoInternalToken -RunDirectory $tokenDir
        $two = Get-DemoInternalToken -RunDirectory $tokenDir
        Assert-Equal $one $two 'Stable local cluster token'
        Assert-Equal ($one.Length -ge 32) $true 'Random token length'
        $stored = Get-Content -LiteralPath (Join-Path $tokenDir 'demo-internal-token.xml') -Raw
        Assert-Equal ($stored.Contains($one)) $false 'No plaintext token on disk'
        Assert-Throws { Get-DemoInternalToken -RunDirectory $tokenDir -ExplicitToken 'short' } '32'
    }
    Test-Case 'Launcher rejects old jars and accepts the secured artifact marker' {
        Add-Type -AssemblyName System.IO.Compression
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $jar = Join-Path $temp 'fixture.jar'
        $zip = [IO.Compression.ZipFile]::Open($jar, [IO.Compression.ZipArchiveMode]::Create)
        $zip.Dispose()
        Assert-Throws { Assert-DemoSecureJar -Path $jar } 'Old backend'
        $zip = [IO.Compression.ZipFile]::Open($jar, [IO.Compression.ZipArchiveMode]::Update)
        try {
            $entry = $zip.CreateEntry('BOOT-INF/classes/security-boundary.version')
            $w = New-Object IO.StreamWriter($entry.Open())
            try { $w.Write('1') } finally { $w.Dispose() }
        } finally { $zip.Dispose() }
        Assert-DemoSecureJar -Path $jar
    }
    Write-Host "RESULT $passed tests passed"
} finally {
    if (Test-Path $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
