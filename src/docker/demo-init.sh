#!/usr/bin/env bash
# 演示数据初始化（#103）——显式手动触发，不参与正常部署
#
# 为什么需要：冷启动后商品列表为空是预期行为。init-db.sql 只建空库，
# 表结构与分类由各服务启动时执行 schema.sql / data.sql 建立，没有商品种子。
# 演示 / 答辩前手动跑一次本脚本，通过真实 HTTP API 备好「演示卖家 + 演示店铺 + 演示商品」。
#
# 边界：不写数据库、不灌数据到正常部署流程；只创建带 [DEMO_SEED]- 标记的数据；
#       重跑按标记去重，不重复创建演示用户 / 商品，也不改动其他商品。
#
# 【专用演示卖家 + 单页安全容量】本脚本只负责“把小演示环境从空库初始化成可演示”，
# 不是通用卖家数据管理工具：
#   1) 只有在**单页**（size=100）内做完去重、才能知道“还缺几件演示商品”；所以判别式先算
#      所需峰值 = 现有商品总数 + 尚缺演示商品数，峰值 ≤ 单页容量 96 才允许写入，否则退出码 3。
#      已完整初始化（4 件都在）的卖家所需峰值 = 现有总数，因此重跑不会因为
#      “上次已经多了 4 件” 而把自己判成超限——两次完整运行必须都成功且不新增；
#      而现有总数 > 100 的卖家无论缺几件都放不进一页，直接拒绝；
#   2) 只在**单页**内做去重与归属校验，不分页、不跨页比对：
#      服务端 /api/products/mine 的排序是 `ORDER BY created_at DESC`（DATETIME 秒级精度），
#      没有稳定二级键；同一秒内创建的记录在 offset 分页下顺序不稳定，跨页比对会漏行；
#   3) 每次读取非过滤列表都校验 list 长度与 total 一致，不一致即失败退出（fail-closed）。
#   因此：不要并发运行本脚本，不要把它指向与业务共用的卖家账号。
#
# 用法（在 src/docker 目录下；WSL 里请用 127.0.0.1 而不是 localhost）：
#   bash demo-init.sh                          # 网关 http://127.0.0.1:19000（verify 部署）
#   bash demo-init.sh --gateway http://127.0.0.1:9000   # 普通 compose 部署（-f docker-compose.yml）
#   bash demo-init.sh --dry-run                # 只做预检，不写入
#   DEMO_SELLER_PASSWORD=... bash demo-init.sh # 卖家口令走环境变量（推荐，可重复执行）
#
# 卖家口令：优先取环境变量 DEMO_SELLER_PASSWORD；未设置时在交互终端隐藏输入，
#   非交互环境（管道 / CI）直接报错退出，不再随机生成。脚本不写 .env、不回显口令。
#   同一演示账号 + 同一口令重复执行才是幂等的（口令不同会按“账号已存在”失败）。
# 管理员配置：同目录 .env 需自行创建并填写 ADMIN_BOOTSTRAP_ACCOUNT / ADMIN_BOOTSTRAP_PASSWORD，
#   仓库里没有该文件、也不含任何真实凭据。
# 退出码：0 成功（含“此前已初始化过”）；
#         1 预检失败；2 请求失败 / 业务码非 0 / 响应不合规；
#         3 安全边界：现有商品数 + 尚缺演示商品数 超过单页安全容量（在业务写入之前退出）。
set -euo pipefail

GATEWAY="http://127.0.0.1:19000"
ENV_FILE="$(cd "$(dirname "$0")" && pwd)/.env"
SELLER_ACCOUNT="demo_seller"
SELLER_NICKNAME=""
# 卖家口令只来自 --seller-password（兼容旧用法）或环境变量，不随机生成
SELLER_PASSWORD="${DEMO_SELLER_PASSWORD:-}"
SHOP_NAME="演示店铺"
TIMEOUT=15
DRY_RUN="0"
# 单页安全容量：卖家现有商品数 + 尚缺演示商品数 不能超过它（见文件头说明）。
# 96 + 4 件演示商品 = 100，正好是单页上限，保证整批数据始终落在 /mine 的同一页里。
PAGE_SIZE=100
PAGE_SAFE_CAP=96
BODY_FILE="$(mktemp)"

