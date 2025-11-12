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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * ProductService 동시성 테스트
 *
 * 실제 멀티스레드 환경에서 상품 재고 관리의 동시성 제어를 검증합니다.
 * - 동시 재고 차감 시 정확성
 * - 비관적 락을 통한 동시성 제어
 * - 재고 부족 예외 처리
 */
class ProductServiceConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("100명이 동시에 재고 10개인 상품을 1개씩 주문하면 10명만 성공한다")
    void decreaseStock_Concurrency_LimitedStock() throws InterruptedException {
        // given - 재고 10개인 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "인기 상품", 50000L, 10);
        Product savedProduct = productRepository.save(product);

        // given - 동시성 제어 설정
        int totalThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명이 동시에 재고 1개씩 차감 시도
        for (int i = 0; i < totalThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    productService.decreaseStock(savedProduct.getId(), new Quantity(1));
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(); // 모든 스레드 준비 대기
        startLatch.countDown(); // 동시 시작
        doneLatch.await(); // 모든 스레드 완료 대기

        executorService.shutdown();

        // then - 정확히 10명만 성공
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(90);

        // then - 재고가 0이 되어야 함
        Product updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("50명이 동시에 재고 100개인 상품을 2개씩 주문하면 모두 성공하고 재고는 0이 된다")
    void decreaseStock_Concurrency_ExactMatch() throws InterruptedException {
        // given - 재고 100개인 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "충분한 재고 상품", 10000L, 100);
        Product savedProduct = productRepository.save(product);

        // given - 동시성 제어 설정
        int totalThreads = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 50명이 동시에 2개씩 차감 (총 100개)
        for (int i = 0; i < totalThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    productService.decreaseStock(savedProduct.getId(), new Quantity(2));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        // then - 50명 모두 성공
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(0);

        // then - 재고가 정확히 0
        Product updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("동시에 여러 스레드에서 재고 차감과 복구를 반복해도 최종 재고는 정확하다")
    void decreaseAndIncreaseStock_Concurrency() throws InterruptedException {
        // given - 재고 100개인 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "테스트 상품", 10000L, 100);
        Product savedProduct = productRepository.save(product);

        // given - 동시성 제어 설정
        int decreaseThreads = 30; // 30개 차감
        int increaseThreads = 20; // 20개 복구
        int totalThreads = decreaseThreads + increaseThreads;

        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        // when - 30개 차감 스레드
        for (int i = 0; i < decreaseThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    productService.decreaseStock(savedProduct.getId(), new Quantity(1));
                } catch (Exception e) {
                    // 재고 부족 시 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // when - 20개 복구 스레드
        for (int i = 0; i < increaseThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    productService.increaseStock(savedProduct.getId(), new Quantity(1));
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        // then - 최종 재고는 100 - 30 + 20 = 90
        Product updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(90);
    }

    @Test
    @DisplayName("재고가 1개 남았을 때 100명이 동시에 요청하면 1명만 성공한다")
    void decreaseStock_Concurrency_LastOne() throws InterruptedException {
        // given - 재고 1개인 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "마지막 재고", 100000L, 1);
        Product savedProduct = productRepository.save(product);

        // given - 동시성 제어 설정
        int totalThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명이 동시에 마지막 1개 구매 시도
        for (int i = 0; i < totalThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    productService.decreaseStock(savedProduct.getId(), new Quantity(1));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        // then - 정확히 1명만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(99);

        // then - 재고 0
        Product updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("여러 스레드에서 다른 수량을 동시에 차감해도 총 재고는 정확하다")
    void decreaseStock_Concurrency_DifferentQuantities() throws InterruptedException {
        // given - 재고 1000개인 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "대량 재고 상품", 1000L, 1000);
        Product savedProduct = productRepository.save(product);

        // given - 동시성 제어 설정
        int totalThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger totalDecreased = new AtomicInteger(0);

        // when - 100명이 1~10개씩 랜덤하게 차감
        for (int i = 0; i < totalThreads; i++) {
            final int quantity = (i % 10) + 1; // 1~10개
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    productService.decreaseStock(savedProduct.getId(), new Quantity(quantity));
                    totalDecreased.addAndGet(quantity);
                } catch (Exception e) {
                    // 재고 부족 시 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        // then - 실제 차감된 재고와 DB의 재고가 일치
        Product updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(1000 - totalDecreased.get());
    }

    // Helper method
    private Product createProduct(Category category, String name, Long price, int stock) {
        return new Product(category, name, "상품 설명", new Money(price), new Stock(stock));
    }
}
