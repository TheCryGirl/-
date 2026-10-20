package org.example.ssm1.Controller;

import org.example.ssm1.Entity.PurchaseRequest;
import org.example.ssm1.Service.PurchaseRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-requests")
@CrossOrigin
public class PurchaseRequestController {

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<List<PurchaseRequest>> getByBuyerId(@PathVariable String buyerId) {
        return ResponseEntity.ok(purchaseRequestService.getByBuyerId(buyerId));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Integer> requestPurchase(@PathVariable String productId, @RequestBody Map<String, String> payload, @RequestAttribute(value = "currentUser", required = false) String currentUserId) {
        String buyerId = payload.get("buyerId");
        if (currentUserId != null && !currentUserId.equals(buyerId)) {
            return ResponseEntity.status(403).body(0);
        }
        if (currentUserId == null) {
             return ResponseEntity.status(401).body(0);
        }
        int result = purchaseRequestService.requestPurchase(productId, buyerId);
        if (result > 0) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(0);
    }

    @PutMapping("/{productId}/reject")
    public ResponseEntity<Integer> rejectPurchase(@PathVariable String productId, @RequestBody Map<String, String> payload) {
        String buyerId = payload.get("buyerId");
        int result = purchaseRequestService.rejectPurchase(productId, buyerId);
        if (result > 0) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(0);
    }
}
