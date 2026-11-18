package com.mall.payment.mapper;

import com.mall.payment.entity.CommissionRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 佣金费率规则数据访问层（#144 第二块）。规则量级小，全量读取后在内存中回退。
 */
@Mapper
public interface CommissionRuleMapper {

    List<CommissionRule> selectAll();
}
