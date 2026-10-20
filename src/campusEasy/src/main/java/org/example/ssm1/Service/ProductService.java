package org.example.ssm1.Service;

import org.example.ssm1.Entity.Product;
import org.example.ssm1.Mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Cacheable(value = "products", key = "#productId")
    public Product getProductById(String productId) {
        return productMapper.selectById(productId);
    }
    
    @Cacheable(value = "all_products", key = "'all'")
    public List<Product> getAllProducts() {
        return productMapper.selectAll();
    }

    public List<Product> getProductsByPrice(double low, double high) {
        return productMapper.selectByPrice(low,high,"在售");
    }
    public List<Product> getProductsByUserId(String userId) {
        return productMapper.selectByUserId(userId);
    }

    @Cacheable(value = "products_by_status", key = "#status")
    public List<Product> getProductsByStatus(String status) {
        return productMapper.selectByStatus(status);
    }

    @Cacheable(value = "products_by_class", key = "#classification")
    public List<Product> getProductsByClassification(String classification) {
        return productMapper.selectByClassification(classification,"在售");
    }

    @CacheEvict(value = {"all_products", "products_by_status", "products_by_class"}, allEntries = true)
    public int createProduct(Product product) {
        return productMapper.insert(product);
    }

    @CacheEvict(value = {"all_products", "products_by_status", "products_by_class"}, allEntries = true)
    public int createProductWithImage(String userId, String name, double price, String classification, String description, MultipartFile image) throws IOException {
        Product product = new Product();
        product.setProductId("p" + System.currentTimeMillis());
        product.setUserId(userId);
        product.setName(name);
        product.setPrice(price);
        product.setClassification(classification);
        product.setDescription(description);
        product.setStatus("在售");
        product.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        
        if (image != null && !image.isEmpty()) {
            String contentType = image.getContentType();
            if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
                throw new IllegalArgumentException("仅支持 JPG 或 PNG 格式的图片");
            }
            String imageUrl = saveImage(image);
            product.setImageUrl(imageUrl);
        }
        
        return productMapper.insert(product);
    }

    private String saveImage(MultipartFile image) throws IOException {
        String originalFilename = image.getOriginalFilename();
        String extension = "";
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originalFilename.substring(dotIndex);
            }
        }
        String filename = "product_" + System.currentTimeMillis() + extension;
        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        Path target = uploadDir.resolve(filename);
        Files.copy(image.getInputStream(), target);
        return "/api/products/images/" + filename;
    }

    @CacheEvict(value = {"products", "all_products", "products_by_status", "products_by_class"}, allEntries = true)
    public int updateProductStatus(Product product) {
        if (product != null) {
            return productMapper.update(product);
        }
        return 0;
    }

    @CacheEvict(value = {"products", "all_products", "products_by_status", "products_by_class"}, allEntries = true)
    public int updateProduct(Product product) {
        return productMapper.update(product);
    }

    @CacheEvict(value = {"products", "all_products", "products_by_status", "products_by_class"}, allEntries = true)
    public int deleteProduct(String productId) {return productMapper.deleteById(productId);}
    
    @CacheEvict(value = {"products", "all_products", "products_by_status", "products_by_class"}, allEntries = true)
    public int deleteProductsByUserIdAndName(String userId, String name) {
        return productMapper.deleteByUserIdAndName(userId,name,"在售");
    }

}
