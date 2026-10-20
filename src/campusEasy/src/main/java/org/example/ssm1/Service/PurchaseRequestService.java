package org.example.ssm1.Service;

import org.example.ssm1.Entity.Product;
import org.example.ssm1.Entity.PurchaseRequest;
import org.example.ssm1.Mapper.PurchaseRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PurchaseRequestService {

    @Autowired
    private PurchaseRequestMapper purchaseRequestMapper;

    @Autowired
    private ProductService productService;

    public List<PurchaseRequest> getByBuyerId(String buyerId) {
        return purchaseRequestMapper.selectByBuyerId(buyerId);
    }

    @Transactional
    public int requestPurchase(String productId, String buyerId) {
        Product product = productService.getProductById(productId);
        if (product == null || !"在售".equals(product.getStatus())) {
            return 0;
        }
        if (buyerId == null || buyerId.isEmpty() || buyerId.equals(product.getUserId())) {
            return 0;
        }
        product.setStatus("确认中");
        product.setPendingBuyerId(buyerId);
        int updateResult = productService.updateProduct(product);
        if (updateResult <= 0) {
            return 0;
        }
        PurchaseRequest request = new PurchaseRequest();
        request.setProductId(productId);
        request.setSellerId(product.getUserId());
        request.setBuyerId(buyerId);
        request.setStatus("确认中");
        request.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        return purchaseRequestMapper.insert(request);
    }

    @Transactional
    public int rejectPurchase(String productId, String buyerId) {
        Product product = productService.getProductById(productId);
        if (product == null || !"确认中".equals(product.getStatus())) {
            return 0;
        }
        if (buyerId == null || buyerId.isEmpty()) {
            return 0;
        }
        product.setStatus("在售");
        product.setPendingBuyerId(null);
        int updateResult = productService.updateProduct(product);
        if (updateResult <= 0) {
            return 0;
        }
        return purchaseRequestMapper.updateStatusByProductAndBuyer(productId, buyerId, "购买失败");
    }

    public int confirmPurchase(String productId, String buyerId) {
        return purchaseRequestMapper.updateStatusByProductAndBuyer(productId, buyerId, "已确认");
    }
}
