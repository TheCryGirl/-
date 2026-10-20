package org.example.ssm1.Controller;

import org.example.ssm1.Entity.Order;
import org.example.ssm1.Service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrderById(@PathVariable String orderId) {
        Order order = orderService.getOrderById(orderId);
        if (order != null) {return ResponseEntity.ok(order);}
        else {return ResponseEntity.notFound().build();}
    }

    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<List<Order>> getOrdersByBuyerId(@PathVariable String buyerId) {
        List<Order> orders = orderService.getOrdersByBuyerId(buyerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<List<Order>> getOrdersBySellerId(@PathVariable String sellerId) {
        List<Order> orders = orderService.getOrdersBySellerId(sellerId);
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<Integer> createOrder(@RequestBody Order order) {
        int result = orderService.createOrder(order);
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<Integer> updateOrder(@RequestBody Order order) {
        int result = orderService.updateOrder(order);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Integer> deleteOrder(@PathVariable String orderId) {
        int result = orderService.deleteOrder(orderId);
        return ResponseEntity.ok(result);
    }
}
