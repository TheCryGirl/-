package com.mall.user.mapper;

import com.mall.user.entity.FreightRule;
import com.mall.user.entity.FreightTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 运费模板与规则（#136）。
 */
@Mapper
public interface FreightMapper {

    FreightTemplate selectTemplateByShopId(@Param("shopId") String shopId);

    List<FreightRule> selectRulesByTemplateId(@Param("templateId") String templateId);

    int insertTemplate(FreightTemplate template);

    int updateTemplate(FreightTemplate template);

    int softDeleteRulesByTemplateId(@Param("templateId") String templateId);

    int insertRule(FreightRule rule);
}
