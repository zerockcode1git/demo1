package com.example.demo1.product.service;

import com.example.demo1.product.dto.ProductCreateRequest;
import com.example.demo1.product.dto.ProductPageRequest;
import com.example.demo1.product.dto.ProductPageResponse;
import com.example.demo1.product.dto.ProductResponse;
import com.example.demo1.product.dto.ProductUpdateRequest;
import com.example.demo1.product.dto.ProductUpdateResult;
import com.example.demo1.product.entity.Product;
import com.example.demo1.product.entity.ProductImage;
import com.example.demo1.product.exception.InvalidProductImageException;
import com.example.demo1.product.exception.ProductNotFoundException;
import com.example.demo1.product.repository.ProductRepository;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클래스 기본값은 읽기 전용 트랜잭션이고, 데이터를 변경하는 메서드만 @Transactional로 재정의한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = new Product(request.name(), request.price(), request.stock());
        request.imageUrls().forEach(product::addImage);
        return ProductResponse.from(productRepository.save(product));
    }

    public ProductResponse getOne(Long id) {
        return ProductResponse.from(findProductWithImages(id));
    }

    public ProductPageResponse getAll(ProductPageRequest request) {
        return ProductPageResponse.from(productRepository.findAllWithMainImage(request.toNamePattern(), request.toPageable()));
    }

    /**
     * @param newImageUrls 새로 업로드한 이미지 URL. 남겨 둔 기존 이미지 뒤에 붙는다.
     * @return 수정된 상품과, 수정으로 빠진 기존 이미지 URL
     */
    @Transactional
    public ProductUpdateResult update(Long id, ProductUpdateRequest request, List<String> newImageUrls) {
        Product product = findProductForUpdate(id);
        List<String> currentUrls = product.getImages().stream().map(ProductImage::imageUrl).toList();
        List<String> keptUrls = request.imageUrls();
        validateKeptImages(currentUrls, keptUrls);
        List<String> removedUrls = currentUrls.stream().filter(url -> !keptUrls.contains(url)).toList();

        product.changeName(request.name());
        product.changePrice(request.price());
        product.changeStock(request.stock());
        product.clearImages();
        keptUrls.forEach(product::addImage);
        newImageUrls.forEach(product::addImage);

        // 변경 감지로 저장되지만 updatedAt은 flush 시점에 갱신되므로, 응답 생성 전에 flush한다.
        productRepository.flush();
        return new ProductUpdateResult(ProductResponse.from(product), removedUrls);
    }

    /**
     * 삭제 표시만 한다. 이후 조회, 수정, 삭제 요청은 상품이 없는 것으로 처리된다.
     */
    @Transactional
    public void delete(Long id) {
        findProductForUpdate(id).delete();
    }

    /**
     * 남겨 둘 이미지는 이 상품의 기존 이미지여야 한다. 다른 곳의 URL을 끼워 넣지 못하게 한다.
     */
    private void validateKeptImages(List<String> currentUrls, List<String> keptUrls) {
        for (String url : keptUrls) {
            if (!currentUrls.contains(url)) {
                throw new InvalidProductImageException("상품에 없는 이미지입니다. url=" + url);
            }
        }
        if (new HashSet<>(keptUrls).size() != keptUrls.size()) {
            throw new InvalidProductImageException("같은 이미지를 두 번 넣을 수 없습니다.");
        }
    }

    private Product findProductWithImages(Long id) {
        return productRepository.findWithImagesById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Product findProductForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
