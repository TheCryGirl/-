package com.mall.user.mapper;

import com.mall.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户数据访问层。
 */
@Mapper
public interface UserMapper {

    int insert(User user);

    User selectById(@Param("userId") String userId);

    User selectByAccount(@Param("account") String account);

    User selectByNickname(@Param("nickname") String nickname);

    int updateById(User user);

    int updateStatusById(@Param("userId") String userId,
                         @Param("status") String status);

    int updateRoleById(@Param("userId") String userId,
                       @Param("role") String role,
                       @Param("updatedBy") String updatedBy);

    List<User> selectPage(@Param("role") String role,
                          @Param("status") String status,
                          @Param("keyword") String keyword,
                          @Param("offset") int offset,
                          @Param("size") int size);

    long countPage(@Param("role") String role,
                   @Param("status") String status,
                   @Param("keyword") String keyword);
}
