package me.pacphi.ai.resos.csv.impl;

import me.pacphi.ai.resos.csv.CsvEntityMapper;
import me.pacphi.ai.resos.csv.CsvMappingException;
import me.pacphi.ai.resos.csv.EntityMapper;
import me.pacphi.ai.resos.jdbc.AppUserEntity;
import me.pacphi.ai.resos.jdbc.AuthorityEntity;
import me.pacphi.ai.resos.jdbc.UserAuthorityEntity;
import me.pacphi.ai.resos.repository.AppUserRepository;
import me.pacphi.ai.resos.repository.AuthorityRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@CsvEntityMapper("user-authorities")
public class UserAuthorityMapper implements EntityMapper<UserAuthorityEntity> {

    private final AppUserRepository userRepository;
    private final AuthorityRepository authorityRepository;

    public UserAuthorityMapper(AppUserRepository userRepository, AuthorityRepository authorityRepository) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
    }

    @Override
    public UserAuthorityEntity mapFromCsv(String[] line) throws CsvMappingException {
        try {
            String username = line[0];
            String authorityName = line[1];

            // Look up user by username
            UUID userId = userRepository.findByUsername(username)
                    .map(AppUserEntity::getId)
                    .orElseThrow(() -> new CsvMappingException("User not found: " + username));

            // Look up authority by name
            UUID authorityId = authorityRepository.findByName(authorityName)
                    .map(AuthorityEntity::getId)
                    .orElseThrow(() -> new CsvMappingException("Authority not found: " + authorityName));

            var entity = new UserAuthorityEntity();
            entity.setUserId(userId);
            entity.setAuthorityId(authorityId);
            return entity;
        } catch (IllegalArgumentException | NullPointerException | ArrayIndexOutOfBoundsException e) {
            throw new CsvMappingException("Failed to map user authority from CSV", e);
        }
    }

    @Override
    public Class<UserAuthorityEntity> getEntityClass() {
        return UserAuthorityEntity.class;
    }
}
