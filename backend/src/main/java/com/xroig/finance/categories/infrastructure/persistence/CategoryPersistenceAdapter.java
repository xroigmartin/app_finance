package com.xroig.finance.categories.infrastructure.persistence;

import com.xroig.finance.categories.domain.Category;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.domain.CategoryRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Command-side persistence adapter: implements the {@link CategoryRepository} port over JPA. */
@Component
public class CategoryPersistenceAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;
    private final CategoryJpaMapper mapper;

    public CategoryPersistenceAdapter(CategoryJpaRepository jpa, CategoryJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Category save(Category category) {
        return mapper.toDomain(jpa.save(mapper.toJpa(category)));
    }

    @Override
    public void deleteById(CategoryId id) {
        jpa.deleteById(id.value());
    }

    @Override
    public boolean existsByParentId(CategoryId parentId) {
        return jpa.existsByParentId(parentId.value());
    }

    @Override
    public List<Category> findChildren(CategoryId parentId) {
        return jpa.findByParentId(parentId.value()).stream().map(mapper::toDomain).toList();
    }
}
