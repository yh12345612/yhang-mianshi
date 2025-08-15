package com.yhang.springbootinit.service.impl;

import static com.yhang.springbootinit.constant.UserConstant.USER_LOGIN_STATE;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yhang.springbootinit.constant.RedisConstant;
import com.yhang.springbootinit.exception.BusinessException;
import com.yhang.springbootinit.exception.ThrowUtils;
import com.yhang.springbootinit.saToken.DeviceUtil;
import com.yhang.springbootinit.service.UserService;
import com.yhang.springbootinit.common.ErrorCode;
import com.yhang.springbootinit.constant.CommonConstant;
import com.yhang.springbootinit.mapper.UserMapper;
import com.yhang.springbootinit.model.dto.user.UserQueryRequest;
import com.yhang.springbootinit.model.entity.User;
import com.yhang.springbootinit.model.enums.UserRoleEnum;
import com.yhang.springbootinit.model.vo.LoginUserVO;
import com.yhang.springbootinit.model.vo.UserVO;
import com.yhang.springbootinit.utils.SqlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * 用户服务实现
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值，混淆密码
     */
    public static final String SALT = "yupi";
    @Resource
    private RedissonClient redissonClient;
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        System.out.println(request.getSession().getId());
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        StpUtil.login(user.getId(), DeviceUtil.getRequestDevice(request));
        StpUtil.getSession().set(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }



    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        System.out.println(request.getSession().getId());
        // 先判断是否已登录
        Object userObjId = StpUtil.getLoginIdDefaultNull();
        ThrowUtils.throwIf(userObjId==null,ErrorCode.PARAMS_ERROR,"没有登录");
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        User currentUser = this.getById((String) userObjId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        // 先判断是否已登录
        // 先判断是否已登录
        Object userObjId = StpUtil.getLoginIdDefaultNull();
        ThrowUtils.throwIf(userObjId==null,ErrorCode.PARAMS_ERROR,"没有登录");
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        return this.getById((String) userObjId);
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObjId = StpUtil.getLoginIdDefaultNull();
        ThrowUtils.throwIf(userObjId==null,ErrorCode.PARAMS_ERROR,"没有登录");
        User user =getById((String) userObjId);
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObjId = StpUtil.getLoginIdDefaultNull();
        ThrowUtils.throwIf(userObjId==null,ErrorCode.PARAMS_ERROR,"没有登录");
        // 移除登录态
        StpUtil.logout(userObjId,DeviceUtil.getRequestDevice(request));
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public boolean addUserSignIn(long userId) {
        int year = LocalDateTime.now().getYear();
        String key = RedisConstant.getUserSignIn(year, userId);
        RBitSet bitSet = redissonClient.getBitSet(key);
        boolean signIn = bitSet.get(LocalDateTime.now().getDayOfYear());
        if(!signIn)
        {
            bitSet.set(LocalDateTime.now().getDayOfYear(),true);
        }
        return true;
    }

    @Override
    public List<Integer> getUserSignRecord(long userId, Integer year) {
        if(year==null)
        {
            year=LocalDateTime.now().getYear();
        }
        String key = RedisConstant.getUserSignIn(year, userId);
        RBitSet bitSet = redissonClient.getBitSet(key);
        //把redis的结果转化成java自带的数据结果，避免每次循环都要访问redis
        BitSet bitSet1 = bitSet.asBitSet();
        //使用LinkedHashMap可以实现有序排列
        List<Integer> list=new ArrayList<>();
        int index=bitSet1.nextSetBit(0);
        while (index!=-1)
        {
            list.add(index);
            //查找下一次签到的日期
            index=bitSet1.nextSetBit(index+1);
        }
        return list;
    }
}
