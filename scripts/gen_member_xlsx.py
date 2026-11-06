# -*- coding: utf-8 -*-
"""
生成《成员材料填写表.xlsx》
6 个子表：5 人表（每人一页）+ 1 统计总表
依赖：openpyxl
"""
import os
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.formatting.rule import CellIsRule

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "成员材料填写表.xlsx")

# ---------- 样式 ----------
C_TITLE   = "1F3A5F"
C_HEADER  = "3E6FA8"
C_INPUT   = "FFF4E6"   # 待填单元格底色
C_FORMULA = "EAF2FB"   # 公式计算结果底色
C_NOTE    = "F2F2F2"
C_WARN    = "FDE2E2"

FONT_TITLE  = Font(name="微软雅黑", size=14, bold=True, color="FFFFFF")
FONT_HEAD   = Font(name="微软雅黑", size=10, bold=True, color="FFFFFF")
FONT_BODY   = Font(name="微软雅黑", size=10)
FONT_NOTE   = Font(name="微软雅黑", size=9, color="666666")
FONT_SMALL  = Font(name="微软雅黑", size=9)

THIN = Side(style="thin", color="C9D1D9")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)

ALIGN_C = Alignment(horizontal="center", vertical="center", wrap_text=True)
ALIGN_L = Alignment(horizontal="left", vertical="top", wrap_text=True)

DIMS = ["工作态度", "系统设计", "系统实现", "系统测试"]

