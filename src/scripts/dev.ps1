#Requires -Version 5.1
<#
.SYNOPSIS
    在线商城项目 - 本地服务一键启停

.DESCRIPTION
    管理 Nacos 与各微服务的启动、停止、状态查看。
    进程用 Start-Process 脱离当前终端启动，关掉 DSH 或终端不会影响它们。

.PARAMETER Action
    start    启动全部（Nacos + 已打包的服务）
    stop     停止全部
    restart  重启全部
    status   查看当前状态
    logs     查看日志目录

.PARAMETER Only
    只操作指定的组件，逗号分隔。可选：nacos,gateway,user-service,product-service,cart-service,order-service,payment-service
    默认全部。

.EXAMPLE
    .\dev.ps1 start
    启动全部

.EXAMPLE
    .\dev.ps1 start -Only nacos
    只启动 Nacos

.EXAMPLE
    .\dev.ps1 status
    查看谁在跑

.EXAMPLE
    .\dev.ps1 stop
    全部停止
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'restart', 'status', 'logs')]
    [string]$Action = 'status',

    # 第二个位置参数：逗号分隔的组件名，如 "nacos" 或 "nacos,gateway"
    [Parameter(Position = 1)]
    [string]$Targets = '',

    # 也可以用 -Only 显式指定数组
    [string[]]$Only = @()
)

$ErrorActionPreference = 'Stop'

# ---------------- 配置 ----------------

# 项目根目录（脚本放在 src/scripts 下）
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path (Join-Path $Root 'src'))) {
    # 兼容脚本被放在项目根的情况
    $Root = Split-Path -Parent $PSScriptRoot
}

$LogDir = Join-Path $Root 'logs'
$RunDir = Join-Path $Root '.run'   # 存放 pid 文件
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
New-Item -ItemType Directory -Path $RunDir -Force | Out-Null

# 组件定义：名称、端口、启动方式
$Components = [ordered]@{
    'nacos'           = @{ Port = 8848; Kind = 'nacos' }
    'gateway'         = @{ Port = 9000; Kind = 'jar' }
    'user-service'    = @{ Port = 9001; Kind = 'jar' }
    'product-service' = @{ Port = 9002; Kind = 'jar' }
    'cart-service'    = @{ Port = 9003; Kind = 'jar' }
    'order-service'   = @{ Port = 9004; Kind = 'jar' }
    'payment-service' = @{ Port = 9005; Kind = 'jar' }
}

# ---------------- 工具函数 ----------------

function Get-JavaExe {
    # 优先用 JAVA_HOME，其次 PATH
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return "$env:JAVA_HOME\bin\java.exe"
    }
    $j = Get-Command java -ErrorAction SilentlyContinue
    if ($j) { return $j.Source }
    throw '找不到 java，请先安装 JDK 17+ 并设置 JAVA_HOME'
}

function Get-NacosHome {
    $candidates = @(
        "$env:LOCALAPPDATA\Programs\nacos",
        "C:\nacos",
        "$Root\nacos"
    )
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c 'bin\startup.cmd')) { return $c }
    }
    return $null
}

