$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$cases = @(
    @{ Name = 'product inventory reserve (100/500 requests)'; Module = 'src/product-service'; Test = 'Issue48InventoryConcurrencyTest' },
    @{ Name = 'order cancellation race and idempotency'; Module = 'src/order-service'; Test = 'Issue48OrderConcurrencyTest' }
)
$results = @()

foreach ($case in $cases) {
    $started = Get-Date
    $logPath = Join-Path $env:TEMP ("issue48-{0}.log" -f ($case.Test))
    Push-Location (Join-Path $repoRoot $case.Module)
    try {
        $mavenCommand = "mvn -q -Dtest=$($case.Test) test > `"$logPath`" 2>&1"
        & cmd.exe /d /s /c $mavenCommand
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
    $finished = Get-Date
    $status = if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }
    $results += [pscustomobject]@{
        Case = $case.Name
        Started = $started.ToString('yyyy-MM-dd HH:mm:ss zzz')
        Duration = [math]::Round(($finished - $started).TotalSeconds, 2)
        ExitCode = $exitCode
        Result = $status
    }
    Write-Host ("[{0}] {1} ({2}s, exit {3})" -f $status, $case.Name, [math]::Round(($finished - $started).TotalSeconds, 2), $exitCode)
    if ($exitCode -ne 0) {
        Write-Host "Maven output: $logPath"
    }
}

Write-Host ''
$results | Format-Table -AutoSize
if (($results | Where-Object Result -eq 'FAIL').Count -gt 0) {
    exit 1
}
