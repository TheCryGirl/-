package com.mall.order.mapper;

import com.mall.order.entity.AuditLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogMapper {

    int insert(AuditLog auditLog);

    List<AuditLog> selectPage(@Param("operatorId") String operatorId,
                              @Param("action") String action,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              @Param("offset") int offset,
                              @Param("size") int size);

    long countPage(@Param("operatorId") String operatorId,
                   @Param("action") String action,
                   @Param("from") LocalDateTime from,
                   @Param("to") LocalDateTime to);
}