function Find-Jar([string]$Name) {
    $dir = Join-Path $Root "src\$Name\target"
    if (-not (Test-Path $dir)) { return $null }
    # 排除 sources / javadoc / original 包
    $jar = Get-ChildItem $dir -Filter '*.jar' -File |
        Where-Object { $_.Name -notmatch 'sources|javadoc|original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($jar) { return $jar.FullName }
    return $null
}

function Test-Port([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($conn) { return $conn.OwningProcess | Select-Object -First 1 }
    return $null
}

function Get-PidFile([string]$Name) { Join-Path $RunDir "$Name.pid" }

function Save-Pid([string]$Name, [int]$ProcessId) {
    $ProcessId | Out-File (Get-PidFile $Name) -Encoding ascii -NoNewline
}

function Read-Pid([string]$Name) {
    $f = Get-PidFile $Name
    if (Test-Path $f) {
        $v = Get-Content $f -ErrorAction SilentlyContinue
        if ($v -and $v.Trim() -match '^\d+$') { return [int]$v.Trim() }
    }
    return 0
}

function Clear-Pid([string]$Name) {
    $f = Get-PidFile $Name
    if (Test-Path $f) { Remove-Item $f -Force -ErrorAction SilentlyContinue }
}

function Get-RunningPid([string]$Name) {
    # 先看端口（最可靠），再看 pid 文件
    $port = $Components[$Name].Port
    $p = Test-Port $port
    if ($p) { return [int]$p }
    $saved = Read-Pid $Name
    if ($saved -gt 0) {
        $proc = Get-Process -Id $saved -ErrorAction SilentlyContinue
        if ($proc) { return $saved }
        Clear-Pid $Name
    }
    return 0
}

function Start-Nacos {
    $nacosHome = Get-NacosHome
    if (-not $nacosHome) {
        Write-Host '  [跳过] 找不到 Nacos 安装目录' -ForegroundColor Yellow
        Write-Host '         预期位置: %LOCALAPPDATA%\Programs\nacos' -ForegroundColor DarkGray
        Write-Host '         下载: https://github.com/alibaba/nacos/releases/download/2.3.2/nacos-server-2.3.2.zip' -ForegroundColor DarkGray
        return $false
    }

    $log = Join-Path $LogDir 'nacos.log'
    Write-Host "  启动 Nacos ($nacosHome)" -ForegroundColor Cyan

    # Nacos 自带的 startup.cmd 会 fork 出自己的 java 进程，
    # 这里用 Start-Process 启动它并让新窗口最小化，关掉终端不影响。
    $env:MODE = 'standalone'
    $startup = Join-Path $nacosHome 'bin\startup.cmd'
    # cmd /c 后面带重定向时，整条命令需要再包一层双引号，
    # 否则 cmd 只解析到第一个引号就结束，重定向不生效。
    $cmdLine = "`"`"$startup`" -m standalone > `"$log`" 2>&1`""
    Start-Process -FilePath 'cmd.exe' `
        -ArgumentList '/c', $cmdLine `
        -WorkingDirectory (Join-Path $nacosHome 'bin') `
        -WindowStyle Minimized
    return $true
}

function Start-ServiceJar([string]$Name) {
    $jar = Find-Jar $Name
    if (-not $jar) {
        Write-Host "  [跳过] $Name 没有打包好的 jar" -ForegroundColor Yellow
        Write-Host "         先执行: cd src/$Name; mvn package -DskipTests" -ForegroundColor DarkGray
        return $false
    }

    $java = Get-JavaExe
    $log = Join-Path $LogDir "$Name.log"
    $errLog = Join-Path $LogDir "$Name.err.log"

    Write-Host "  启动 $Name  ($(Split-Path -Leaf $jar))" -ForegroundColor Cyan

    # Windows 环境变量不区分大小写，但 .NET 的 ProcessStartInfo 会区分。
    # 同时存在 NO_PROXY 与 no_proxy 时 Start-Process 会报"键已存在"，
    # 这里临时清掉代理相关变量再启动子进程。
    $proxyVars = @('NO_PROXY', 'no_proxy', 'HTTP_PROXY', 'http_proxy',
                   'HTTPS_PROXY', 'https_proxy', 'ALL_PROXY', 'all_proxy')
    $saved = @{}
    foreach ($v in $proxyVars) {
        $saved[$v] = [Environment]::GetEnvironmentVariable($v, 'Process')
        [Environment]::SetEnvironmentVariable($v, $null, 'Process')
    }
    try {
        $proc = Start-Process -FilePath $java `
            -ArgumentList '-jar', "`"$jar`"" `
            -RedirectStandardOutput $log `
            -RedirectStandardError $errLog `
            -WindowStyle Hidden `
            -PassThru
        Save-Pid $Name $proc.Id
    }
    finally {
        foreach ($v in $proxyVars) {
            if ($saved[$v]) {
                [Environment]::SetEnvironmentVariable($v, $saved[$v], 'Process')
            }
        }
    }
    return $true
}

function Wait-Healthy([string]$Name, [int]$TimeoutSec = 120) {
    $port = $Components[$Name].Port
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port $port) { return $true }
        Start-Sleep -Milliseconds 800
    }
    return $false
}

function Stop-Component([string]$Name) {
    $procId = Get-RunningPid $Name
    if ($procId -le 0) {
        Write-Host "  $Name 未运行" -ForegroundColor DarkGray
        Clear-Pid $Name
        return
    }

    Write-Host "  停止 $Name (PID $procId)" -ForegroundColor Yellow
    try {
        # 先尝试正常结束，给进程机会释放端口
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($proc) {
            if ($proc.MainWindowHandle -eq 0) {
                # 无窗口的进程，用 Stop-Process
                Stop-Process -Id $procId -Force -ErrorAction Stop
            }
            else {
                Stop-Process -Id $procId -Force -ErrorAction Stop
            }
        }
    }
    catch {
        Write-Host "    结束进程失败: $($_.Exception.Message)" -ForegroundColor Red
    }
    Clear-Pid $Name

    # 等端口释放
    $waited = 0
    while ((Test-Port $Components[$Name].Port) -and $waited -lt 15) {
        Start-Sleep -Milliseconds 500
        $waited++
    }
    if (Test-Port $Components[$Name].Port) {
        Write-Host "    端口 $($Components[$Name].Port) 仍被占用" -ForegroundColor Red
    }
}

function Resolve-Targets {
    # 合并两种传参：位置参数 $Targets（逗号分隔）与 -Only（数组）
    $list = @()
    if ($Only -and $Only.Count -gt 0) { $list += $Only }
    if ($Targets -and $Targets.Trim()) {
        $list += ($Targets -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    }

    if ($list.Count -gt 0) {
        foreach ($o in $list) {
            if (-not $Components.Contains($o)) {
                Write-Host "未知组件: $o" -ForegroundColor Red
                Write-Host "可选: $($Components.Keys -join ', ')" -ForegroundColor DarkGray
                exit 1
            }
        }
        return @($list)
    }
    return @($Components.Keys)
}

# ---------------- 动作 ----------------

function Do-Start {
    $targets = @(Resolve-Targets)
    Write-Host "`n启动在线商城本地服务`n" -ForegroundColor Green

    # Nacos 必须最先启动（其他服务要注册到它）
    $ordered = @('nacos') + ($targets | Where-Object { $_ -ne 'nacos' })

    foreach ($name in $ordered) {
        if ($targets -notcontains $name -and $name -eq 'nacos') {
            # 用户没要求启动 nacos，但启动其他服务时需要它
            if (-not (Get-RunningPid 'nacos')) {
                Write-Host '  注意: Nacos 未运行，其他服务将无法注册' -ForegroundColor Yellow
            }
            continue
        }

        if (Get-RunningPid $name) {
            Write-Host "  $name 已在运行" -ForegroundColor DarkGray
            continue
        }

        if ($name -eq 'nacos') {
            if (-not (Start-Nacos)) { continue }
            if (Wait-Healthy 'nacos' 90) {
                Write-Host "    Nacos 就绪 (http://127.0.0.1:8848/nacos)" -ForegroundColor Green
            }
            else {
                Write-Host '    Nacos 启动超时，看日志: logs\nacos.log' -ForegroundColor Red
            }
        }
        else {
            if (-not (Start-ServiceJar $name)) { continue }
            # 只对网关等待，其他服务可能还在开发中
            if ($name -eq 'gateway') {
                if (Wait-Healthy $name 90) {
                    Write-Host "    网关就绪 (http://localhost:9000)" -ForegroundColor Green
                }
                else {
                    Write-Host "    网关启动超时，看日志: logs\$name.log" -ForegroundColor Red
                }
            }
        }
    }

    Write-Host "`n日志目录: $LogDir" -ForegroundColor DarkGray
    Write-Host "查看状态: .\dev.ps1 status`n" -ForegroundColor DarkGray
}

function Do-Stop {
    $targets = @(Resolve-Targets)
    Write-Host "`n停止在线商城本地服务`n" -ForegroundColor Green

    # 反向停止：先停业务服务，最后停 Nacos
    $ordered = @($targets | Where-Object { $_ -ne 'nacos' }) + @('nacos')
    foreach ($name in $ordered) {
        Stop-Component $name
    }
    Write-Host "`n完成`n" -ForegroundColor DarkGray
}

function Do-Status {
    Write-Host "`n在线商城本地服务状态`n" -ForegroundColor Green
    Write-Host ("  {0,-18} {1,-8} {2,-10} {3}" -f '组件', '端口', '状态', 'PID') -ForegroundColor DarkGray
    Write-Host ('  ' + ('-' * 52)) -ForegroundColor DarkGray

    foreach ($name in $Components.Keys) {
        $port = $Components[$name].Port
        $procId = Get-RunningPid $name
        $state = if ($procId -gt 0) { '运行中' } else { '未运行' }
        $color = if ($procId -gt 0) { 'Green' } else { 'DarkGray' }
        Write-Host ("  {0,-18} {1,-8} {2,-10} {3}" -f $name, $port, $state, $procId) -ForegroundColor $color
    }

    # 额外信息
    Write-Host ''
    if (Test-Port 8848) {
        Write-Host '  Nacos 控制台: http://127.0.0.1:8848/nacos' -ForegroundColor Cyan
        try {
            $svc = Invoke-RestMethod 'http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=50' -TimeoutSec 5
            if ($svc.doms) {
                Write-Host "  已注册服务: $($svc.doms -join ', ')" -ForegroundColor Cyan
            }
            else {
                Write-Host '  已注册服务: (无)' -ForegroundColor DarkGray
            }
        }
        catch { }
    }
    if (Test-Port 9000) {
        Write-Host '  网关健康检查: http://localhost:9000/actuator/health' -ForegroundColor Cyan
    }
    Write-Host ''
}

function Do-Logs {
    Write-Host "`n日志目录: $LogDir`n" -ForegroundColor Green
    if (-not (Test-Path $LogDir)) { Write-Host '  暂无日志'; return }
    $files = Get-ChildItem $LogDir -Filter '*.log' | Sort-Object Name
    if (-not $files) { Write-Host '  暂无日志'; return }
    $files | Select-Object Name, @{n = 'KB'; e = { [math]::Round($_.Length / 1KB, 1) } }, LastWriteTime |
        Format-Table -AutoSize
    Write-Host '  实时查看某个服务: Get-Content logs\gateway.log -Wait -Tail 50' -ForegroundColor DarkGray
    Write-Host ''
}

# ---------------- 入口 ----------------

switch ($Action) {
    'start' { Do-Start }
    'stop' { Do-Stop }
    'restart' { Do-Stop; Start-Sleep -Seconds 2; Do-Start }
    'status' { Do-Status }
    'logs' { Do-Logs }
}
