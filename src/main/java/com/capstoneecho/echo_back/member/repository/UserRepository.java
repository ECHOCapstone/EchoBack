package com.capstoneecho.echo_back.member.repository;

import com.capstoneecho.echo_back.member.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // 탈퇴 시각이 threshold 이전(또는 같음) 인 사용자. retention 스케줄러가 hard-delete 대상으로 사용한다.
    // 한 번에 너무 많은 row 를 잡지 않도록 호출 측에서 batchSize 로 잘라 부른다.
    @Query("""
            SELECT u FROM User u
             WHERE u.deletedAt IS NOT NULL
               AND u.deletedAt <= :threshold
             ORDER BY u.deletedAt ASC
            """)
    List<User> findExpiredDeleted(@Param("threshold") Instant threshold, Limit limit);
}
