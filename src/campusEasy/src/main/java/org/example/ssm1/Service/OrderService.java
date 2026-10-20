package org.example.ssm1.Service;

import org.example.ssm1.Entity.Order;
import org.example.ssm1.Entity.Product;
import org.example.ssm1.Mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    public Order getOrderById(String orderId) {
        return orderMapper.selectById(orderId);
    }
    public List<Order> getOrdersByBuyerId(String buyerId) {
        return orderMapper.selectByBuyerId(buyerId);
    }
    public List<Order> getOrdersBySellerId(String sellerId) {
        return orderMapper.selectBySellerId(sellerId);
    }
    @Transactional
    public int createOrder(Order order) {
        Product product = productService.getProductById(order.getProductId());
        if (product == null || !"确认中".equals(product.getStatus())) {
            throw new RuntimeException("商品不存在或不在确认状态");
        }
        if (product.getPendingBuyerId() != null && !product.getPendingBuyerId().equals(order.getBuyerId())) {
            throw new RuntimeException("当前商品待确认买家不匹配");
        }
        int result = orderMapper.insert(order);
        if (result > 0) {
            product.setStatus("已售");
            product.setPendingBuyerId(null);
            
            int updateResult = productService.updateProduct(product);
            if (updateResult <= 0) {
                throw new RuntimeException("商品状态已变更，请刷新后重试");
            }
            
            int confirmResult = purchaseRequestService.confirmPurchase(order.getProductId(), order.getBuyerId());
            if (confirmResult <= 0) {
                throw new RuntimeException("更新购买请求状态失败");
            }
        }
        return result;
    }

    public int updateOrder(Order order) {
        return orderMapper.update(order);
    }
    public int deleteOrder(String orderId) {
        return orderMapper.deleteById(orderId);
    }
}
