package com.mall.product;

import com.mall.product.common.*;
import com.mall.product.dto.request.*;
import com.mall.product.dto.response.ProductDetailResponse;
import com.mall.product.service.ProductService;
import com.mall.product.service.StockService;
import com.mall.product.common.TimeUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ProductLifecycleRegressionTest {
    static final String SELLER="LIFE-SELLER";
    @Autowired ProductService products;
    @Autowired StockService stock;
    @Autowired JdbcTemplate db;
    @Autowired com.mall.product.mapper.ProductMapper productMapper;
    @BeforeEach void clear() {
        db.update("DELETE FROM stock_reservation_item");db.update("DELETE FROM stock_reservation");
        db.update("DELETE FROM sku");db.update("DELETE FROM product");
    }
    ProductDetailResponse product(boolean approved) {
        CreateSkuRequest sku=new CreateSkuRequest();sku.setSpecName("default");sku.setPrice(100L);sku.setStockAvailable(10);
        CreateProductRequest req=new CreateProductRequest();req.setTitle("Lifecycle fixture");req.setCategoryId("C04");req.setSkus(List.of(sku));
        var p=products.create(SELLER,req);
        if(approved){AuditProductRequest a=new AuditProductRequest();a.setApproved(true);p=products.audit(p.getProductId(),"ADMIN",a);}
        return p;
    }
    UpdateStatusRequest status(String s){var r=new UpdateStatusRequest();r.setStatus(s);return r;}
    void reserve(ProductDetailResponse p,String order) {
        ReserveItem i=new ReserveItem();i.setSkuId(p.getSkus().get(0).getSkuId());i.setQuantity(2);
        ReserveStockRequest r=new ReserveStockRequest();r.setOrderId(order);r.setItems(List.of(i));r.setExpireAt(TimeUtils.format(LocalDateTime.now().plusMinutes(30)));stock.reserve(r);
    }
    int value(String sku,String col){return db.queryForObject("SELECT "+col+" FROM sku WHERE sku_id=?",Integer.class,sku);}
    @Test void pendingAndRejectedCannotLaunderApprovalThroughOffShelf() {
        var p=product(false);
        for(String state:List.of("PENDING","REJECTED")) {
            db.update("UPDATE product SET status=? WHERE product_id=?",state,p.getProductId());
            assertThrows(BizException.class,()->products.updateStatus(p.getProductId(),SELLER,status("OFF_SHELF")));
            assertThrows(BizException.class,()->products.updateStatus(p.getProductId(),SELLER,status("ON_SALE")));
            assertEquals(state,db.queryForObject("SELECT status FROM product WHERE product_id=?",String.class,p.getProductId()));
        }
    }
    @Test void approvedOffShelfRoundTripWorksButEditRequiresNewApproval() {
        var p=product(true);
        assertEquals(ProductStatus.OFF_SHELF,products.updateStatus(p.getProductId(),SELLER,status("OFF_SHELF")).getStatus());
        assertEquals(ProductStatus.ON_SALE,products.updateStatus(p.getProductId(),SELLER,status("ON_SALE")).getStatus());
        UpdateProductRequest edit=new UpdateProductRequest();edit.setTitle("Edited requires review");products.update(p.getProductId(),SELLER,edit);
        assertThrows(BizException.class,()->products.updateStatus(p.getProductId(),SELLER,status("OFF_SHELF")));
    }
    @Test void sellerStatusUpdateCannotOverwriteConcurrentReviewOrOtherOwners() {
        var p=product(true);
        db.update("UPDATE product SET status='PENDING' WHERE product_id=?",p.getProductId());
        assertEquals(0,productMapper.updateSellerStatus(p.getProductId(),ProductStatus.ON_SALE,ProductStatus.OFF_SHELF,SELLER));
        db.update("UPDATE product SET status='ON_SALE' WHERE product_id=?",p.getProductId());
        assertEquals(0,productMapper.updateSellerStatus(p.getProductId(),ProductStatus.ON_SALE,ProductStatus.OFF_SHELF,"OTHER"));
        assertEquals("ON_SALE",db.queryForObject("SELECT status FROM product WHERE product_id=?",String.class,p.getProductId()));
    }
    @Test void currentSellerStillCannotOperateAnotherSellersProduct() {
        var p=product(true);
        assertThrows(BizException.class,()->products.updateStatus(p.getProductId(),"OTHER",status("OFF_SHELF")));
        assertThrows(BizException.class,()->products.delete(p.getProductId(),"OTHER"));
        assertEquals("ON_SALE",db.queryForObject("SELECT status FROM product WHERE product_id=?",String.class,p.getProductId()));
    }
    @Test void deletedProductCanReleaseExistingReservationButCannotBeReservedAgain() {
        var p=product(true);String sku=p.getSkus().get(0).getSkuId();reserve(p,"O-DELETE-RELEASE");products.delete(p.getProductId(),SELLER);
        assertTrue(stock.release("O-DELETE-RELEASE").released());
        assertTrue(stock.release("O-DELETE-RELEASE").released());
        assertEquals(0,value(sku,"stock_locked"));assertEquals(10,value(sku,"stock_available"));
        assertThrows(BizException.class,()->reserve(p,"O-DELETE-NEW"));
        assertEquals(0,products.list(null,null,null,null,"default",null,null,1,20).total());
    }
    @Test void deletedProductCanConfirmThenRestockExactlyOnce() {
        var p=product(true);String sku=p.getSkus().get(0).getSkuId();reserve(p,"O-DELETE-PAY");products.delete(p.getProductId(),SELLER);
        assertTrue(stock.confirm("O-DELETE-PAY").confirmed());assertTrue(stock.confirm("O-DELETE-PAY").confirmed());
        assertEquals(0,value(sku,"stock_locked"));assertEquals(8,value(sku,"stock_available"));
        stock.refundRestock("O-DELETE-PAY");stock.refundRestock("O-DELETE-PAY");
        assertEquals(10,value(sku,"stock_available"));assertEquals(1,value(sku,"is_deleted"));
    }
    @Test void deletedProductAfterPaymentStillRestocksAndNeverReturnsToCatalog() {
        var p=product(true);String sku=p.getSkus().get(0).getSkuId();reserve(p,"O-DELETE-REFUND");stock.confirm("O-DELETE-REFUND");products.delete(p.getProductId(),SELLER);
        stock.refundRestock("O-DELETE-REFUND");assertEquals(10,value(sku,"stock_available"));
        assertThrows(BizException.class,()->products.detail(p.getProductId(),SELLER,"SELLER"));
    }
    @Test void missingHistoricalSkuRollsBackRefundMarkerInsteadOfFalseSuccess() {
        var p=product(true);String sku=p.getSkus().get(0).getSkuId();reserve(p,"O-MISSING-SKU");stock.confirm("O-MISSING-SKU");
        db.update("DELETE FROM sku WHERE sku_id=?",sku);
        assertThrows(BizException.class,()->stock.refundRestock("O-MISSING-SKU"));
        assertEquals("CONFIRMED",db.queryForObject("SELECT status FROM stock_reservation WHERE order_id=?",String.class,"O-MISSING-SKU"));
    }
}