trap 'rm -f "$BODY_FILE"' EXIT
while [[ $# -gt 0 ]]; do
    case "$1" in
        --gateway) GATEWAY="${2%/}"; shift 2 ;;
        --env-file) ENV_FILE="$2"; shift 2 ;;
        --seller-account) SELLER_ACCOUNT="$2"; shift 2 ;;
        --seller-nickname) SELLER_NICKNAME="$2"; shift 2 ;;
        --seller-password) SELLER_PASSWORD="$2"; shift 2 ;;
        --timeout) TIMEOUT="$2"; shift 2 ;;
        --dry-run) DRY_RUN="1"; shift ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "未知参数: $1（--help 查看用法）" >&2; exit 1 ;;
    esac
done

log() { echo "[demo-init] $*"; }
die() { echo "[demo-init] 失败: $*" >&2; exit "${2:-2}"; }

# 演示商品：键|标题|副标题|分类ID|规格|价格(分)|库存|低库存阈值
DEMO_PRODUCTS=(
    "math|【DEMO】高等数学 同济第七版|演示教材，含习题全解|C01|默认规格|3900|30|5"
    "kaoyan|【DEMO】考研英语真题精讲|演示考研资料，2015-2024|C0102|默认规格|5900|20|3"
    "earbuds-std|【DEMO】无线蓝牙耳机|演示数码，续航 30 小时|C0201|标准版|12900|15|3"
    "earbuds-anc|【DEMO】无线蓝牙耳机|演示数码，续航 30 小时|C0201|降噪版|19900|10|2"
)
PRODUCT_MARK=" [DEMO_SEED]-"

