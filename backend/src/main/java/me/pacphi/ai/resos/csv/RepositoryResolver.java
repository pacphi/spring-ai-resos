package me.pacphi.ai.resos.csv;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("dev")
public class RepositoryResolver {
    private final ListableBeanFactory beanFactory;
    private final Map<Class<?>, CrudRepository<?, ?>> repositoryCache;

    public RepositoryResolver(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        this.repositoryCache = new ConcurrentHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public <T, ID> CrudRepository<T, ID> getRepositoryForEntity(Class<T> entityClass) {
        return (CrudRepository<T, ID>) repositoryCache.computeIfAbsent(entityClass, this::findRepositoryForEntity);
    }

    private CrudRepository<?, ?> findRepositoryForEntity(Class<?> entityClass) {
        return beanFactory.getBeansOfType(CrudRepository.class).values().stream()
                .filter(repo -> getEntityClass(repo).equals(entityClass))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No repository found for entity: " + entityClass.getName()));
    }

    private Class<?> getEntityClass(CrudRepository<?, ?> repository) {
        return Objects.requireNonNull(GenericTypeResolver.resolveTypeArguments(repository.getClass(), CrudRepository.class))[0];
    }
}
