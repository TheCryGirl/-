package org.example.ssm1.Controller;

import org.example.ssm1.Entity.Product;
import org.example.ssm1.Service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/{productId}")
    public ResponseEntity<Product> getProductById(@PathVariable String productId) {
        Product product = productService.getProductById(productId);
        if (product != null) {
            return ResponseEntity.ok(product);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Product>> getProductsByUserId(@PathVariable String userId) {
        List<Product> products = productService.getProductsByUserId(userId);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/user/{userId}/all")
    public ResponseEntity<List<Product>> getAllProductsByUserId(@PathVariable String userId) {
        List<Product> products = productService.getProductsByUserId(userId);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Product>> getProductsByStatus(@PathVariable String status) {
        List<Product> products = productService.getProductsByStatus(status);
        return ResponseEntity.ok(products);
    }
    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchProduct(@RequestParam String keyword) {
        List<Product> products = productService.getProductsByStatus("在售");
        products.removeIf(product -> !product.getName().contains(keyword));
        return ResponseEntity.ok(products);
    }

    @GetMapping("/classification/{classification}")
    public ResponseEntity<List<Product>> getProductsByClassification(@PathVariable String classification) {
        List<Product> products = productService.getProductsByClassification(classification);
        return ResponseEntity.ok(products);
    }
    @GetMapping("/price/{low}/{high}")
    public ResponseEntity<List<Product>> getProductsByPrice(@PathVariable double low, @PathVariable double high) {
        List<Product> products = productService.getProductsByPrice(low, high);
        return ResponseEntity.ok(products);
    }

    @PostMapping
    public ResponseEntity<Integer> createProduct(@RequestBody Product product) {
        int result = productService.createProduct(product);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/with-image")
    public ResponseEntity<Integer> createProductWithImage(@RequestParam String userId,
                                                          @RequestParam String name,
                                                          @RequestParam double price,
                                                          @RequestParam String classification,
                                                          @RequestParam(required = false) String description,
                                                          @RequestParam(required = false) MultipartFile image) {
        try {
            int result = productService.createProductWithImage(userId, name, price, classification, description, image);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(-1);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(-1);
        }
    }

    @GetMapping("/images/{filename:.+}")
    public ResponseEntity<Resource> getProductImage(@PathVariable String filename) throws IOException {
        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        Path imagePath = uploadDir.resolve(filename).normalize();
        if (!Files.exists(imagePath) || !imagePath.startsWith(uploadDir)) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = Files.readAllBytes(imagePath);
        ByteArrayResource resource = new ByteArrayResource(data);
        String contentType = Files.probeContentType(imagePath);
        MediaType mediaType = contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    @PutMapping
    public ResponseEntity<Integer> updateProduct(@RequestBody Product product) {
        int result = productService.updateProduct(product);
        return ResponseEntity.ok(result);
    }
    @PutMapping("/{productId}/status")
    public ResponseEntity<Integer> updateProductStatus(@RequestBody Product product){
        int result = productService.updateProductStatus(product);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Integer> deleteProduct(@PathVariable String productId) {
        int result = productService.deleteProduct(productId);
        return ResponseEntity.ok(result);
    }
}