# ---------- 预检 ----------
command -v curl >/dev/null 2>&1 || die "缺少 curl" 1
command -v python3 >/dev/null 2>&1 || die "缺少 python3（用于解析 JSON 响应）" 1
[[ "$SELLER_ACCOUNT" =~ ^[a-zA-Z0-9_]{4,20}$ ]] || die "演示卖家账号须为 4-20 位字母/数字/下划线" 1
# 昵称在库里有唯一约束，必须随账号变化，否则换账号重跑会以 20003 失败
SELLER_NICKNAME="${SELLER_NICKNAME:-演示卖家_${SELLER_ACCOUNT}}"
(( ${#SELLER_NICKNAME} >= 2 && ${#SELLER_NICKNAME} <= 20 )) \
    || die "演示卖家昵称长度须为 2-20 位（当前 ${#SELLER_NICKNAME} 位），可用 --seller-nickname 指定" 1

# 卖家口令：必须显式提供且稳定，否则重跑无法登录同一演示账号。
# 不随机生成、不写 .env、不回显；非交互环境无法隐藏输入时直接报错，不做猜测。
if [[ -z "$SELLER_PASSWORD" ]]; then
    if [[ -t 0 ]]; then
        read -r -s -p "[demo-init] 输入演示卖家 ${SELLER_ACCOUNT} 的口令（不回显）：" SELLER_PASSWORD
        echo
    else
        die "未提供演示卖家口令：请在非交互环境设置 DEMO_SELLER_PASSWORD 后重试（脚本不写 .env、不回显口令）" 1
    fi
fi
(( ${#SELLER_PASSWORD} >= 6 && ${#SELLER_PASSWORD} <= 32 )) || die "演示卖家口令长度须为 6-32 位" 1

# ---------- HTTP 辅助 ----------
# 错误只暴露 label / HTTP 状态码 / 业务码与安全概括，不打印响应体：
# 响应体可能含 JWT、账号口令回显或服务端堆栈，不能进日志。
# jcode：从响应体提取业务 code；非 JSON / 缺字段输出 "?"（由调用方按失败处理）
jcode() {
    python3 -c 'import sys,json
try:
    print(json.load(sys.stdin).get("code"))
except Exception:
    print("?")'
}

# jfield PATH：从响应体按点号路径取字符串字段，缺失或空值返回空串
jfield() {
    python3 -c 'import sys,json
path = sys.argv[1].split(".")
try:
    cur = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for key in path:
    if not isinstance(cur, dict) or key not in cur:
        sys.exit(0)
    cur = cur[key]
if cur is None:
    sys.exit(0)
print(cur if isinstance(cur, str) else json.dumps(cur))' "$1"
}

# request METHOD PATH [TOKEN] [JSON_BODY]：HTTP 状态码写到 $HTTP_CODE，响应体写到 $BODY_FILE。
# 连接失败 / 超时返回非 0，由调用方给出不含响应体的概括。
request() {
    local method="$1" path="$2" token="${3:-}" body="${4:-}"
    local args=(-s -m "$TIMEOUT" -o "$BODY_FILE" -w '%{http_code}' -X "$method" "${GATEWAY}${path}")
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer ${token}")
    [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' --data-binary "$body")
    HTTP_CODE="$(curl "${args[@]}")" || return 1
    [[ -n "$HTTP_CODE" ]] || return 1
    return 0
}

# api_ok LABEL METHOD PATH [TOKEN] [JSON_BODY]：要求 HTTP 200 且业务 code=0，
# 成功时把响应体写到 stdout；失败立即以退出码 2 终止（只打码值，不打响应体）。
api_ok() {
    local label="$1"; shift
    local bcode
    request "$@" || die "${label}: 连接失败或超时（网关 ${GATEWAY}）"
    [[ "$HTTP_CODE" == "200" ]] || die "${label}: HTTP ${HTTP_CODE}"
    bcode="$(jcode <"$BODY_FILE")"
    [[ "$bcode" == "0" ]] || die "${label}: 业务码 code=${bcode}"
    cat "$BODY_FILE"
}
# login_token ACCOUNT PASSWORD：登录并取令牌。
# HTTP 非 200、业务码非 0、缺 data.token 或为空值，任一情形都打印原因并返回非 0，
# 由调用方转成非零退出码（绝不在缺 token 的情况下继续用空令牌发请求）。
login_token() {
    local account="$1" password="$2" bcode token
    request POST "/api/users/login" "" \
        "$(python3 -c 'import json,sys;print(json.dumps({"account":sys.argv[1],"password":sys.argv[2]}))' "$account" "$password")" \
        || { echo "[demo-init] 失败: 登录 ${account}: 连接失败或超时" >&2; return 1; }
    [[ "$HTTP_CODE" == "200" ]] \
        || { echo "[demo-init] 失败: 登录 ${account}: HTTP ${HTTP_CODE}" >&2; return 1; }
    bcode="$(jcode <"$BODY_FILE")"
    [[ "$bcode" == "0" ]] \
        || { echo "[demo-init] 失败: 登录 ${account}: 业务码 code=${bcode}（管理员口令需与 .env 中 ADMIN_BOOTSTRAP_PASSWORD 一致）" >&2; return 1; }
    token="$(jfield data.token <"$BODY_FILE")"
    [[ -n "$token" ]] \
        || { echo "[demo-init] 失败: 登录 ${account}: 响应缺少 data.token 或为空值" >&2; return 1; }
    printf '%s' "$token"
}

# need_token VAR ACCOUNT PASSWORD：登录取令牌并赋给 VAR。
# 失败原因由 login_token 打到 stderr；因为 exit 在 $(...) 子 shell 里传不出来，
# 用哨兵字符串把失败带回当前 shell 再退出。
TOKEN_FAIL="__TOKEN_FAIL__"
need_token() {
    local got
    got="$(login_token "$2" "$3" || echo "$TOKEN_FAIL")"
    [[ "$got" != "$TOKEN_FAIL" ]] || exit 2
    eval "$1=\$got"
}

# ---------- 单页安全容量辅助 ----------
# 一次 GET /api/products/mine?page=1&size=100（带可选 status）把响应体留在 $BODY_FILE，
# 并校验：HTTP 200 / code=0，未带 status 时还要求 data.list 长度与 data.total 一致。
# fetch_mine LABEL [STATUS]：成功返回 0 且 $MINE_TOTAL 为 total；结构不合规立即终止（退出码 3）。
# $2 非空时按状态过滤（服务端先筛后分页，total 是该状态下的数量）——
# 过滤之后的 list 本来就只是一部分，不能再和 total 比长度。
MINE_TOTAL=0
# 商品列表响应快照（单页，供去重/归属/结果核对复用），退出时一并删除
MINE_SNAPSHOT="$(mktemp)"
trap 'rm -f "$BODY_FILE" "$MINE_SNAPSHOT"' EXIT
fetch_mine() {
    local label="$1" status="${2:-}" query="page=1&size=${PAGE_SIZE}"
    [[ -z "$status" ]] || query+="&status=${status}"
    # 注意：不能写成 `api_ok ... >"$BODY_FILE"`——api_ok 自己要把响应体 cat 出来，
    # 而 $BODY_FILE 正是它写入的那份文件，重定向到同一路径会把内容清空。
    # 这里把 api_ok 的输出另存一份快照再用。
    local resp verdict
    resp="$(api_ok "${label}" GET "/api/products/mine?${query}" "$SELLER_TOKEN")" \
        || die "${label}: 请求失败"
    printf '%s' "$resp" >"$MINE_SNAPSHOT"
    # 严格校验：total 必须是数字、list 必须是数组；未带 status 时 list 长度还必须等于 total。
    # 任何一条不满足都 fail-closed——不做“猜总数”或“只要非空就继续”。
    verdict="$(python3 - "$MINE_SNAPSHOT" "$status" <<'PY'
import json, sys
try:
    d = json.load(open(sys.argv[1], encoding="utf-8"))["data"]
    total = d.get("total")
    rows = d.get("list")
except Exception:
    print("BAD_STRUCTURE"); sys.exit(0)
if not isinstance(total, int) or total < 0 or not isinstance(rows, list):
    print("BAD_STRUCTURE"); sys.exit(0)
if not sys.argv[2] and len(rows) != total:
    print("MISMATCH %d %d" % (len(rows), total)); sys.exit(0)
print("OK %d %d" % (total, len(rows)))
PY
)" 2>/dev/null
    [[ -n "$verdict" ]] || die "${label}: 响应不是预期的 JSON 结构"
    case "$verdict" in
        "OK "*) MINE_TOTAL="${verdict#OK }"; MINE_TOTAL="${MINE_TOTAL%% *}" ;;
        "MISMATCH "*)
            local got want
            read -r got want <<<"${verdict#MISMATCH }"
            die "${label}: 本页 ${got} 行与 total=${want} 不一致（分页不稳定、数据在变，或该卖家商品已超出单页）——无法在单页内可信去重，已停止，不写任何数据" 3
            ;;
        *) die "${label}: 响应缺少 data.list / data.total 或类型不合规" 3 ;;
    esac
    return 0
}

# require_safe_capacity LABEL：写入前的规模闸门——现有商品总数 + 尚缺演示商品数
# 必须放得进单页安全容量，否则在任何业务写入之前退出（退出码 3）。
# 已完整初始化的卖家 missing=0，所需峰值就是现有总数，所以重跑不会把自己判成超限；
# 而现有总数本身 > 当前 PAGE_SIZE 的卖家（单页装不下、去重不可信）一律拒绝。
require_safe_capacity() {
    local label="$1" missing="$2" peak
    peak=$(( MINE_TOTAL + missing ))
    (( MINE_TOTAL <= PAGE_SIZE )) \
        || die "${label}: 该卖家现有商品 ${MINE_TOTAL} 件已超过单页容量 ${PAGE_SIZE}，无法在单页内可信去重；本脚本只做小型演示初始化。换一个专用演示账号（--seller-account）" 3
    (( peak <= PAGE_SAFE_CAP )) \
        || die "${label}: 现有商品 ${MINE_TOTAL} 件 + 尚缺演示商品 ${missing} 件 = ${peak} 件，超过单页安全容量 ${PAGE_SAFE_CAP}（页面上限 ${PAGE_SIZE} 内需预留演示商品位置）。已停止，不写任何数据；换一个专用演示账号（--seller-account）" 3
}

# ---------- 预检：网关与管理员配置（先做本地预检，不碰网络） ----------
[[ -f "$ENV_FILE" ]] || die "找不到 ${ENV_FILE}：管理员引导账号只能配置在 .env（见 README「管理员账号」）" 1
ADMIN_ACCOUNT="$(sed -n 's/^[[:space:]]*ADMIN_BOOTSTRAP_ACCOUNT=//p' "$ENV_FILE" | tail -n1 | tr -d '\r')"
ADMIN_PASSWORD="$(sed -n 's/^[[:space:]]*ADMIN_BOOTSTRAP_PASSWORD=//p' "$ENV_FILE" | tail -n1 | tr -d '\r')"
[[ -n "$ADMIN_ACCOUNT" && -n "$ADMIN_PASSWORD" ]] \
    || die "${ENV_FILE} 缺少 ADMIN_BOOTSTRAP_ACCOUNT / ADMIN_BOOTSTRAP_PASSWORD：没有管理员就无法审核商品上架" 1

# 卖家不能与管理员同名：否则脚本会把这个账号当卖家赋角色、改店铺，等于动了管理员账号
[[ "$SELLER_ACCOUNT" != "$ADMIN_ACCOUNT" ]] \
    || die "演示卖家账号 ${SELLER_ACCOUNT} 与管理员账号相同：请用 --seller-account 指定其他账号，脚本不会修改管理员账号的店铺与角色" 1

log "网关：${GATEWAY}"
request GET "/api/products?page=1&size=1" || die "网关连通性（匿名商品列表）: 连接失败或超时（网关 ${GATEWAY}）"
[[ "$HTTP_CODE" == "200" ]] || die "网关连通性（匿名商品列表）: HTTP ${HTTP_CODE}"
[[ "$(jcode <"$BODY_FILE")" == "0" ]] || die "网关连通性（匿名商品列表）: 业务码 code=$(jcode <"$BODY_FILE")"

need_token ADMIN_TOKEN "$ADMIN_ACCOUNT" "$ADMIN_PASSWORD"
log "管理员登录成功（口令不回显）"

# 管理员令牌对应的真实账号必须是配置里的 ADMIN_ACCOUNT：网关按令牌里的角色注入
# X-User-Role，若令牌实际属于别的账号，后续会以错误身份改数据。
request GET "/api/users/me" "$ADMIN_TOKEN" || die "校验管理员身份: 连接失败或超时"
[[ "$HTTP_CODE" == "200" ]] || die "校验管理员身份: HTTP ${HTTP_CODE}"
[[ "$(jfield data.role <"$BODY_FILE")" == "ADMIN" ]] \
    || die "校验管理员身份: ${ADMIN_ACCOUNT} 的角色不是 ADMIN，无法审核商品"
ADMIN_ID="$(jfield data.userId <"$BODY_FILE")"
[[ -n "$ADMIN_ID" ]] || die "校验管理员身份: 响应缺少 data.userId 或为空值"

# ---------- 演示卖家：先探测是否已存在（口令稳定，重跑才能复用同一账号） ----------
# 业务码含义（user-service ErrorCode）：
#   20001 ACCOUNT_OR_PASSWORD_ERROR —— 账号不存在与口令错误**同一个码**，服务端不区分；
#   20004 ACCOUNT_FROZEN —— 账号存在且口令正确但被冻结。
# 所以只有 20001 才可能是“账号不存在”，允许走注册探测；20004 必须报冻结并退出，
# 绝不能把冻结账号当成不存在去注册（那会撞 20002 或掩盖真实状态）。
request POST "/api/users/login" "" \
    "$(python3 -c 'import json,sys;print(json.dumps({"account":sys.argv[1],"password":sys.argv[2]}))' "$SELLER_ACCOUNT" "$SELLER_PASSWORD")" \
    || die "探测演示卖家 ${SELLER_ACCOUNT}: 连接失败或超时"
SELLER_EXISTS="0"
if [[ "$HTTP_CODE" == "200" ]]; then
    case "$(jcode <"$BODY_FILE")" in
        0) SELLER_EXISTS="1" ;;
        20001)
            # 账号不存在或口令错误（服务端不区分）：按“需要注册”处理，
            # 若账号其实存在但口令不同，注册会以 20002 明确失败，不会覆盖旧账号。
            SELLER_EXISTS="0"
            ;;
        20004)
            die "探测演示卖家 ${SELLER_ACCOUNT}: 业务码 code=20004（账号已被冻结）。冻结账号不能当作不存在处理，脚本既不会注册同名账号也不会改写它；请解冻该账号或换一个 --seller-account" 2
            ;;
        *)
            die "探测演示卖家 ${SELLER_ACCOUNT}: HTTP ${HTTP_CODE} 业务码 code=$(jcode <"$BODY_FILE")（非 20001/20004，按失败退出）" 2
            ;;
    esac