# 成员定义：(表名, 代号, 角色, 负责范围, 预填总结, 是否属于该人)
MEMBERS = [
    ("哭女", "哭女", "组长", "网关、Nacos、接口契约、设计报告、集成联调",
     "作为组长，我主要承担 gateway 网关搭建、Nacos 服务治理、接口契约维护（迭代到 v1.7）以及跨服务集成联调。"
     "整个系统拆成 6 个微服务加前端，看似职责分明，但一到集成阶段，底层环境不一致与并发边界问题全部暴露出来。\n\n"
     "最典型的事故发生在容器化阶段。在 product-service 增加低库存预警功能时，schema.sql 里顺手写了 "
     "ALTER TABLE ... ADD COLUMN IF NOT EXISTS。本地单测跑 H2 内存库完全正常，但打成 Docker 镜像、"
     "拉起真实 MySQL 8 容器时服务直接挂掉——这是 MariaDB 的扩展语法，MySQL 8 不支持。"
     "内存库测试通过并不等于生产环境就绪。\n\n"
     "更棘手的是 Issue #74 的高并发重复扣库存：H2 在事务回滚后复用主键自增序列，掩盖了并发冲突；"
     "业务层 createFresh 提交事务与 Feign 预占之间存在毫秒级竞态窗口，并发重试误判状态，补发了第二次预占。"
     "最后引入 RESERVING 中间状态配合 CAS 抢占权控制才闭环。网关白名单也栽过跟头——直接放行卖家接口导致 "
     "JWT 未被解析，下游拿到 null userId。这次集成排查累计处理 8 个核心 Issue、提交代码超 1.8 万行，"
     "彻底磨平了我对微服务「表面解耦、隐式依赖」的认知盲区。"),
    ("王瑞", "王瑞（yy）", "组员", "商品服务、订单服务、并发测试、管理端 UI 改造",
     "我负责 product-service 与 order-service 的核心交易链路设计与并发测试，重心在库存的预占、确认、释放、回补，"
     "以及订单状态机和幂等保障。\n\n"
     "状态机选型初期讨论过分布式锁或行级悲观锁，但高并发下锁竞争剧烈，容易打满连接池。"
     "最终采用基于原状态约束的条件更新（CAS）：单条 UPDATE 内嵌前置状态校验，靠 InnoDB 行锁保证原子性，"
     "配合 order_idempotency 唯一索引拦截重试。\n\n"
     "写 Issue48OrderConcurrencyTest 时抓到一个极隐蔽的漏洞：买家主动取消与系统超时取消并发到达时，"
     "后到的请求读到订单已是 CANCELLED，UPDATE 影响行数为 0，但旧逻辑没阻断，仍顺位触发了一次库存释放 "
     "Feign 调用，导致同一笔订单库存被释放两次、可售库存虚增。我在影响行数不为 1 时强制拦截并抛幂等异常，"
     "堵死了这个口子。后来还接手管理端 10 个页面的 Element Plus 改造（PR #104），前后端联调让我更清楚"
     "接口字段冗余对前端渲染的负面影响。"),
    ("来财", "来财", "组员", "用户服务、前端商城化改造、卖家端 UI 改造",
     "我负责 user-service 的实现，以及整个前端的商城化改造和卖家端 6 个页面的重构（PR #92）。"
     "原工程是个二手闲置交易系统，改成正规 B2C 商城，前端几乎等于推倒重来。\n\n"
     "那段时间改动量极大，二十多个页面连续重构，尤其商品多规格（SKU）动态表单联动和上下架校验，"
     "数据结构嵌套深，前后调了两整天才把双向绑定和规格矩阵算对。商品编辑页踩了个大坑："
     "我在保存接口后跟了 loadProduct() 刷新详情，但后端规则是「商品被修改就打回 PENDING 待审核」，"
     "而详情接口对非在售商品直接报错，结果保存成功却弹出「加载失败」把成功提示盖掉。\n\n"
     "分类树也坑了一整天，后端返回无序导致子分类排到父类前面，最后靠后端按 level 排序、前端递归缩进才理顺。"
     "user-service 那边 PUT /api/users/{id}/role 传错参数直接吐 500，完全没法排查。"
     "前后端契约一旦有模糊，最遭殃的就是写 UI 的人。"),
    ("韬哥", "韬哥", "组员", "支付服务、买家端 UI 改造",
     "我负责 payment-service 的全生命周期，涵盖支付单流转、回调验签、幂等控制、退款与售后，"
     "同时承担买家端 8 个核心交易页面的 Element Plus 改造（PR #101）。\n\n"
     "支付回调我卡死了处理顺序：先无条件验签，再查幂等，接着核对金额，最后才推状态机。"
     "讨论过把查幂等前置以省验签开销，我坚持反对——未验签就写幂等去重表，攻击者可以用伪造回调把表打满，"
     "真实支付通知反倒被当重复消息丢弃，造成掉单。\n\n"
     "退款没有用 Redis 全局互斥锁，而是在 RefundMapper 用 WHERE refunded_amount + ? <= amount 的行级"
     "条件更新，把防超退压在数据库层。买家端改造后组长抓出我 3 处交互偏差：AfterSales 误加了「支付单必须 "
     "SUCCESS 才显示可退金额」，未付款时输入框露出来却看不见金额；OrderDetail 上传评价图没做总量限制，"
     "选 8 张会先传 3 张再拦截。后期还出过一次 payment 表缺 reconciled_at 列导致对账报错的事故。"
     "金融级业务容不得任何理所当然的侥幸。"),
    ("fd", "fd", "组员", "购物车服务、内部接口鉴权、账户页 UI 改造",
     "我负责 cart-service，后期增补了内部接口鉴权、商品评价、操作审计日志模块，"
     "并完成账户中心 4 个页面的 Element Plus 改造（PR #105）。\n\n"
     "购物车看着单薄，深挖全是边界：同一 SKU 多次加购必须累加数量而不是生成两行；"
     "商品下架或店铺被停用时不能物理 DELETE，否则买家刷新东西凭空消失，必须跨服务实时核验并标记失效置灰；"
     "价格不能在加购时固化，结算必须动态拉最新定价。\n\n"
     "排查 Issue #56 时发现 order-service 下单扣减购物车直接调了公开接口，网关剥离前缀后底层无法区分"
     "这是网关转发还是内部 Feign 调用，存在安全绕过隐患。我重构为 /internal/cart/** 并强制核验 "
     "X-Internal-Token。做账户页时还踩了个运行时坑：局域网 IP 访问属于非安全上下文，crypto.randomUUID() "
     "返回 undefined，导致防重提交令牌报错、按钮静默无反应，最后用全局守卫加随机数兜底解决。"
     "微服务的稳定正是建立在对这些边缘漏洞的严防死守上。"),
]

# 互评矩阵：谁评谁（不含自己）
def targets_of(idx):
    return [i for i in range(5) if i != idx]


def style_cell(c, font=FONT_BODY, fill=None, align=ALIGN_C, border=True):
    c.font = font
    if fill:
        c.fill = PatternFill("solid", fgColor=fill)
    c.alignment = align
    if border:
        c.border = BORDER


