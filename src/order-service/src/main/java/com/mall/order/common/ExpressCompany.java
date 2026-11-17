package com.mall.order.common;

/**
 * 快递公司枚举（任务 #138）。
 *
 * <p>发货时只接受这里的编码，不接受自由文本：前端下拉选择、后端按编码校验，
 * 避免出现「顺丰」「顺丰速运」「SF」三种写法混在同一张表里，导致后续按快递公司统计失效。
 *
 * <p>{@code code} 落库（{@code order.express_company}），{@code label} 只用于展示，
 * 由接口回传，前端不再自行维护一份映射。
 */
public enum ExpressCompany {

    SF("顺丰速运"),
    JD("京东物流"),
    ZTO("中通快递"),
    YTO("圆通速递"),
    STO("申通快递"),
    YUNDA("韵达速递"),
    JTSD("极兔速递"),
    EMS("中国邮政EMS");

    private final String label;

    ExpressCompany(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 按编码解析，忽略大小写与首尾空白；不认识返回 null。 */
    public static ExpressCompany parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        for (ExpressCompany company : values()) {
            if (company.name().equals(value)) {
                return company;
            }
        }
        return null;
    }

    /** 编码转展示名；未知编码原样返回，不吞成空串。 */
    public static String labelOf(String code) {
        ExpressCompany company = parse(code);
        return company == null ? code : company.getLabel();
    }
}
