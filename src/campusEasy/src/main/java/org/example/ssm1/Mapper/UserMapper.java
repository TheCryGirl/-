package org.example.ssm1.Mapper;

import org.apache.ibatis.annotations.*;
import org.example.ssm1.Entity.User;

@Mapper
public interface UserMapper {

    @Select("SELECT user_id as userId, name, age, sex, address, phone, password, create_time as createTime FROM `user` WHERE user_id = #{userId}")
    User selectById(String userId);

    @Insert("INSERT INTO `user` (user_id, name, age, sex, address, phone, password, create_time) VALUES (#{userId}, #{name}, #{age}, #{sex}, #{address}, #{phone}, #{password}, #{createTime})")
    int insert(User user);

    @Update("UPDATE `user` SET name = #{name}, age = #{age}, sex = #{sex}, address = #{address}, phone = #{phone}, password = #{password} WHERE user_id = #{userId}")
    int update(User user);

    @Delete("DELETE FROM `user` WHERE user_id = #{userId}")
    int deleteById(String userId);

    @Select("SELECT user_id as userId, name, age, sex, address, phone, password, create_time as createTime FROM `user` WHERE name = #{name}")
    User selectByName(String name);

    @Select("SELECT name FROM `user` WHERE user_id = #{userId}")
    String selectNameById(String userId);
}
