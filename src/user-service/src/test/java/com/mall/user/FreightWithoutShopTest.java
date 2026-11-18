package com.mall.user;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.dto.request.FreightConfigRequest;
import com.mall.user.dto.response.ShopResponse;
import com.mall.user.service.FreightService;
import com.mall.user.service.ShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 未入驻卖家配置运费（#136 + #144）。
 *
 * <p>#144 起开店必须走入驻审核，`ShopService.getMine` 不再自动建店。
 * 没有店铺就没有地方挂运费模板，此时必须返回可理解的 `20007`（提示先走入驻申请），
 * 不能让 `shop_id` 以 null 落库、把数据库的非空约束错误抛给前端。
 */
@SpringBootTest
@ActiveProfiles("test")
class FreightWithoutShopTest {

    @Autowired
    private FreightService freightService;

    @MockBean
    private ShopService shopService;

    @Test
    void savingFreightWithoutShopAsksForApplication() {
        when(shopService.getMine(anyString())).thenReturn(new ShopResponse());

        assertThatThrownBy(() -> freightService.saveMine("U-NO-SHOP", request()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("入驻申请")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    void readingFreightWithoutShopAsksForApplication() {
        when(shopService.getMine(anyString())).thenReturn(new ShopResponse());

        assertThatThrownBy(() -> freightService.getMine("U-NO-SHOP"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("入驻申请")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    /** #144 的 ShopService 自己抛 20007 时，原样透传成带操作提示的文案。 */
    @Test
    void shopServiceNotFoundIsRethrownWithActionableHint() {
        when(shopService.getMine(anyString()))
                .thenThrow(new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在"));

        assertThatThrownBy(() -> freightService.getMine("U-NO-SHOP"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先完成店铺入驻申请")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    /** 内部查询仍按「未配置模板」兜底：下单链路不能因为店铺缺记录被打断。 */
    @Test
    void internalLookupStaysLenientForUnknownSeller() {
        var response = freightService.getBySellerId("U-NOT-EXISTS");

        assertThat(response.getTemplateId()).isNull();
        assertThat(response.getFreeThreshold()).isZero();
        assertThat(response.getRules()).isEmpty();
    }

    private static FreightConfigRequest request() {
        FreightConfigRequest request = new FreightConfigRequest();
        request.setName("全国运费");
        request.setFreeThreshold(0L);
        request.setRules(List.of());
        return request;
    }
}
