package org.example.ssm1;

import org.example.ssm1.Entity.Product;
import org.example.ssm1.Entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RedisDataTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testAddStringData() {
        System.out.println("=== 测试添加字符串数据到Redis ===");

        // 添加简单的键值对
        stringRedisTemplate.opsForValue().set("welcome_message", "欢迎使用校园二手交易平台！", 300, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set("system_status", "running", 300, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set("current_time", LocalDateTime.now().toString(), 60, TimeUnit.SECONDS);

        // 验证数据是否成功添加
        String welcomeMsg = stringRedisTemplate.opsForValue().get("welcome_message");
        String systemStatus = stringRedisTemplate.opsForValue().get("system_status");
        String currentTime = stringRedisTemplate.opsForValue().get("current_time");

        System.out.println("欢迎消息: " + welcomeMsg);
        System.out.println("系统状态: " + systemStatus);
        System.out.println("当前时间: " + currentTime);

        System.out.println("✓ 字符串数据添加成功！");
    }

    @Test
    public void testAddUserData() {
        System.out.println("=== 测试添加用户数据到Redis ===");

        // 创建测试用户对象
        User user1 = new User();
        user1.setUserId("U001");
        user1.setName("张三");
        user1.setPhone("13800138001");
        user1.setSex("男");
        user1.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        User user2 = new User();
        user2.setUserId("U002");
        user2.setName("李四");
        user2.setPhone("13800138002");
        user2.setSex("女");
        user2.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 使用RedisTemplate存储用户对象
        redisTemplate.opsForValue().set("user:U001", user1, 300, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("user:U002", user2, 300, TimeUnit.SECONDS);

        // 验证数据
        User retrievedUser1 = (User) redisTemplate.opsForValue().get("user:U001");
        User retrievedUser2 = (User) redisTemplate.opsForValue().get("user:U002");

        System.out.println("用户1: " + retrievedUser1.getName() + ", 手机: " + retrievedUser1.getPhone());
        System.out.println("用户2: " + retrievedUser2.getName() + ", 手机: " + retrievedUser2.getPhone());

        System.out.println("✓ 用户数据添加成功！");
    }

    @Test
    public void testAddProductData() {
        System.out.println("=== 测试添加商品数据到Redis ===");

        // 创建测试商品列表
        List<Product> productList = new ArrayList<>();

        Product product1 = new Product();
        product1.setProductId("P001");
        product1.setUserId("U001");
        product1.setName("MacBook Pro 2023");
        product1.setPrice(12999.0);
        product1.setClassification("电子产品");
        product1.setStatus("在售");
        product1.setDescription("九成新，配件齐全");
        product1.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));

        Product product2 = new Product();
        product2.setProductId("P002");
        product2.setUserId("U002");
        product2.setName("Java编程思想");
        product2.setPrice(89.0);
        product2.setClassification("图书教材");
        product2.setStatus("在售");
        product2.setDescription("原版书籍，几乎全新");
        product2.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));

        productList.add(product1);
        productList.add(product2);

        // 存储商品列表
        redisTemplate.opsForValue().set("products:featured", productList, 300, TimeUnit.SECONDS);

        // 存储单个商品
        redisTemplate.opsForValue().set("product:P001", product1, 300, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("product:P002", product2, 300, TimeUnit.SECONDS);

        // 验证数据
        List<Product> featuredProducts = (List<Product>) redisTemplate.opsForValue().get("products:featured");
        Product singleProduct = (Product) redisTemplate.opsForValue().get("product:P001");

        System.out.println("推荐商品数量: " + featuredProducts.size());
        System.out.println("商品1名称: " + singleProduct.getName() + ", 价格: ¥" + singleProduct.getPrice());

        System.out.println("✓ 商品数据添加成功！");
    }

    @Test
    public void testAddHashData() {
        System.out.println("=== 测试添加Hash数据到Redis ===");

        // 添加用户会话信息（Hash结构）
        String sessionKey = "session:user:U001";
        redisTemplate.opsForHash().put(sessionKey, "last_login", LocalDateTime.now().toString());
        redisTemplate.opsForHash().put(sessionKey, "login_count", "5");
        redisTemplate.opsForHash().put(sessionKey, "current_ip", "192.168.1.100");
        redisTemplate.expire(sessionKey, 300, TimeUnit.SECONDS);

        // 验证Hash数据
        Object lastLogin = redisTemplate.opsForHash().get(sessionKey, "last_login");
        Object loginCount = redisTemplate.opsForHash().get(sessionKey, "login_count");

        System.out.println("最后登录时间: " + lastLogin);
        System.out.println("登录次数: " + loginCount);

        System.out.println("✓ Hash数据添加成功！");
    }

    @Test
    public void testAddListData() {
        System.out.println("=== 测试添加List数据到Redis ===");

        // 添加消息队列数据
        String messageQueueKey = "message_queue:notifications";
        List<String> notifications = new ArrayList<>();
        notifications.add("您的商品有新留言");
        notifications.add("订单状态已更新");
        notifications.add("系统维护通知");

        // 使用List结构存储
        for (String notification : notifications) {
            redisTemplate.opsForList().leftPush(messageQueueKey, notification);
        }
        redisTemplate.expire(messageQueueKey, 300, TimeUnit.SECONDS);

        // 获取List数据
        List<Object> messages = redisTemplate.opsForList().range(messageQueueKey, 0, -1);
        System.out.println("通知消息数量: " + messages.size());
        for (int i = 0; i < messages.size(); i++) {
            System.out.println("消息" + (i+1) + ": " + messages.get(i));
        }

        System.out.println("✓ List数据添加成功！");
    }

    @Test
    public void displayAllKeys() {
        System.out.println("=== 显示所有Redis键 ===");

        // 获取所有key（注意：生产环境不建议使用KEYS命令）
        Set<String> keys = stringRedisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            System.out.println("当前Redis中的所有键:");
            for (String key : keys) {
                System.out.println("- " + key);
            }
        } else {
            System.out.println("Redis中暂无数据");
        }
    }

    @Test
    public void cleanupTestData() {
        System.out.println("=== 清理测试数据 ===");

        // 删除测试用的key
        stringRedisTemplate.delete("welcome_message");
        stringRedisTemplate.delete("system_status");
        stringRedisTemplate.delete("current_time");
        redisTemplate.delete("user:U001");
        redisTemplate.delete("user:U002");
        redisTemplate.delete("products:featured");
        redisTemplate.delete("product:P001");
        redisTemplate.delete("product:P002");
        redisTemplate.delete("session:user:U001");
        redisTemplate.delete("message_queue:notifications");

        System.out.println("✓ 测试数据清理完成！");
    }
}