else
    die "探测演示卖家 ${SELLER_ACCOUNT}: HTTP ${HTTP_CODE}" 2
fi

if [[ "$SELLER_EXISTS" == "1" ]]; then
    log "演示卖家 ${SELLER_ACCOUNT} 已存在且口令匹配，复用"
elif [[ "$DRY_RUN" == "1" ]]; then
    log "[dry-run] 将注册演示卖家 ${SELLER_ACCOUNT}（账号已存在但口令不同会在此停止）"
else
    # 注册是唯一可能新建账号的写入。此前的 20001 已排除“账号存在”的已知码，
    # 若实际已存在（例如口令不同）这里会以 20002 失败并保持旧账号不变。
    api_ok "注册演示卖家 ${SELLER_ACCOUNT}" POST "/api/users/register" "" \
        "$(python3 -c 'import json,sys;print(json.dumps({"account":sys.argv[1],"password":sys.argv[2],"nickname":sys.argv[3]}))' "$SELLER_ACCOUNT" "$SELLER_PASSWORD" "$SELLER_NICKNAME")" >/dev/null
    log "已注册演示卖家 ${SELLER_ACCOUNT}"
fi

# 新注册用户默认是 BUYER，需要管理员赋 SELLER 后重新登录拿新令牌
need_token SELLER_TOKEN "$SELLER_ACCOUNT" "$SELLER_PASSWORD"
request GET "/api/users/me" "$SELLER_TOKEN" || die "查询演示卖家身份: 连接失败或超时"
[[ "$HTTP_CODE" == "200" ]] || die "查询演示卖家身份: HTTP ${HTTP_CODE}"
[[ "$(jcode <"$BODY_FILE")" == "0" ]] || die "查询演示卖家身份: 业务码 code=$(jcode <"$BODY_FILE")"
SELLER_ROLE="$(jfield data.role <"$BODY_FILE")"
SELLER_ID="$(jfield data.userId <"$BODY_FILE")"
[[ -n "$SELLER_ROLE" && -n "$SELLER_ID" ]] \
    || die "查询演示卖家身份: 响应缺少 data.role / data.userId 或为空值"

