package com.capstoneecho.echo_back.external.llm.canonical;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCanonicalLockRepository extends JpaRepository<UserCanonicalLock, Long> {

    Optional<UserCanonicalLock> findByUserIdAndTargetTypeAndTargetId(
            Long userId, CanonicalTargetType targetType, Long targetId);

    boolean existsByUserIdAndTargetTypeAndTargetId(
            Long userId, CanonicalTargetType targetType, Long targetId);
}
