package org.example.ssm1.Service;

import org.example.ssm1.Entity.User;
import org.example.ssm1.Mapper.UserMapper;
import org.example.ssm1.Util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    public User getUserById(String userId) {
        return userMapper.selectById(userId);
    }

    public Map<String, Object> login(String userId, String password) {
        User user = userMapper.selectById(userId);
        if (user != null && user.getPassword().equals(password)) {
            String token = JwtUtil.generateToken(user.getUserId());
            Map<String, Object> response = new HashMap<>();
            response.put("user", user);
            response.put("token", token);
            return response;
        }
        return null;
    }

    public String registerUser(User user) {
        if (userMapper.selectById(user.getUserId()) != null) {
            return "id已存在";
        }
        if (userMapper.selectByName(user.getName()) != null) {
            return "用户名已存在";
        }
        int result = userMapper.insert(user);
        if (result > 0) return "注册成功";
        return "注册失败:未知错误,请联系管理员";
    }

    public int createUser(User user) {
        return userMapper.insert(user);
    }

    public int updateUser(User user) {
        return userMapper.update(user);
    }

    public int deleteUser(String userId) {
        return userMapper.deleteById(userId);
    }

    public User getUserByName(String name) {
        return userMapper.selectByName(name);
    }

    public String getNameByUserId(String userId) {
        return userMapper.selectNameById(userId);
    }
}