# 双重护栏：数据库里卖家账号的角色若已是 ADMIN，或它与管理员是同一身份，立即停止，
# 绝不改其角色与店铺（否则会把管理员降级 / 改掉管理员的店）。
[[ "$SELLER_ID" != "$ADMIN_ID" ]] \
    || die "演示卖家 ${SELLER_ACCOUNT} 与管理员是同一账号（userId=${ADMIN_ID}）：脚本不会修改管理员账号的角色与店铺"
[[ "$SELLER_ROLE" != "ADMIN" ]] \
    || die "演示卖家 ${SELLER_ACCOUNT}（${SELLER_ID}）的实际角色是 ADMIN：拒绝修改管理员账号，请用 --seller-account 换普通账号"

# ---------- 容量闸门：任何写入之前先算出“还缺几件”并确认放得进单页 ----------
# 这是本脚本的安全前提（单页去重依赖它），所以放在赋角色/建店/发布之前：
# 超限就在业务写入前退出（退出码 3），不留下半成品数据。
# 这里必须先读到现有的演示商品，才能知道还缺几件——所以闸门不是“现有总数 ≤ 上限”，
# 而是“现有总数 + 尚缺件数 ≤ 单页安全容量”；已完整的卖家 missing=0 因此可以重跑。
# 注意：fetch_mine 必须在本 shell 里调用（它要写 $MINE_TOTAL 与 $MINE_SNAPSHOT），
# 不能塞进 $(...) ——命令替换是子 shell，赋值和快照都不会回传。
fetch_mine "确认演示卖家商品规模"
DEMO_MISSING="$(python3 - "$MINE_SNAPSHOT" "$PRODUCT_MARK" "${DEMO_PRODUCTS[@]}" <<'PY'
import json, sys
mark = sys.argv[2]
present = {p.get("title") or "" for p in json.load(open(sys.argv[1], encoding="utf-8"))["data"]["list"]}
# 与后面的发布流程用同一套标题拼接规则：标题|副标题|分类|规格|价格|库存|阈值
missing = 0
for item in sys.argv[3:]:
    fields = item.split("|")
    if fields[1] + mark + fields[0] not in present:
        missing += 1
