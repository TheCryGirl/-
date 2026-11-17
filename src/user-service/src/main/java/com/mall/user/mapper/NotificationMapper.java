package com.mall.user.mapper;

import com.mall.user.entity.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 站内通知数据访问层（#139）。 */
@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO notification (notification_id, user_id, type, title, content, biz_id, biz_type, read_at, created_at) "
            + "VALUES (#{notificationId}, #{userId}, #{type}, #{title}, #{content}, #{bizId}, #{bizType}, #{readAt}, #{createdAt})")
    int insert(Notification notification);

    @Select("<script>SELECT * FROM notification WHERE user_id = #{userId} "
            + "<if test='unreadOnly == 1'>AND read_at IS NULL </if>"
            + "ORDER BY created_at DESC, notification_id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Notification> selectPage(@Param("userId") String userId,
                                  @Param("unreadOnly") int unreadOnly,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM notification WHERE user_id = #{userId} "
            + "<if test='unreadOnly == 1'>AND read_at IS NULL</if></script>")
    long countPage(@Param("userId") String userId, @Param("unreadOnly") int unreadOnly);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND read_at IS NULL")
    long countUnread(@Param("userId") String userId);

    @Update("UPDATE notification SET read_at = NOW() WHERE notification_id = #{notificationId} "
            + "AND user_id = #{userId} AND read_at IS NULL")
    int markRead(@Param("notificationId") String notificationId, @Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM notification WHERE notification_id = #{notificationId} AND user_id = #{userId}")
    long countOwned(@Param("notificationId") String notificationId, @Param("userId") String userId);

    @Update("UPDATE notification SET read_at = NOW() WHERE user_id = #{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") String userId);
}