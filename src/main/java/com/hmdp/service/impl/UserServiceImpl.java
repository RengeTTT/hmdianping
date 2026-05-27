package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code);
        stringRedisTemplate.expire(RedisConstants.LOGIN_CODE_KEY + phone, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送验证码
        log.info("发送验证码成功：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误！");
        }
        // 校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (code == null || cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 查找用户是否在数据库
        User user = query().eq("phone", phone).one();
        log.info("info:{}", user);
        if (user == null) {
            // 不存在则新建用户并插入
            log.info("注册：{}", loginForm);
            user = createUserWithPhone(loginForm);
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        // 为当前登录用户设置token
        String token = UUID.randomUUID().toString();
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true));
        Map<String, String> userMap = new HashMap<>();
        stringObjectMap.forEach((k, v) -> {userMap.put(k, v.toString());});
        // 缓存用户信息并设置最大时间
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public void logOut(HttpServletRequest httpRequest) {
        String token = httpRequest.getHeader("authorization");
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);

    }

    private User createUserWithPhone(LoginFormDTO loginForm) {

        User user = User.builder()
                .phone(loginForm.getPhone())
                .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        save(user);
        return user;
    }
}
