package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result follow(Long followId, Boolean isFollow) {
        if (followId == null || isFollow == null) {
            return Result.fail("参数错误");
        }
        // 获取当前用户的id
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOW_KEY + userId;

        if (isFollow) {
            // 如果还没关注，则新增关注
            Follow follow = Follow.builder().followUserId(followId).userId(userId).build();
            boolean save = save(follow);
            if (save) {
                // 插入成功则添加到集合中
                stringRedisTemplate.opsForSet().add(key, followId.toString());
            }
        } else {
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followId));
            if (remove) {
                stringRedisTemplate.opsForSet().remove(key, followId.toString());
            }
        }
        return Result.ok("操作成功");
    }

    @Override
    public Result isFollow(Long followUserId) {
        if (followUserId == null) {
            return Result.fail("参数错误");
        }
        Long userId = UserHolder.getUser().getId();
        Long cnt = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(cnt > 0);
    }

    @Override
    public Result commonLikes(Long followId) {
        Long userId = UserHolder.getUser().getId();
        if (userId == null || followId == null) {
            return Result.fail("参数错误");
        }
        // 得到两个用户的集合求交集
        String key1 = RedisConstants.FOLLOW_KEY + userId;
        String key2 = RedisConstants.FOLLOW_KEY + followId;

        List<Long> list = stringRedisTemplate.opsForSet().intersect(key1, key2).stream().map(Long::valueOf).toList();
        if (list == null) {
            return Result.ok(Collections.EMPTY_LIST);
        }
        List<UserDTO> users = userService.listByIds(list).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).toList();
        return Result.ok(users);
    }
}
