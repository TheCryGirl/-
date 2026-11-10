#Requires -Version 5.1
<#
Local demo launcher. This script never changes machine/user environment settings,
never stops a process, never recreates a database and never saves credentials.
#>
param(
    [switch]$CheckOnly,
    [switch]$NoBrowser,
    [switch]$NonInteractive,
    [int]$TimeoutSeconds = 150
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'DemoLauncher.psm1') -Force
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runDir = Join-Path $Root '.run'
$stateFile = Join-Path $runDir 'demo-processes.json'
$frontDir = Join-Path $Root 'src\web'
$demoUrl = 'http://127.0.0.1:5173/products'
$lock = $null
$password = $null
$script:logDir = $null
$failure = $false
$started = New-Object System.Collections.ArrayList
$records = @{}
$services = [ordered]@{ 'user-service'=9001; 'product-service'=9002; 'cart-service'=9003; 'order-service'=9004; 'payment-service'=9005; 'gateway'=9000 }

function Save-DemoState {
    $list = @($records.Values | Sort-Object Name)
    $json = ConvertTo-Json -InputObject $list -Depth 4
    $temp = $stateFile + '.tmp'
    [IO.File]::WriteAllText($temp, $json, (New-Object Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $temp -Destination $stateFile -Force
}

function Test-RecordedDemoOwner([string]$Name, [int]$ProcessId, [string]$Artifact) {
    if (-not $records.ContainsKey($Name)) { return $false }
    return ((Get-DemoRecordedProcessId -Record $records[$Name] -Artifact $Artifact) -eq $ProcessId)
}

function Assert-DemoFrontendTarget([string]$Name, [int]$ProcessId, [string]$Artifact) {
    if ($Name -ne 'frontend') { return }
    $owned = Test-RecordedDemoOwner $Name $ProcessId $Artifact
    $binding = Test-DemoFrontendBinding -Record $records[$Name]
    if (-not $owned -or -not $binding) {
        Write-Host "  Frontend ownership=$owned gatewayBinding=$binding pid=$ProcessId" -ForegroundColor Yellow
        throw '现有前端的网关目标无法确认是本机9000，可能连接另一套数据。请自行关闭该前端后重试；脚本不会结束它。'
    }
}

function Ensure-DemoComponent {
    param([string]$Name, [int]$Port, [string]$Artifact, [string]$Exe,
          [string[]]$ChildArguments, [string]$WorkDir, [hashtable]$ChildEnvironment,
          [scriptblock]$Probe)
    $listener = Get-DemoListener $Port
    $owned = $false
    $ready = $false
    $pendingId = 0
    if ($records.ContainsKey($Name)) { $pendingId = Get-DemoRecordedProcessId -Record $records[$Name] -Artifact $Artifact }
    if ($listener -gt 0) {
        $command = Get-DemoCommandLine $listener
        $owned = (Test-DemoCommandOwnership -CommandLine $command -Artifact $Artifact) -or (Test-RecordedDemoOwner $Name $listener $Artifact)
        Assert-DemoFrontendTarget $Name $listener $Artifact
        if ($owned) { $ready = [bool](& $Probe) }
    } elseif ($pendingId -gt 0) {
        $owned = $true
        Assert-DemoFrontendTarget $Name $pendingId $Artifact
    }
    $action = Get-DemoPortAction -Listening ($listener -gt 0) -Owned $owned -Ready $ready -PendingProcess ($pendingId -gt 0)
    if ($action -eq 'reuse') {
        Write-Host "  [复用] $Name :$Port 已就绪" -ForegroundColor DarkGreen
        return
    }
    if ($action -eq 'start') {
        $logPrefix = Join-Path $script:logDir $Name
        Write-Host "  [启动] $Name :$Port" -ForegroundColor Cyan
        $proc = Start-DemoChild -FilePath $Exe -Arguments $ChildArguments -WorkingDirectory $WorkDir -LogPrefix $logPrefix -Environment $ChildEnvironment
        $listener = $proc.Id
        $record = [pscustomobject]@{
            Name=$Name; ProcessId=$proc.Id; StartedUtc=$proc.StartTime.ToUniversalTime().ToString('o');
            Artifact=$Artifact; LogPrefix=$logPrefix
        }
        if ($Name -eq 'frontend') { $record | Add-Member -NotePropertyName GatewayTarget -NotePropertyValue 'http://127.0.0.1:9000' }
        $records[$Name] = $record
        $null = $started.Add($record)
        Save-DemoState
    } else {
        if ($listener -eq 0) { $listener = $pendingId }
        Write-Host "  [等待] $Name :$Port 已启动，正在就绪" -ForegroundColor Yellow
    }
    Wait-DemoReady -Name $Name -Probe $Probe -TimeoutSeconds $TimeoutSeconds -ProcessId $listener
    Write-Host "  [就绪] $Name :$Port" -ForegroundColor Green
}

try {
    Write-Host ''
    Write-Host '在线商城 · 一键启动演示' -ForegroundColor Green
    Write-Host '仅在本机使用；不会改系统配置、结束其他进程或重置业务数据。' -ForegroundColor DarkGray
    Write-Host ''
    # Discover local prerequisites without changing JAVA_HOME or PATH globally.
    $javaHomes = @($env:DEMO_JAVA_HOME, $env:JAVA_HOME)
    foreach ($base in @('C:\Tools', (Join-Path $env:ProgramFiles 'Eclipse Adoptium'), (Join-Path $env:ProgramFiles 'Java'))) {
        if (Test-Path -LiteralPath $base) {
            $javaHomes += @(Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'jdk[-_]?17|jdk-17|17\.0' } | ForEach-Object { $_.FullName })
        }
    }
    $java = Find-DemoJava17 -Candidates $javaHomes
    $javaHome = Split-Path -Parent (Split-Path -Parent $java)
    Write-Host "  Java: $java" -ForegroundColor DarkGray
    $nacos = Find-DemoNacos -Candidates @($env:DEMO_NACOS_HOME, (Join-Path $env:LOCALAPPDATA 'Programs\nacos'), 'C:\nacos', (Join-Path $Root 'nacos'))
    Write-Host "  Nacos: $($nacos.Home)" -ForegroundColor DarkGray
    $node = Get-Command node.exe -ErrorAction SilentlyContinue
    if (-not $node) { throw '找不到 Node.js。请安装项目所需的 Node.js 后重试。' }
    $nodeVersion = (& $node.Source --version).Trim()
    $major = [int]($nodeVersion.TrimStart('v').Split('.')[0])
    $minor = [int]($nodeVersion.TrimStart('v').Split('.')[1])
    if ($major -lt 22 -or ($major -eq 22 -and $minor -lt 12)) { throw "当前 Node.js $nodeVersion；本项目 Vite 8 演示需要 Node.js 22.12+ 或 24。" }
    $vite = Join-Path $frontDir 'node_modules\vite\bin\vite.js'
    if (-not (Test-Path -LiteralPath $vite)) { throw '前端依赖缺失。请先在 src\web 用 pnpm install --frozen-lockfile 安装；启动器不会自动联网安装。' }
    $jars = @{}
    foreach ($name in $services.Keys) { $jars[$name] = Find-DemoServiceJar -Root $Root -Name $name }
    if ($env:DB_HOST -and $env:DB_HOST -notin @('127.0.0.1','localhost')) { throw '此演示入口只连接本机 MySQL；检测到远程 DB_HOST，请先检查当前终端环境。' }
    if ($env:DB_PORT -and $env:DB_PORT -ne '3306') { throw '此演示入口要求本机 MySQL 3306；请先检查 DB_PORT。' }
    if ((Get-DemoListener 3306) -eq 0) { throw '本机 MySQL :3306 未运行。请先启动已有 MySQL 服务；本脚本不会安装、重置或停止数据库。' }
    Write-Host '  [通过] JDK 17、Nacos、Node、六个 JAR、前端依赖、MySQL 端口已找到。' -ForegroundColor Green
    if ($CheckOnly) {
        Write-Host '  预检查结束，未启动进程、未读取密码、未打开浏览器。' -ForegroundColor Green
        return
    }
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    try {
        $lock = [IO.File]::Open((Join-Path $runDir 'demo-launcher.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch { throw '另一个演示启动器正在运行，请等待那个窗口完成，不要连续双击。' }
    $script:logDir = Join-Path $Root ('logs\demo-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $PID)
    New-Item -ItemType Directory -Path $script:logDir -Force | Out-Null
    $records = Read-DemoProcessRecords -Path $stateFile
    # Preflight all application ports before asking for a credential or starting anything.
    $components = @([pscustomobject]@{Name='nacos';Port=8848;Artifact=$nacos.Jar})
    foreach ($name in $services.Keys) { $components += [pscustomobject]@{Name=$name;Port=$services[$name];Artifact=$jars[$name]} }
    $components += [pscustomobject]@{Name='frontend';Port=5173;Artifact=$vite}
    foreach ($c in $components) {
        $owner = Get-DemoListener $c.Port
        if ($owner -gt 0) {
            $owned = (Test-DemoCommandOwnership -CommandLine (Get-DemoCommandLine $owner) -Artifact $c.Artifact) -or (Test-RecordedDemoOwner $c.Name $owner $c.Artifact)
            if (-not $owned) { throw "端口 $($c.Port) 被其他或无法验证归属的进程 PID=$owner 占用；不会强制结束它。" }
            Assert-DemoFrontendTarget $c.Name $owner $c.Artifact
        }
    }
    # Only ask when at least one DB-backed service will actually be launched.
    $needDb = $false
    foreach ($name in @('user-service','product-service','cart-service','order-service','payment-service')) {
        $pending = 0
        if ($records.ContainsKey($name)) { $pending = Get-DemoRecordedProcessId -Record $records[$name] -Artifact $jars[$name] }
        if ((Get-DemoListener $services[$name]) -eq 0 -and $pending -eq 0) { $needDb = $true }
    }
    if ($needDb) {
        $password = $env:DB_PASSWORD
        if ([string]::IsNullOrEmpty($password)) {
            if ($NonInteractive) { throw '未设置当前进程的 DB_PASSWORD；非交互模式不读取口令。' }
            $userName = if ($env:DB_USER) { $env:DB_USER } else { 'root' }
            Write-Host "请输入本机 MySQL 用户 $userName 的密码（隐藏输入，仅本次启动使用）。" -ForegroundColor Yellow
            $secure = Read-Host 'MySQL 密码' -AsSecureString
            $password = (New-Object System.Net.NetworkCredential('', $secure)).Password
            $secure.Dispose()
            if ([string]::IsNullOrEmpty($password)) { throw '未提供数据库密码，未启动应用服务。' }
        }
    }
    # Reuse the existing local-dev key so independently launched services remain compatible.
    # This value is not a production credential. It is already used by dev.ps1.
    $jwt = $env:JWT_SECRET
    if (-not $jwt) { $jwt = 'dev-only-secret-please-change-in-production-0123456789' }
    if ($jwt.Length -lt 32) { throw 'JWT_SECRET 长度不足32字符，未启动服务。' }
    $common = @{
        JAVA_HOME=$javaHome; DB_HOST='127.0.0.1'; DB_PORT='3306'; JWT_SECRET=$jwt;
        NACOS_ADDR='127.0.0.1:8848'; NACOS_ENABLED='true';
        ADMIN_BOOTSTRAP_ACCOUNT=$null; ADMIN_BOOTSTRAP_PASSWORD=$null;
        USER_SERVICE_URL='http://127.0.0.1:9001'; PRODUCT_SERVICE_URL='http://127.0.0.1:9002';
        CART_SERVICE_URL='http://127.0.0.1:9003'; ORDER_SERVICE_URL='http://127.0.0.1:9004';
        PAYMENT_SERVICE_URL='http://127.0.0.1:9005'
    }
    Ensure-DemoComponent -Name 'nacos' -Port 8848 -Artifact $nacos.Jar -Exe $java `
        -ChildArguments @('-Xms256m','-Xmx512m','-Dnacos.standalone=true',('-Dnacos.home=' + $nacos.Home),'-jar',$nacos.Jar) `
        -WorkDir $nacos.Home -ChildEnvironment @{ JAVA_HOME=$javaHome; MODE='standalone'; ADMIN_BOOTSTRAP_ACCOUNT=$null; ADMIN_BOOTSTRAP_PASSWORD=$null } `
        -Probe { Test-DemoNacosReady }
    foreach ($name in $services.Keys) {
        $port = $services[$name]
        $envForChild = @{} + $common
        if ($name -ne 'gateway') { $envForChild['DB_PASSWORD'] = $password }
        # Preserve established upload paths by using the repository root as WorkingDirectory.
        Ensure-DemoComponent -Name $name -Port $port -Artifact $jars[$name] -Exe $java `
            -ChildArguments @('-Xms64m','-Xmx256m','-jar',$jars[$name]) -WorkDir $Root `
            -ChildEnvironment $envForChild -Probe { Test-DemoServiceHealth $port }
        $envForChild.Clear()
    }
    $password = $null
    Wait-DemoReady -Name 'gateway product route' -TimeoutSeconds $TimeoutSeconds -Probe {
        $j=Get-DemoHttpJson 'http://127.0.0.1:9000/api/products?page=1&size=1'
        return ($null -ne $j -and $j.code -eq 0)
    }
    Ensure-DemoComponent -Name 'frontend' -Port 5173 -Artifact $vite -Exe $node.Source `
        -ChildArguments @($vite,'--host','127.0.0.1','--port','5173','--strictPort') -WorkDir $frontDir `
        -ChildEnvironment @{ VITE_GATEWAY_TARGET='http://127.0.0.1:9000'; BROWSER='none'; ADMIN_BOOTSTRAP_ACCOUNT=$null; ADMIN_BOOTSTRAP_PASSWORD=$null } `
        -Probe { Test-DemoFrontendReady -Marker '' }
    $registry=Get-DemoHttpJson 'http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=50'
    $missing=@($services.Keys | Where-Object { $_ -notin @($registry.doms) })
    if ($missing.Count -gt 0) {
        Wait-DemoReady -Name 'Nacos six service registrations' -TimeoutSeconds $TimeoutSeconds -Probe {
            $registered = Get-DemoHttpJson 'http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=50'
            return ($null -ne $registered -and @($services.Keys | Where-Object { $_ -notin @($registered.doms) }).Count -eq 0)
        }
    }
    Write-Host ''
    Write-Host '  [成功] 前后端、网关业务路由、前端代理、Nacos服务注册均已就绪。' -ForegroundColor Green
    Write-Host "  演示页面：$demoUrl" -ForegroundColor Cyan
    Write-Host "  本次日志：$script:logDir" -ForegroundColor DarkGray
    Write-Host '  关闭此启动窗口不会停止后台服务；下次双击会复用本次正常进程。' -ForegroundColor DarkGray
    if (-not $NoBrowser) {
        # Shell-open the known local URL in the user's default browser only after readiness.
        Start-Process -FilePath $demoUrl
    }
} catch {
    $failure = $true
    Write-Host ''
    Write-Host ('[启动未完成] ' + $_.Exception.Message) -ForegroundColor Red
    if ($script:logDir) { Write-Host ('日志位置：' + $script:logDir) -ForegroundColor Yellow }
    Write-Host '未结束任何既有程序；已启动的组件保留，修正后重新双击即可继续。' -ForegroundColor Yellow
} finally {
    $password = $null
    if ($lock) { $lock.Dispose() }
}
if ($failure) { exit 1 }
