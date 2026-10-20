# -*- coding: utf-8 -*-
"""生成用户故事地图（横轴=用户活动骨架，纵轴=按 Sprint 切分的任务层）
图内不带图题，图题由 Word 图注给出，避免重复。
"""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "images")
os.makedirs(OUT, exist_ok=True)

FONT_DIR = r"C:\Windows\Fonts"


def F(name, size):
    for cand in (name, "msyh.ttc"):
        p = os.path.join(FONT_DIR, cand)
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()


C_BACKBONE = "#1F4E79"   # 骨架列头
C_S1 = "#D6E4F0"         # Sprint 1
C_S2 = "#E2EFDA"         # Sprint 2
C_S3 = "#FFF2CC"         # Sprint 3
C_S4 = "#FBE2D5"         # Sprint 4
C_LABEL = "#F2F2F2"
C_TEXT = "#1F2328"
C_LINE = "#8FA8BF"
C_MUTED = "#5B6B7B"


def rounded(d, box, r, fill, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def draw_story_map(fname, subtitle, columns, tiers, legend):
    """columns: [(名称, 说明)]
    tiers: [(层名, 副标题, 颜色, [[col0 卡...], [col1 卡...], ...])]"""
    f_sub = F("msyh.ttc", 22)
    f_col = F("msyhbd.ttc", 26)
    f_colsub = F("msyh.ttc", 18)
    f_card = F("msyh.ttc", 22)
    f_tier = F("msyhbd.ttc", 23)
    f_tiers = F("msyh.ttc", 17)
    f_leg = F("msyh.ttc", 18)

    W = 2400
    MARGIN = 40
    LABEL_W = 250
    NCOL = len(columns)
    COL_GAP = 20
    usable = W - 2 * MARGIN - LABEL_W
    COL_W = (usable - COL_GAP * (NCOL - 1)) // NCOL
    CARD_W = COL_W - 10
    CARD_H = 78
    CARD_GAP = 12
    EMPTY_H = 52

    row_heights = []
    for _, _, _, grid in tiers:
        mx = max(len(c) for c in grid) if any(grid) else 1
        n = max(mx, 1)
        row_heights.append(26 + n * CARD_H + (n - 1) * CARD_GAP + 18)

    y = 0
    H = 30 + 40 + 30 + 96 + 22 + sum(row_heights) + 30 + 60
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)

    # 副标题（无图题）
    y = 30
    d.text((MARGIN, y), subtitle, font=f_sub, fill=C_MUTED)
    y += 40

    # 列头（骨架）
    x0 = MARGIN + LABEL_W
    for i, (cname, csub) in enumerate(columns):
        x = x0 + i * (COL_W + COL_GAP)
        rounded(d, [x, y, x + COL_W, y + 96], 10, C_BACKBONE)
        tw = d.textlength(cname, font=f_col)
        d.text((x + (COL_W - tw) / 2, y + 18), cname, font=f_col, fill="white")
        tw = d.textlength(csub, font=f_colsub)
        d.text((x + (COL_W - tw) / 2, y + 58), csub, font=f_colsub, fill="#C9D9E8")
    y += 96 + 22

    # 各层
    for ti, (tname, tsub, tcolor, grid) in enumerate(tiers):
        rh = row_heights[ti]
        rounded(d, [MARGIN, y, MARGIN + LABEL_W - 20, y + rh - 14], 10,
                C_LABEL, C_LINE, 1)
        d.text((MARGIN + 18, y + 18), tname, font=f_tier, fill=C_BACKBONE)
        d.text((MARGIN + 18, y + 52), tsub, font=f_tiers, fill=C_MUTED)

        for ci, cards in enumerate(grid):
            x = x0 + ci * (COL_W + COL_GAP) + 5
            cy = y
            if not cards:
                rounded(d, [x, cy, x + CARD_W, cy + EMPTY_H], 8, "#FAFAFA", C_LINE, 1)
                tw = d.textlength("（本迭代无）", font=f_tiers)
                d.text((x + (CARD_W - tw) / 2, cy + 15), "（本迭代无）",
                       font=f_tiers, fill="#A8B4C0")
                continue
            for card in cards:
                rounded(d, [x, cy, x + CARD_W, cy + CARD_H], 8, tcolor, C_LINE, 1)
                lines = card.split("\n")
                th = len(lines) * 26
                ty = cy + (CARD_H - th) / 2 + 2
                for ln in lines:
                    tw = d.textlength(ln, font=f_card)
                    d.text((x + (CARD_W - tw) / 2, ty), ln, font=f_card, fill=C_TEXT)
                    ty += 26
                cy += CARD_H + CARD_GAP
        y += rh

    # 图例
    y += 8
    lx = MARGIN
    for text, color in legend:
        rounded(d, [lx, y + 4, lx + 34, y + 30], 6, color, C_LINE, 1)
        d.text((lx + 44, y + 5), text, font=f_leg, fill=C_MUTED)
        lx += 44 + d.textlength(text, font=f_leg) + 46

    img = img.crop((0, 0, W, y + 54))
    img.save(os.path.join(OUT, fname), dpi=(300, 300))
    print("saved", fname, img.size)


