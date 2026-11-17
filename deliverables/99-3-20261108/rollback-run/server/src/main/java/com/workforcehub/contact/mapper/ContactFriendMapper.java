package com.workforcehub.contact.mapper;

import com.workforcehub.contact.domain.ContactFriend;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContactFriendMapper {

    /**
     * 列出当前用户的好友（按好友真实姓名排序）
     */
    List<OrgUserSummaryResponse> selectFriends(@Param("userId") Long userId);

    /**
     * 列出当前用户收到的待处理好友申请
     */
    List<OrgUserSummaryResponse> selectIncomingRequests(@Param("userId") Long userId);

    /**
     * 查询好友关系（含软删除行，用于复活判断）
     */
    ContactFriend selectFriend(@Param("userId") Long userId, @Param("friendUserId") Long friendUserId);

    /**
     * 新增好友关系
     */
    int insertFriend(@Param("row") ContactFriend row);

    /**
     * 物理删除好友关系
     */
    int deleteFriend(@Param("userId") Long userId, @Param("friendUserId") Long friendUserId);

    /**
     * 更新好友关系状态
     */
    int updateStatus(@Param("userId") Long userId,
                     @Param("friendUserId") Long friendUserId,
                     @Param("status") String status,
                     @Param("operatorId") Long operatorId);

    /**
     * 更新好友备注
     */
    int updateRemark(@Param("userId") Long userId,
                     @Param("friendUserId") Long friendUserId,
                     @Param("remark") String remark);
}
