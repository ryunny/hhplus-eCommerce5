package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.BaseIntegrationTest;
import com.hhplus.ecommerce.domain.entity.Category;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.repository.CategoryRepository;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Quantity;
import com.hhplus.ecommerce.domain.vo.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * ProductService 통합 테스트
 *
 * 상품 재고 관리 핵심 기능을 검증합니다.
 * - 재고 조회 및 검증
 * - 재고 차감 (비관적 락)
 * - 재고 복구
 */
class ProductServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("상품을 생성하고 조회할 수 있다")
    void createAndGetProduct() {
        // given
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = new Product(
                category,
                "노트북",
                "고성능 노트북",
                new Money(1000000L),
                new Stock(10)
        );
        Product savedProduct = productRepository.save(product);

        // when
        Product foundProduct = productService.getProduct(savedProduct.getId());

        // then
        assertThat(foundProduct).isNotNull();
        assertThat(foundProduct.getName()).isEqualTo("노트북");
        assertThat(foundProduct.getPrice().getAmount()).isEqualTo(1000000L);
        assertThat(foundProduct.getStock().getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고를 차감할 수 있다")
    void decreaseStock() {
        // given
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "마우스", 10000L, 100);
        Product savedProduct = productRepository.save(product);

        // when
        productService.decreaseStock(savedProduct.getId(), new Quantity(10));

        // then
        Product updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getValue()).isEqualTo(90); // 100 - 10
    }

    @Test
    @DisplayName("재고가 부족하면 차감할 수 없다")
    void decreaseStock_InsufficientStock() {
        // given
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "키보드", 15000L, 5);
        Product savedProduct = productRepository.save(product);

        // when & then
        assertThatThrownBy(() ->
                productService.decreaseStock(savedProduct.getId(), new Quantity(10))
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");

        // then - 재고는 변경되지 않아야 함
        Product unchangedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(unchangedProduct.getStock().getValue()).isEqualTo(5);
    }

    @Test
    @DisplayName("재고를 복구할 수 있다")
    void increaseStock() {
        // given
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "모니터", 300000L, 10);
        Product savedProduct = productRepository.save(product);

        // when - 재고 차감 후 복구
        productService.decreaseStock(savedProduct.getId(), new Quantity(5));
        productService.increaseStock(savedProduct.getId(), new Quantity(3));

        // then
        Product updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getValue()).isEqualTo(8); // 10 - 5 + 3
    }

    @Test
    @DisplayName("재고 검증이 정상 동작한다")
    void validateStock() {
        // given
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "헤드셋", 50000L, 10);
        Product savedProduct = productRepository.save(product);

        // when & then - 재고가 충분한 경우
        assertThatCode(() ->
                productService.validateStock(savedProduct, new Quantity(5))
        ).doesNotThrowAnyException();

        // when & then - 재고가 부족한 경우
        assertThatThrownBy(() ->
                productService.validateStock(savedProduct, new Quantity(20))
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("여러 상품을 동시에 조회할 수 있다")
    void getProducts() {
        // given
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product1 = createProduct(category, "상품1", 10000L, 10);
        Product product2 = createProduct(category, "상품2", 20000L, 20);
        Product product3 = createProduct(category, "상품3", 30000L, 30);

        Product saved1 = productRepository.save(product1);
        Product saved2 = productRepository.save(product2);
        Product saved3 = productRepository.save(product3);

        List<Long> productIds = List.of(saved1.getId(), saved2.getId(), saved3.getId());

        // when
        List<Product> products = productService.getProducts(productIds);

        // then
        assertThat(products).hasSize(3);
        assertThat(products).extracting("name")
                .containsExactlyInAnyOrder("상품1", "상품2", "상품3");
    }

    @Test
    @DisplayName("비관적 락을 사용하여 상품을 조회할 수 있다")
    void getProductWithLock() {
        // given
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "노트북", 1000000L, 10);
        Product savedProduct = productRepository.save(product);

        // when
        Product lockedProduct = productRepository.findByIdWithLock(savedProduct.getId()).orElseThrow();

        // then
        assertThat(lockedProduct).isNotNull();
        assertThat(lockedProduct.getName()).isEqualTo("노트북");
        assertThat(lockedProduct.getStock().getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("카테고리별 상품 목록을 조회할 수 있다")
    void getProductsByCategory() {
        // given
        Category electronics = categoryRepository.save(new Category("전자제품"));
        Category books = categoryRepository.save(new Category("도서"));

        productRepository.save(createProduct(electronics, "노트북", 1000000L, 10));
        productRepository.save(createProduct(electronics, "마우스", 10000L, 20));
        productRepository.save(createProduct(books, "자바의 정석", 30000L, 50));

        // when
        List<Product> electronicsProducts = productRepository.findByCategoryId(electronics.getId());

        // then
        assertThat(electronicsProducts).hasSize(2);
        assertThat(electronicsProducts).extracting("name")
                .containsExactlyInAnyOrder("노트북", "마우스");
    }

    @Test
    @DisplayName("존재하지 않는 상품을 조회하면 예외가 발생한다")
    void getProduct_NotFound() {
        // when & then
        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }

    // Helper method
    private Product createProduct(Category category, String name, Long price, int stock) {
        return new Product(category, name, "상품 설명", new Money(price), new Stock(stock));
    }
}
