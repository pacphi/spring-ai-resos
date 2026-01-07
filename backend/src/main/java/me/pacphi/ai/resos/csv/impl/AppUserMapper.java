package me.pacphi.ai.resos.csv.impl;

import me.pacphi.ai.resos.csv.CsvEntityMapper;
import me.pacphi.ai.resos.csv.CsvMappingException;
import me.pacphi.ai.resos.csv.EntityMapper;
import me.pacphi.ai.resos.jdbc.AppUserEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;

@CsvEntityMapper("users")
public class AppUserMapper implements EntityMapper<AppUserEntity> {

    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    @Override
    public AppUserEntity mapFromCsv(String[] line) throws CsvMappingException {
        try {
            var entity = new AppUserEntity();
            entity.setUsername(line[0]);

            // BCrypt the password from CSV
            String rawPassword = line[1];
            entity.setPassword(passwordEncoder.encode(rawPassword));

            entity.setEmail(line[2]);
            entity.setEnabled(Boolean.parseBoolean(line[3]));
            entity.setAccountNonExpired(Boolean.parseBoolean(line[4]));
            entity.setAccountNonLocked(Boolean.parseBoolean(line[5]));
            entity.setCredentialsNonExpired(Boolean.parseBoolean(line[6]));
            entity.setCreatedAt(OffsetDateTime.now());
            entity.setUpdatedAt(OffsetDateTime.now());

            return entity;
        } catch (IllegalArgumentException | NullPointerException | ArrayIndexOutOfBoundsException e) {
            throw new CsvMappingException("Failed to map app user from CSV", e);
        }
    }

    @Override
    public Class<AppUserEntity> getEntityClass() {
        return AppUserEntity.class;
    }
}