def build():
    wb = Workbook()
    wb.remove(wb.active)

    member_sheets = []

    # ================= 5 张个人表 =================
    for idx, (short, code, role, scope, summary) in enumerate(MEMBERS):
        ws = wb.create_sheet(f"{idx+1}-{short}")
        member_sheets.append(ws)
        ws.sheet_view.showGridLines = False

        widths = [4, 16, 22, 22, 22, 22, 12, 40]
        for i, w in enumerate(widths, start=1):
            ws.column_dimensions[get_column_letter(i)].width = w

        # 标题
        ws.merge_cells("A1:H1")
        c = ws["A1"]
        c.value = f"第 {idx+1} 位成员：{code}（{role}）— 个人材料填写"
        style_cell(c, FONT_TITLE, C_TITLE, ALIGN_C, border=False)
        ws.row_dimensions[1].height = 30

        # 身份区
        ws.merge_cells("A3:H3")
        c = ws["A3"]; c.value = "一、身份确认"
        style_cell(c, FONT_HEAD, C_HEADER, Alignment(horizontal="left", vertical="center"), border=False)
        ws.row_dimensions[3].height = 20

        labels = [("代号", code), ("角色", role), ("负责范围", scope)]
        r = 4
        for lb, val in labels:
            ws.cell(r, 1, "").border = BORDER
            ws.merge_cells(start_row=r, start_column=2, end_row=r, end_column=3)
            c = ws.cell(r, 2, lb); style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
            ws.merge_cells(start_row=r, start_column=4, end_row=r, end_column=8)
            c = ws.cell(r, 4, val); style_cell(c, FONT_BODY, None, Alignment(horizontal="left", vertical="center"))
            r += 1

        # 真实姓名 + 确认
        c = ws.cell(r, 2, "真实姓名")
        style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
        c = ws.cell(r, 4, "")
        style_cell(c, FONT_BODY, C_INPUT, ALIGN_C)
        ws.merge_cells(start_row=r, start_column=4, end_row=r, end_column=5)
        c = ws.cell(r, 6, "本人确认已填写")
        style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
        c = ws.cell(r, 7, "")
        style_cell(c, FONT_BODY, C_INPUT, ALIGN_C)
        ws.merge_cells(start_row=r, start_column=7, end_row=r, end_column=8)
        name_row = r

        # 数据验证：下拉
        dv_yes = DataValidation(type="list", formula1='"是,否"', allow_blank=True)
        ws.add_data_validation(dv_yes)
        dv_yes.add(ws.cell(name_row, 7))

        # 个人总结
        r += 2
        ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=8)
        c = ws.cell(r, 1, "二、个人实验总结（400-600 字，可沿用预填初稿或自行改写）")
        style_cell(c, FONT_HEAD, C_HEADER, Alignment(horizontal="left", vertical="center"), border=False)
        ws.row_dimensions[r].height = 20

        r += 1
        ws.merge_cells(start_row=r, start_column=1, end_row=r + 11, end_column=8)
        c = ws.cell(r, 1, summary)
        c.font = FONT_BODY
        c.alignment = ALIGN_L
        c.fill = PatternFill("solid", fgColor=C_INPUT)
        c.border = BORDER
        for rr in range(r, r + 12):
            ws.row_dimensions[rr].height = 18
        summary_row = r
        r += 12

        # 是否改写
        c = ws.cell(r, 1, "")
        ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=2)
        c = ws.cell(r, 1, "是否自行改写")
        style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
        c = ws.cell(r, 3, "")
        style_cell(c, FONT_BODY, C_INPUT, ALIGN_C)
        dv_rw = DataValidation(type="list", formula1='"否-沿用初稿,是-我重写了"', allow_blank=True)
        ws.add_data_validation(dv_rw)
        dv_rw.add(ws.cell(r, 3))
        rewrite_row = r

        c = ws.cell(r, 4, "总结字数")
        style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
        c = ws.cell(r, 5, f"=LEN(A{summary_row})")
        style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)

        # 打分区
        r += 2
        ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=8)
        c = ws.cell(r, 1, "三、互评打分（给另外 4 位成员，每项 0-25 分，四项合计 100）")
        style_cell(c, FONT_HEAD, C_HEADER, Alignment(horizontal="left", vertical="center"), border=False)
        ws.row_dimensions[r].height = 20

        r += 1
        heads = ["", "被评人", "工作态度", "系统设计", "系统实现", "系统测试", "合计", "一句话理由（具体到事）"]
        for i, h in enumerate(heads, start=1):
            c = ws.cell(r, i, h)
            style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
        ws.row_dimensions[r].height = 26
        head_row = r

        first_data = r + 1
        tgs = targets_of(idx)
        for j, t in enumerate(tgs):
            rr = first_data + j
            ws.cell(rr, 1, "").border = BORDER
            c = ws.cell(rr, 2, MEMBERS[t][1])
            style_cell(c, FONT_BODY, None, ALIGN_C)
            for k in range(3, 7):
                c = ws.cell(rr, k)
                style_cell(c, FONT_BODY, C_INPUT, ALIGN_C)
            c = ws.cell(rr, 7, f"=IF(COUNT(C{rr}:F{rr})=4,SUM(C{rr}:F{rr}),\"\")")
            style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
            c = ws.cell(rr, 8, "")
            style_cell(c, FONT_BODY, C_INPUT, ALIGN_L)
            ws.row_dimensions[rr].height = 32

        last_data = first_data + len(tgs) - 1

        # 分数校验：0-25 整数
        dv_score = DataValidation(type="whole", operator="between", formula1=0, formula2=25,
                                  allow_blank=True, showErrorMessage=True,
                                  errorTitle="分数范围错误", error="请输入 0-25 的整数")
        ws.add_data_validation(dv_score)
        dv_score.add(f"C{first_data}:F{last_data}")

        # 条件格式：满分/异常提示
        ws.conditional_formatting.add(
            f"C{first_data}:F{last_data}",
            CellIsRule(operator="notBetween", formula=["0", "25"],
                       fill=PatternFill("solid", fgColor=C_WARN))
        )

        # 本页完成情况
        r = last_data + 2
        ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=2)
        c = ws.cell(r, 1, "本页完成度")
        style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
        c = ws.cell(r, 3, f'=IF(COUNTA(B{first_data}:B{last_data})-COUNTBLANK(D{name_row})*0=4,"","")')
        r += 1
        checks = [
            ("姓名已填", f'=IF(D{name_row}<>"","是","未填")'),
            ("确认已勾", f'=IF(G{name_row}<>"","是","未填")'),
            ("已打分人数", f'=COUNT(G{first_data}:G{last_data})&"/4"'),
            ("已写理由条数", f'=COUNTA(H{first_data}:H{last_data})&"/4"'),
        ]
        for lb, f in checks:
            c = ws.cell(r, 1, "")
            ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=2)
            c = ws.cell(r, 1, lb)
            style_cell(c, FONT_BODY, C_FORMULA, Alignment(horizontal="left", vertical="center"))
            ws.merge_cells(start_row=r, start_column=3, end_row=r, end_column=4)
            c = ws.cell(r, 3, f)
            style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
            r += 1

        # 可选补充
        r += 1
        ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=8)
        c = ws.cell(r, 1, "四、补充说明（可选）")
        style_cell(c, FONT_HEAD, C_HEADER, Alignment(horizontal="left", vertical="center"), border=False)
        r += 1
        ws.merge_cells(start_row=r, start_column=1, end_row=r + 3, end_column=8)
        c = ws.cell(r, 1, "")
        c.fill = PatternFill("solid", fgColor=C_INPUT)
        c.border = BORDER
        c.alignment = ALIGN_L

        # 页脚说明
        r += 5
        ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=8)
        c = ws.cell(r, 1, "填写说明：橙色格为待填项；灰色格为自动计算不要动。评分口径 23-25 超出预期 / 20-22 符合预期 / 17-19 基本符合 / 14-16 低于预期。")
        style_cell(c, FONT_NOTE, None, Alignment(horizontal="left", vertical="center"), border=False)

        # 冻结
        ws.freeze_panes = ws.cell(head_row + 1, 1)

        # 记录坐标供统计表引用
        ws._meta = dict(name_row=name_row, first=first_data, last=last_data,
                        summary_row=summary_row, rewrite_row=rewrite_row)

    # ================= 统计总表 =================
    ws = wb.create_sheet("0-统计总表", 0)
    ws.sheet_view.showGridLines = False

    for i, w in enumerate([4, 16, 12, 12, 10, 10, 10, 10, 12, 10, 10, 34], start=1):
        ws.column_dimensions[get_column_letter(i)].width = w

    ws.merge_cells("A1:L1")
    c = ws["A1"]; c.value = "组内互评统计总表（自动汇总，勿手改）"
    style_cell(c, FONT_TITLE, C_TITLE, ALIGN_C, border=False)
    ws.row_dimensions[1].height = 30

    # --- 1. 身份 ---
    ws.merge_cells("A3:L3")
    c = ws["A3"]; c.value = "一、成员身份"
    style_cell(c, FONT_HEAD, C_HEADER, Alignment(horizontal="left", vertical="center"), border=False)
    ws.row_dimensions[3].height = 20

    r = 4
    heads = ["", "代号", "角色", "真实姓名", "确认", "负责范围"]
    for i, h in enumerate(heads, start=1):
        c = ws.cell(r, i, h)
        style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
    identity_rows = []
    for idx, (short, code, role, scope, _) in enumerate(MEMBERS):
        rr = r + 1 + idx
        ws.cell(rr, 1, "").border = BORDER
        c = ws.cell(rr, 2, code); style_cell(c, FONT_BODY, None, ALIGN_C)
        c = ws.cell(rr, 3, role); style_cell(c, FONT_BODY, None, ALIGN_C)
        c = ws.cell(rr, 4, f"='{idx+1}-{short}'!D{member_sheets[idx]._meta['name_row']}")
        style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
        c = ws.cell(rr, 5, f"='{idx+1}-{short}'!G{member_sheets[idx]._meta['name_row']}")
        style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
        ws.merge_cells(start_row=rr, start_column=6, end_row=rr, end_column=12)
        c = ws.cell(rr, 6, scope); style_cell(c, FONT_BODY, None, Alignment(horizontal="left", vertical="center"))
        identity_rows.append(rr)
    r = r + 6

    # --- 2. 互评明细矩阵 ---
    r += 1
    ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=12)
    c = ws.cell(r, 1, "二、互评明细（行=评分人，列=被评人，共 20 份）")
    style_cell(c, FONT_HEAD, C_HEADER, Alignment(horizontal="left", vertical="center"), border=False)
    ws.row_dimensions[r].height = 20

    r += 1
    heads = ["", "评分人 \\ 被评人"] + [m[1] for m in MEMBERS]
    for i, h in enumerate(heads, start=1):
        c = ws.cell(r, i, h)
        style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
    ws.row_dimensions[r].height = 32
    matrix_head = r

    matrix_rows = {}
    for by in range(5):
        rr = matrix_head + 1 + by
        ws.cell(rr, 1, "").border = BORDER
        c = ws.cell(rr, 2, MEMBERS[by][1]); style_cell(c, FONT_BODY, None, ALIGN_C)
        meta = member_sheets[by]._meta
        tgs = targets_of(by)
        for col, t in enumerate(range(5)):
            cell = ws.cell(rr, 3 + col)
            if t == by:
                cell.value = "—"
                style_cell(cell, FONT_SMALL, "EDEDED", ALIGN_C)
            else:
                pos = tgs.index(t)
                src_row = meta["first"] + pos
                # 引用个人表该人这一行的合计（第 7 列）
                cell.value = f"=IF('{by+1}-{MEMBERS[by][0]}'!G{src_row}=\"\",\"\",'{by+1}-{MEMBERS[by][0]}'!G{src_row})"
                style_cell(cell, FONT_BODY, C_FORMULA, ALIGN_C)
        matrix_rows[by] = rr
    r = matrix_head + 6

    # --- 3. 每人汇总 ---
    r += 1
    ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=12)
    c = ws.cell(r, 1, "三、每人收到的评分汇总")
    style_cell(c, FONT_HEAD, C_HEADER, Alignment(horizontal="left", vertical="center"), border=False)
    ws.row_dimensions[r].height = 20

    r += 1
    heads = ["", "成员", "收到份数", "工作态度均分", "系统设计均分",
             "系统实现均分", "系统测试均分", "平均总分", "最高", "最低", "贡献系数", "备注"]
    for i, h in enumerate(heads, start=1):
        c = ws.cell(r, i, h)
        style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
    ws.row_dimensions[r].height = 32
    summary_head = r

    for t in range(5):
        rr = summary_head + 1 + t
        ws.cell(rr, 1, "").border = BORDER
        c = ws.cell(rr, 2, MEMBERS[t][1]); style_cell(c, FONT_BODY, None, ALIGN_C)

        # 该列在矩阵中的字母
        col_letter = get_column_letter(3 + t)
        rng = f"{col_letter}{matrix_head+1}:{col_letter}{matrix_head+5}"

        c = ws.cell(rr, 3, f'=COUNT({rng})&"/4"'); style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
        # 各维度均分：从被评人所在个人表提取
        # 被评人 t 的维度 j 出现在「评分人 by 的个人表」里，行号 = first + pos(target)
        dim_formulas = []
        for j in range(4):
            parts = []
            for by in range(5):
                if by == t:
                    continue
                tgs = targets_of(by)
                pos = tgs.index(t)
                src = member_sheets[by]._meta["first"] + pos
                col = get_column_letter(3 + j)
                parts.append(f"'{by+1}-{MEMBERS[by][0]}'!{col}{src}")
            dim_formulas.append("=AVERAGE(" + ",".join(parts) + ")")
        for j, f in enumerate(dim_formulas):
            c = ws.cell(rr, 4 + j, f)
            style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
            c.number_format = "0.0"
        c = ws.cell(rr, 8, f"=AVERAGE({rng})")
        style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
        c.number_format = "0.0"
        c = ws.cell(rr, 9, f"=IF(COUNT({rng})=0,\"\",MAX({rng}))"); style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
        c = ws.cell(rr, 10, f"=IF(COUNT({rng})=0,\"\",MIN({rng}))"); style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
        c = ws.cell(rr, 11, ""); style_cell(c, FONT_BODY, C_INPUT, ALIGN_C)
        c = ws.cell(rr, 12, ""); style_cell(c, FONT_BODY, None, ALIGN_L)

    contrib_first = summary_head + 1
    contrib_last = summary_head + 5

    r = contrib_last + 1
    c = ws.cell(r, 2, "贡献系数合计")
    style_cell(c, FONT_HEAD, C_HEADER, ALIGN_C)
    c = ws.cell(r, 3, f"=SUM(K{contrib_first}:K{contrib_last})")
    style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
    c.number_format = "0.00"
    c = ws.cell(r, 4, f'=IF(COUNT(K{contrib_first}:K{contrib_last})<5,"未填齐",'
                      f'IF(ABS(SUM(K{contrib_first}:K{contrib_last})/5-1)<0.0001,"通过（平均=1.0）","不通过，平均="&TEXT(SUM(K{contrib_first}:K{contrib_last})/5,"0.000")))')
    style_cell(c, FONT_BODY, C_FORMULA, Alignment(horizontal="left", vertical="center"))
    ws.merge_cells(start_row=r, start_column=4, end_row=r, end_column=7)
    contrib_check_row = r

    # --- 4. 总体完成度 ---
    r += 2
    ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=12)
    c = ws.cell(r, 1, "四、总体完成度")
    style_cell(c, FONT_HEAD, C_HEADER, Alignment(horizontal="left", vertical="center"), border=False)
    ws.row_dimensions[r].height = 20

    r += 1
    # 姓名数
    name_refs = ",".join([f"'{i+1}-{MEMBERS[i][0]}'!D{member_sheets[i]._meta['name_row']}" for i in range(5)])
    conf_refs = ",".join([f"'{i+1}-{MEMBERS[i][0]}'!G{member_sheets[i]._meta['name_row']}" for i in range(5)])
    metrics = [
        ("真实姓名已填", f"=COUNTA({name_refs})&\" / 5\""),
        ("本人确认已勾", f'=COUNTIF(({conf_refs}),"是")&" / 5"'),
        ("个人总结已填", f"={','.join([f'IF(LEN(\'{i+1}-{MEMBERS[i][0]}\'!A{member_sheets[i]._meta['summary_row']})>50,1,0)' for i in range(5)])},5,0"),
        ("互评已打分", f"=COUNT(C{matrix_head+1}:G{matrix_head+5})&\" / 20\""),
        ("贡献系数已填", f"=COUNT(K{contrib_first}:K{contrib_last})&\" / 5\""),
    ]
    for lb, f in metrics:
        c = ws.cell(r, 2, lb)
        style_cell(c, FONT_BODY, None, Alignment(horizontal="left", vertical="center"))
        ws.merge_cells(start_row=r, start_column=2, end_row=r, end_column=3)
        c = ws.cell(r, 4, f)
        style_cell(c, FONT_BODY, C_FORMULA, ALIGN_C)
        r += 1

    r += 1
    ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=12)
    c = ws.cell(r, 1, "填写说明：橙色格为待填项；浅蓝格为公式结果，不要手改。个人表里填完，这里会自动更新。")
    style_cell(c, FONT_NOTE, None, Alignment(horizontal="left", vertical="center"), border=False)

    ws.freeze_panes = "C5"

    wb.save(OUT)
    print("saved:", OUT)
    return OUT


if __name__ == "__main__":
    build()