LEGEND = [("Sprint 1（第 1–2 周）", C_S1), ("Sprint 2（第 3–4 周）", C_S2),
          ("Sprint 3（第 5–6 周）", C_S3), ("Sprint 4（第 7–8 周）", C_S4)]

# ============ 图 1-1 买家用户故事地图 ============
draw_story_map(
    "fig1-1_story_map_buyer.png",
    "横轴＝用户活动骨架（按买家购物旅程自左向右）；纵轴＝按 Sprint 切分的任务层，越靠上越先交付",
    [
        ("逛与选", "发现商品"),
        ("加购", "形成购买意愿"),
        ("下单结算", "确认交易"),
        ("支付履约", "完成付款"),
        ("售后与复购", "交易闭环"),
    ],
    [
        ("Sprint 1", "MVP 交易闭环", C_S1, [
            ["浏览商品列表", "查看商品详情"],
            ["加入购物车", "查看购物车"],
            ["填写收货地址", "提交订单"],
            ["模拟支付", "查看支付结果"],
            ["我的订单列表"],
        ]),
        ("Sprint 2", "交易正确性", C_S2, [
            ["关键词搜索", "按分类筛选"],
            ["修改数量", "删除购物车项"],
            ["库存预占", "订单超时取消"],
            ["支付回调幂等", "支付结果主动查询"],
            ["取消订单", "确认收货"],
        ]),
        ("Sprint 3", "体验与可观测", C_S3, [
            ["按销量排序"],
            ["库存不足提示", "失效商品标记"],
            ["订单金额明细"],
            ["订单状态实时刷新"],
            ["退款进度查询"],
        ]),
        ("Sprint 4", "售后与运营治理", C_S4, [
            ["商品评价晒单"],
            ["到货提醒"],
            ["发票申请"],
            ["账单与流水核对"],
            ["售后仲裁申请", "评价晒单入口"],
        ]),
    ],
    LEGEND,
)

# ============ 图 1-2 卖家与管理员用户故事地图 ============
draw_story_map(
    "fig1-2_story_map_seller.png",
    "支撑角色不参与买家主旅程，单独成图，避免买家骨架被稀释；纵轴同样按 Sprint 切分",
    [
        ("商品管理", "供给上架"),
        ("订单处理", "履约发货"),
        ("运营与售后", "平台治理"),
    ],
    [
        ("Sprint 1", "基础供给", C_S1, [
            ["发布商品"],
            ["查看待处理订单"],
            ["分类管理"],
        ]),
        ("Sprint 2", "履约能力", C_S2, [
            ["商品上下架", "库存调整"],
            ["确认发货", "订单批量查询"],
            ["退款审核"],
        ]),
        ("Sprint 3", "数据与稳定", C_S3, [
            ["商品信息修改"],
            ["物流信息维护"],
            ["销量汇总看板"],
        ]),
        ("Sprint 4", "治理与运营", C_S4, [
            ["违规商品整改"],
            ["异常订单处理"],
            ["交易流水导出", "用户管理"],
        ]),
    ],
    LEGEND,
)