print(missing)
PY
)"
DEMO_MARKED_COUNT=$(( ${#DEMO_PRODUCTS[@]} - DEMO_MISSING ))
log "演示卖家 ${SELLER_ACCOUNT} 现有商品 ${MINE_TOTAL} 件，其中带演示标记 ${DEMO_MARKED_COUNT} 件，尚缺 ${DEMO_MISSING} 件"
require_safe_capacity "确认演示卖家商品规模" "$DEMO_MISSING"
log "容量校验通过：${MINE_TOTAL} + ${DEMO_MISSING} = $(( MINE_TOTAL + DEMO_MISSING )) ≤ 单页安全容量 ${PAGE_SAFE_CAP}"

# dry-run 在“规模闸门”通过之后才结束：这样超限的账号即使预检也会如实报错，
# 而不是先报“预检通过”再在真实执行时失败。
if [[ "$DRY_RUN" == "1" ]]; then
    log "[dry-run] 预检通过（含商品规模闸门），未写入任何数据"
    exit 0
fi

if [[ "$SELLER_ROLE" != "SELLER" ]]; then
    api_ok "赋予卖家角色" PUT "/api/users/${SELLER_ID}/role" "$ADMIN_TOKEN" '{"role":"SELLER"}' >/dev/null
    log "管理员已将 ${SELLER_ACCOUNT}（${SELLER_ID}）设为 SELLER"
    need_token SELLER_TOKEN "$SELLER_ACCOUNT" "$SELLER_PASSWORD"
fi

# ---------- 演示店铺（PUT /api/shops/mine，入参字段是 name，不是 shopName） ----------
api_ok "维护演示店铺" PUT "/api/shops/mine" "$SELLER_TOKEN" \
    "$(python3 -c 'import json,sys;print(json.dumps({"name":sys.argv[1],"description":"演示用店铺，由 src/docker/demo-init.sh 创建"},ensure_ascii=False))' "$SHOP_NAME")" >/dev/null
log "演示店铺已就绪：${SHOP_NAME}"

# ---------- 演示商品（标题尾带标记，作为重跑去重键） ----------
# 单页处理：容量闸门已保证「现有商品 + 尚缺演示商品」≤ 单页安全容量 96，
# 所以 page=1&size=100 一页就能拿全（含本次新增）；
# 这里不分页、不跨页比对（/mine 的 ORDER BY created_at DESC 没有稳定二级键）。
# 在那一页里按「本卖家 + 标题带标记」筛，后续审核也只针对筛出的 productId，
# 不跨卖家审核，也不扫全局管理员 PENDING 列表。
MINE_ROWS="$(python3 - "$PRODUCT_MARK" "$MINE_SNAPSHOT" <<'PY'
import json, sys
mark = sys.argv[1]
data = json.load(open(sys.argv[2], encoding="utf-8"))["data"]
for p in data["list"]:
    if mark in (p.get("title") or ""):
        print(p["productId"], p["status"], p["title"], sep="\t")
PY
)"
# 去重：同一标题出现多次说明数据已不可信（重跑本不该重复），直接失败而不是挑一条。
TODO_AUDIT=""
for item in "${DEMO_PRODUCTS[@]}"; do
    IFS='|' read -r key title subtitle category spec price stock threshold <<<"$item"
    full_title="${title}${PRODUCT_MARK}${key}"
    found="$(printf '%s' "$MINE_ROWS" | python3 -c 'import sys
for line in sys.stdin.read().splitlines():
    parts = line.split("\t")
    if len(parts) == 3 and parts[2] == sys.argv[1]:
        print(parts[0], parts[1], sep="\t")' "$full_title")"
    if [[ -n "$found" ]]; then
        [[ "$(printf '%s\n' "$found" | wc -l)" -eq 1 ]] \
            || die "演示商品 ${full_title} 在卖家商品里出现多条，数据状态不明确，已停止（不猜测、不删除）" 3
        log "演示商品已存在，跳过发布：${full_title}"
        while IFS=$'\t' read -r pid status; do
            [[ "$status" == "PENDING" ]] && TODO_AUDIT+="${pid}"$'\n'
        done <<<"$found"
        continue
    fi
    body="$(python3 -c '
import json, sys
title, subtitle, category, spec, price, stock, threshold = sys.argv[1:8]
print(json.dumps({"title": title, "subtitle": subtitle, "categoryId": category,
                  "skus": [{"specName": spec, "price": int(price),
                            "stockAvailable": int(stock), "lowStockThreshold": int(threshold)}]},
                 ensure_ascii=False))' "$full_title" "$subtitle" "$category" "$spec" "$price" "$stock" "$threshold")"
    api_ok "发布演示商品 ${full_title}" POST "/api/products" "$SELLER_TOKEN" "$body" >"$BODY_FILE"
    pid="$(jfield data.productId <"$BODY_FILE")"
    [[ -n "$pid" ]] || die "发布演示商品 ${full_title}: 响应缺少 data.productId 或为空值"
    log "已发布演示商品：${full_title}（${pid}，待审核）"
    TODO_AUDIT+="${pid}"$'\n'
done

# ---------- 审核上架：只审「本卖家 + demo 标记」的 productId ----------
# 不复用全局管理员 PENDING 列表：那里可能有其他卖家的待审核商品，
# 按标题标记扫全局会替别人放行，既越权也不可解释。
# 审核前重新做一次**完整单页归属校验**（同样校验 list 长度与 total 一致、不超单页容量），
# 而不是只信刚发布时拿到的那份列表。
if [[ -n "$(printf '%s' "$TODO_AUDIT" | tr -d '[:space:]')" ]]; then
    fetch_mine "复核演示商品归属"
    # 写入后总数不得超过单页容量（已无新增，所以 missing=0；超出即单页装不下）
    require_safe_capacity "复核演示商品归属" 0
    OWNED_MARKED="$(python3 - "$PRODUCT_MARK" "$MINE_SNAPSHOT" <<'PY'
import json, sys
mark = sys.argv[1]
data = json.load(open(sys.argv[2], encoding="utf-8"))["data"]
for p in data["list"]:
    if mark in (p.get("title") or "") and p.get("status") == "PENDING":
        print(p["productId"])
PY
)"
    while IFS= read -r pid; do
        [[ -n "$pid" ]] || continue
        grep -qxF -- "$pid" <<<"$OWNED_MARKED" \
            || die "审核前校验: ${pid} 不属于演示卖家、不是带标记商品或已不是 PENDING，已拒绝审核" 3
        api_ok "审核通过 ${pid}" PUT "/api/products/${pid}/audit" "$ADMIN_TOKEN" \
            '{"approved":true,"remark":"DEMO_SEED 演示数据审核通过"}' >/dev/null
        log "管理员已审核通过 ${pid}（ON_SALE）"
    done <<<"$(printf '%s' "$TODO_AUDIT")"
fi

# ---------- 结果核对：匿名列表（同样校验 list 长度与 total 一致） ----------
# 期望值 = 该卖家名下**全部**带标记商品（原有 + 本次新增），从同一份单页快照里数出来；
# 不用“原有件数 + 本次新增件数”外推，避免补审 / 重跑时把已有商品重复计入。
n_expect="$(python3 - "$MINE_SNAPSHOT" "$PRODUCT_MARK" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))["data"]
print(sum(1 for p in data["list"] if sys.argv[2] in (p.get("title") or "")))
PY
)"
LIST_FILE="$(mktemp)"
api_ok "匿名商品列表" GET "/api/products?page=1&size=${PAGE_SIZE}" >"$LIST_FILE"
DEMO_COUNT="$(python3 - "$LIST_FILE" "$PRODUCT_MARK" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))["data"]
mark = sys.argv[2]
rows = data["list"]
total = data["total"]
if len(rows) != total:
    print(f"[demo-init] 失败: 匿名列表本页 {len(rows)} 行与 total={total} 不一致，无法可信核对", file=sys.stderr)
    sys.exit(3)
demo = [p for p in rows if mark in p["title"]]
print(f"[demo-init] 匿名列表 total={total}，演示商品 {len(demo)} 件")
for p in demo:
    print(f"[demo-init]   {p['productId']} {p['title']} 价格={p['price']}分 库存={p['stock']} 状态={p['status']}")
print(len(demo))
PY
)" || die "结果核对: 匿名商品列表响应不合规（或本页行数与 total 不一致）" 3
rm -f "$LIST_FILE"
printf '%s\n' "$DEMO_COUNT" | sed '$d'
n_found="$(printf '%s\n' "$DEMO_COUNT" | tail -n1)"
(( n_found == n_expect )) \
    || die "结果核对: 匿名列表里演示商品 ${n_found} 件，期望 ${n_expect} 件（该卖家名下带标记商品全部在售），请检查审核状态" 3

log "完成。演示卖家账号：${SELLER_ACCOUNT}（口令来自 DEMO_SELLER_PASSWORD，不回显、不落盘；同一账号 + 同一口令可重复执行）"
log "注意：本脚本不保证并发运行安全，不要同时跑多份，也不要把它指向与业务共用的卖家账号。"
