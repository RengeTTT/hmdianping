package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.System.currentTimeMillis;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        // 查询博客
        queryBlogUser(blog);
        // 查询博客是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        if (id == null || id <= 0) {
            return Result.fail("id不存在");
        }
        // 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        // 查看当前用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        boolean isLiked = stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null;
        // 如果已经点赞则取消点赞，未点赞则点赞
        if (Boolean.TRUE.equals(isLiked)) {
            boolean isUpdated = this.update().setIncrBy("liked", -1).eq("id", id).update();
            if (isUpdated) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } else {
            boolean isUpdated = this.update().setIncrBy("liked", 1).eq("id", id).update();
            if (isUpdated) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), currentTimeMillis());
            }
        }
        // 返回结果
        return Result.ok();
    }

    @Override
    public Result queryBlogTopLikes(Long id) {
        // 利用redis查询前5个点赞的用用户
        if (id == null || id <= 0) {
            return Result.fail("id不存在");
        }
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 查询用户id
        List<Long> userList = stringRedisTemplate.opsForZSet().range(key, 0, 4)
                .stream().map(Long :: valueOf).toList();
        if (userList == null || userList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        String orderIds = StrUtil.join(",", userList);
         // 查询前五个用户的信息
        List<UserDTO> userDTOS = userService
                .query().in("id", userList).last("order by field(" +orderIds+ ")").list() // 这里需要手动指定返回的次序
                .stream()
                .map(user -> BeanUtil.toBean(user, UserDTO.class)).toList();
        return Result.ok(userDTOS);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    private void isBlogLiked(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Boolean isLiked = stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null;
        blog.setIsLike(Boolean.TRUE.equals(isLiked));

    }
}
