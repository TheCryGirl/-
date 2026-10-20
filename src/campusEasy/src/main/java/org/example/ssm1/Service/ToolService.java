package org.example.ssm1.Service;

import org.example.ssm1.Entity.Product;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class ToolService {
    private final ProductService productService;
    private final OrderService orderService;
    private final PurchaseRequestService purchaseRequestService;
    
    public ToolService(ProductService productService, OrderService orderService, PurchaseRequestService purchaseRequestService) {
        this.productService = productService;
        this.orderService = orderService;
        this.purchaseRequestService = purchaseRequestService;
    }
    
    private String getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            Object currentUser = request.getAttribute("currentUser");
            if (currentUser != null) {
                return currentUser.toString();
            }
        }
        return null;
    }

    @Tool(description = "下架用户商品")
    public String cancelUserProduct(@ToolParam String userId, @ToolParam String name) {
        if (productService.deleteProductsByUserIdAndName(userId,name)>0) {
            return "商品下架成功";
        }
        return "商品不存在";
    }
    @Tool(description = "查询全部可购买商品(所有回复都应该实事求是，不凭空捏造、设想，基于客观真实的数据向用户回复)")
    public List<Product> queryAllProduct() {
        List<Product> products = productService.getProductsByStatus("在售");
        if (products.isEmpty()) {
            throw new RuntimeException("暂无符合条件的商品");
        }
        return products;
    }
    @Tool(description = "查询当前用户的商品(若userId未知应向用户询问，切忌自己捏造)")
    public List<Product> queryUserProduct(@ToolParam String userId) {
        return productService.getProductsByUserId(userId);
    }
    @Tool(description = "按分类推荐商品，分类包括'books','sports','clothing','electronics','others'")
    public List<Product> recommendProduct(@ToolParam String classification) {
        return productService.getProductsByClassification(classification);
    }
    @Tool(description = "按价格区间搜索商品")
    public List<Product> searchProductByPrice(@ToolParam double minPrice, @ToolParam double maxPrice) {
        return productService.getProductsByPrice(minPrice,maxPrice);
    }
    @Tool(description = "按名称搜索商品")
    public List<Product> searchProductByKeyword(@ToolParam String keyword) {
        List<Product> products = productService.getProductsByStatus("在售");
        products.removeIf(product -> !product.getName().contains(keyword));
        return products;
    }
    @Tool(description = "发送购买请求")
    public String buyProduct(@ToolParam String productId) {
        try {
            String buyerId = getCurrentUserId();
            if (buyerId == null) {
                return "请先登录";
            }
            
            Product product = productService.getProductById(productId);
            if (product == null) {
                return "商品不存在";
            }
            if (!"在售".equals(product.getStatus())) {
                return "商品不在销售状态";
            }
            if (buyerId.equals(product.getUserId())) {
                return "不能购买自己的商品";
            }
            int requestResult = purchaseRequestService.requestPurchase(productId, buyerId);
            if (requestResult > 0) {
                return "已发起购买请求，请等待卖家确认";
            }
            return "购买请求失败，请稍后重试";
        } catch (Exception e) {
            return "购买过程中发生错误：" + e.getMessage();
        }
    }
    @Tool(description = "转接人工客服")
    public String transferToAgent() {
        return "详情请致电400-888-3456";
    }

    public OrderService getOrderService() {
        return orderService;
    }
}
