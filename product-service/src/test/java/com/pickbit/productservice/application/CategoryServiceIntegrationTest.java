package com.pickbit.productservice.application;

import com.pickbit.productservice.api.dto.request.CategoryCreateRequest;
import com.pickbit.productservice.api.dto.request.CategoryUpdateRequest;
import com.pickbit.productservice.api.dto.response.CategoryResponse;
import com.pickbit.productservice.config.TestContainerConfig;
import com.pickbit.productservice.exception.CategoryNotFoundException;
import com.pickbit.productservice.exception.DuplicateCategoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@Import(TestContainerConfig.class)
@Testcontainers
@ActiveProfiles("test")
class CategoryServiceIntegrationTest {

    @Autowired
    private CategoryService categoryService;


    @Nested
    @DisplayName("카테고리 등록")
    class CreateCategory {

        @Test
        @DisplayName("정상 요청 시 카테고리가 저장되고 응답을 반환한다")
        void createCategory_success() {
            CategoryCreateRequest request = new CategoryCreateRequest("전자기기", "전자기기 카테고리", 0);

            CategoryResponse response = categoryService.createCategory(request);

            assertThat(response.id()).isNotNull();
            assertThat(response.name()).isEqualTo("전자기기");
            assertThat(response.description()).isEqualTo("전자기기 카테고리");
            assertThat(response.active()).isTrue();
            assertThat(response.sortOrder()).isEqualTo(0);
        }

        @Test
        @DisplayName("중복된 이름으로 등록하면 DuplicateCategoryException이 발생한다")
        void createCategory_duplicateName() {
            categoryService.createCategory(new CategoryCreateRequest("전자기기", "설명1", 0));

            assertThatThrownBy(() -> categoryService.createCategory(new CategoryCreateRequest("전자기기", "설명2", 1)))
                    .isInstanceOf(DuplicateCategoryException.class);
        }
    }

    @Nested
    @DisplayName("카테고리 수정")
    class UpdateCategory {

        private Long categoryId;

        @BeforeEach
        void setUp() {
            categoryId = categoryService.createCategory(new CategoryCreateRequest("전자기기", "전자기기 설명", 0)).id();
        }

        @Test
        @DisplayName("카테고리 정보를 수정할 수 있다")
        void updateCategory_success() {
            CategoryUpdateRequest request = new CategoryUpdateRequest("가전제품", "가전제품 설명", 1);

            CategoryResponse response = categoryService.updateCategory(categoryId, request);

            assertThat(response.name()).isEqualTo("가전제품");
            assertThat(response.description()).isEqualTo("가전제품 설명");
            assertThat(response.sortOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 카테고리와 중복되는 이름으로 수정하면 DuplicateCategoryException이 발생한다")
        void updateCategory_duplicateName() {
            categoryService.createCategory(new CategoryCreateRequest("의류", "의류 설명", 1));

            assertThatThrownBy(() -> categoryService.updateCategory(categoryId, new CategoryUpdateRequest("의류", "설명", 0)))
                    .isInstanceOf(DuplicateCategoryException.class);
        }

        @Test
        @DisplayName("같은 이름으로 수정하면 중복 검사를 통과한다")
        void updateCategory_sameName() {
            CategoryResponse response = categoryService.updateCategory(
                    categoryId, new CategoryUpdateRequest("전자기기", "수정된 설명", 2));

            assertThat(response.name()).isEqualTo("전자기기");
            assertThat(response.description()).isEqualTo("수정된 설명");
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 수정 시 CategoryNotFoundException이 발생한다")
        void updateCategory_notFound() {
            assertThatThrownBy(() -> categoryService.updateCategory(999999L, new CategoryUpdateRequest("이름", "설명", 0)))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("카테고리 활성화/비활성화")
    class SetActive {

        private Long categoryId;

        @BeforeEach
        void setUp() {
            categoryId = categoryService.createCategory(new CategoryCreateRequest("전자기기", "설명", 0)).id();
        }

        @Test
        @DisplayName("카테고리를 비활성화할 수 있다")
        void setActive_deactivate() {
            CategoryResponse response = categoryService.setActive(categoryId, false);

            assertThat(response.active()).isFalse();
        }

        @Test
        @DisplayName("비활성화된 카테고리를 다시 활성화할 수 있다")
        void setActive_reactivate() {
            categoryService.setActive(categoryId, false);

            CategoryResponse response = categoryService.setActive(categoryId, true);

            assertThat(response.active()).isTrue();
        }
    }

    @Nested
    @DisplayName("카테고리 조회")
    class GetCategories {

        @BeforeEach
        void setUp() {
            categoryService.createCategory(new CategoryCreateRequest("전자기기", "설명1", 1));
            categoryService.createCategory(new CategoryCreateRequest("의류", "설명2", 0));
            Long deactivated = categoryService.createCategory(new CategoryCreateRequest("도서", "설명3", 2)).id();
            categoryService.setActive(deactivated, false);
        }

        @Test
        @DisplayName("활성 카테고리만 조회할 수 있다")
        void getActiveCategories() {
            List<CategoryResponse> result = categoryService.getActiveCategories();

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(CategoryResponse::active);
        }

        @Test
        @DisplayName("활성 카테고리는 sortOrder 순으로 정렬된다")
        void getActiveCategories_sorted() {
            List<CategoryResponse> result = categoryService.getActiveCategories();

            assertThat(result.get(0).name()).isEqualTo("의류");
            assertThat(result.get(1).name()).isEqualTo("전자기기");
        }

        @Test
        @DisplayName("전체 카테고리를 조회할 수 있다 (비활성 포함)")
        void getAllCategories() {
            List<CategoryResponse> result = categoryService.getAllCategories();

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("단건 카테고리를 조회할 수 있다")
        void getCategory() {
            List<CategoryResponse> all = categoryService.getAllCategories();
            Long id = all.getFirst().id();

            CategoryResponse response = categoryService.getCategory(id);

            assertThat(response.id()).isEqualTo(id);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 조회 시 CategoryNotFoundException이 발생한다")
        void getCategory_notFound() {
            assertThatThrownBy(() -> categoryService.getCategory(999999L))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }
}
