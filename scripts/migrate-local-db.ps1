#Requires -Version 5.1
param(
    [ValidateSet('127.0.0.1', 'localhost')] [string]$DbHost = '127.0.0.1',
    [ValidateRange(1, 65535)] [int]$Port = 3306,
    [string]$User = 'root'
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path -Parent $PSScriptRoot
$mysql = (Get-Command mysql.exe -CommandType Application -ErrorAction SilentlyContinue).Source
if (-not $mysql) { throw '未找到 mysql.exe，请确认 MySQL 客户端已安装。' }

function Invoke-Mysql([string]$Database, [string]$Command) {
    # Windows PowerShell 5.1 将原生 stderr 包装成错误记录；退出码才是 SQL 成败依据。
    $ErrorActionPreference = 'Continue'
    $result = & $mysql --no-defaults --no-login-paths --host=$DbHost --port=$Port --protocol=TCP `
                       --user=$User --database=$Database --default-character-set=utf8mb4 `
                       --batch --skip-column-names --execute $Command 2>&1
    $code = $LASTEXITCODE
    if ($code -ne 0) { throw "MySQL 退出码 $code：$($result -join ' ')" }
}

# 依赖顺序：后面的脚本可能引用前面新增的列或表
$migrations = @(
    @{ Db = 'mall_user';    File = 'src/user-service/src/main/resources/migration-auth-version.sql' },
    @{ Db = 'mall_user';    File = 'src/user-service/src/main/resources/migration-136-freight.sql' },
    @{ Db = 'mall_user';    File = 'src/user-service/src/main/resources/migration-144-shop-apply.sql' },

    @{ Db = 'mall_product'; File = 'src/product-service/src/main/resources/migration-47-low-stock.sql' },
    @{ Db = 'mall_product'; File = 'src/product-service/src/main/resources/migration-140-flash-sale.sql' },
    @{ Db = 'mall_product'; File = 'src/product-service/src/main/resources/migration-145-flash-checkout.sql' },

    @{ Db = 'mall_order';   File = 'src/order-service/src/main/resources/migration-135-main-order.sql' },
    @{ Db = 'mall_order';   File = 'src/order-service/src/main/resources/migration-136-freight-discount.sql' },
    @{ Db = 'mall_order';   File = 'src/order-service/src/main/resources/migration-138-logistics.sql' },
    @{ Db = 'mall_order';   File = 'src/order-service/src/main/resources/migration-144-order-category.sql' },
    @{ Db = 'mall_order';   File = 'src/order-service/src/main/resources/migration-145-flash-checkout.sql' },

    @{ Db = 'mall_payment'; File = 'src/payment-service/src/main/resources/migration-53-seller-id.sql' },
    @{ Db = 'mall_payment'; File = 'src/payment-service/src/main/resources/migration-reconcile.sql' },
    @{ Db = 'mall_payment'; File = 'src/payment-service/src/main/resources/migration-refund-notification.sql' },
    @{ Db = 'mall_payment'; File = 'src/payment-service/src/main/resources/migration-137-channel.sql' },
    @{ Db = 'mall_payment'; File = 'src/payment-service/src/main/resources/migration-140-promotion.sql' },
    @{ Db = 'mall_payment'; File = 'src/payment-service/src/main/resources/migration-143-after-sale.sql' },
    @{ Db = 'mall_payment'; File = 'src/payment-service/src/main/resources/migration-144-settlement-ledger.sql' }
)

foreach ($m in $migrations) {
    if (-not (Test-Path -LiteralPath (Join-Path $repo $m.File) -PathType Leaf)) {
        throw "找不到迁移文件：$($m.File)"
    }
}

Write-Host "本机旧库升级目标：$User@${DbHost}:$Port"
Write-Host '请先停止商城应用、备份并核验业务库，再继续。脚本不删除业务数据；会回填缺失的历史发货和完成时间。DDL 不能整体回滚。'
if ((Read-Host '确认已停服并完成备份？输入 YES 继续') -cne 'YES') { Write-Host '未执行迁移。'; exit 1 }
$secure = Read-Host -AsSecureString '请输入 MySQL 口令（不显示）'
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$previous = [Environment]::GetEnvironmentVariable('MYSQL_PWD','Process')
$step = '预检查'
Push-Location -LiteralPath $repo
try {
    $env:MYSQL_PWD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    # 只支持已有商城基表；先检查四个目标库，避免升级到一半才发现库或基表不存在。
    Invoke-Mysql 'mall_user' 'SELECT 1 FROM `user` LIMIT 0; SELECT 1 FROM shop LIMIT 0;'
    Invoke-Mysql 'mall_product' 'SELECT 1 FROM sku LIMIT 0;'
    Invoke-Mysql 'mall_order' 'SELECT 1 FROM `order` LIMIT 0; SELECT 1 FROM order_item LIMIT 0; SELECT 1 FROM order_idempotency LIMIT 0;'
    Invoke-Mysql 'mall_payment' 'SELECT 1 FROM payment LIMIT 0; SELECT 1 FROM refund LIMIT 0; SELECT 1 FROM after_sale_ticket LIMIT 0; SELECT 1 FROM settlement_item LIMIT 0;'
    foreach ($m in $migrations) {
        $step = "$($m.Db): $($m.File)"
        Invoke-Mysql -Database $m.Db -Command "source $($m.File)"
        Write-Host "[OK] $step"
    }

    # LIMIT 0 让服务所需的列由 MySQL 校验，但不输出业务行。
    $checks = @(
        @{ Name = '买家订单列表'; Db = 'mall_order'
           Sql = 'SELECT order_id, order_no, buyer_id, seller_id, main_order_id, status, total_amount, freight_amount, discount_amount, pay_amount, receiver, receiver_phone, address_full, tracking_no, express_company, shipped_at, expire_at, paid_at, created_at, updated_at, created_by, updated_by, is_deleted, inventory_released FROM `order` LIMIT 0' },
        @{ Name = '自动确认收货'; Db = 'mall_order'
           Sql = "SELECT order_id FROM ``order`` WHERE status = 'SHIPPED' AND shipped_at IS NOT NULL LIMIT 0" },
        @{ Name = '订单明细快照'; Db = 'mall_order'
           Sql = 'SELECT id, order_id, sku_id, product_id, category_id_snapshot, flash_sale_id, price_snapshot FROM order_item LIMIT 0' },
        @{ Name = '买家售后列表'; Db = 'mall_payment'
           Sql = 'SELECT ticket_id, order_id, payment_id, buyer_id, seller_id, after_sale_type, amount, reason, reason_code, status, refund_id, return_tracking_no, exchange_tracking_no, audit_remark, audited_by, audited_at, buyer_shipped_at, seller_shipped_at, idempotency_key, active_flag, created_at, updated_at, created_by, updated_by, is_deleted FROM after_sale_ticket LIMIT 0' },
        @{ Name = '结算账本'; Db = 'mall_payment'
           Sql = 'SELECT commission_snapshot, refunded_amount FROM settlement_item LIMIT 0' }
    )
    foreach ($c in $checks) {
        $step = $c.Name
        Invoke-Mysql -Database $c.Db -Command $c.Sql
        Write-Host "[PASS] $step"
    }
    Write-Host "升级完成：$($migrations.Count) 个脚本，$($checks.Count) 项 SQL 校验。现在可启动新版并验证页面。"
} catch {
    Write-Host "[FAIL] ${step}：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host '已停止。先核对报错和备份，不要清库或忽略错误。'
    exit 1
} finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previous, 'Process')
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    $secure.Dispose()
    Pop-Location
}
exit 0
