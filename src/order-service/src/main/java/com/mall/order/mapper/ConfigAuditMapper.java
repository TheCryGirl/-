package com.mall.order.mapper;

import com.mall.order.entity.ConfigAudit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 平台配置变更留痕（任务 #43） */
@Mapper
public interface ConfigAuditMapper {

    @Insert("INSERT INTO config_audit(operator_id, data_id, config_key, old_value, new_value, created_at) "
            + "VALUES(#{operatorId}, #{dataId}, #{configKey}, #{oldValue}, #{newValue}, NOW())")
    int insert(@Param("operatorId") String operatorId,
               @Param("dataId") String dataId,
               @Param("configKey") String configKey,
               @Param("oldValue") String oldValue,
               @Param("newValue") String newValue);

    @Select("SELECT id, operator_id AS operatorId, data_id AS dataId, config_key AS configKey, "
            + "old_value AS oldValue, new_value AS newValue, "
            + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
            + "FROM config_audit ORDER BY id DESC LIMIT #{limit}")
    List<ConfigAudit> selectRecent(@Param("limit") int limit);
}
