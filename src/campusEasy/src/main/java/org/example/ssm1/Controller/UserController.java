package org.example.ssm1.Controller;

import org.example.ssm1.Entity.User;
import org.example.ssm1.Mapper.UserMapper;
import org.example.ssm1.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable String userId) {
        User user = userService.getUserById(userId);
        if (user != null) {return ResponseEntity.ok(user);}
        else {return ResponseEntity.notFound().build();}
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody User loginRequest) {
        Map<String, Object> response = userService.login(loginRequest.getUserId(), loginRequest.getPassword());
        if (response != null) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping
    public ResponseEntity<String> createUser(@RequestBody User user) {
        String result = userService.registerUser(user);
        if ("注册成功".equals(result)) {
            return ResponseEntity.ok(result);
        } else if ("id已存在".equals(result) || "用户名已存在".equals(result)) {
            return ResponseEntity.status(400).body(result);
        } else {
            return ResponseEntity.status(500).body(result);
        }
    }


    @PutMapping
    public ResponseEntity<Integer> updateUser(@RequestBody User user) {
        int result = userService.updateUser(user);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Integer> deleteUser(@PathVariable String userId) {
        int result = userService.deleteUser(userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/getname/{userId}")
    public ResponseEntity<String> getUserName(@PathVariable String userId) {
        String name = userService.getNameByUserId(userId);
        return ResponseEntity.ok(name);
    }
}
