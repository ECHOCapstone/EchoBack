package com.capstoneecho.echo_back.member.retention;

import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 탈퇴 후 grace period 가 지난 사용자를 hard delete 한다.
// 자식 데이터 (recordings / pronunciation_feedbacks / retry_attempts / chapter_progress / session_progress
// / sessions / social_accounts / daily_challenge_attempts / daily_challenge_rewards) 는 DB FK 의
// ON DELETE CASCADE 로 함께 정리된다 (V17 마이그레이션 참조).
@Service
public class UserRetentionService {

    private static final Logger log = LoggerFactory.getLogger(UserRetentionService.class);

    private final UserRepository userRepository;
    private final Clock clock;

    public UserRetentionService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // 한 번의 cleanup 사이클. 트랜잭션 1개에 묶어 부분 삭제로 인한 일관성 깨짐을 막는다.
    // 삭제 대상이 한 번에 너무 많으면 DB lock 시간이 길어지므로 호출 측이 batchSize 로 잘라 호출한다.
    @Transactional
    public int sweepExpired(int graceDays, int batchSize) {
        if (graceDays < 0) {
            throw new IllegalArgumentException("graceDays must be >= 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
        Instant threshold = clock.instant().minus(graceDays, ChronoUnit.DAYS);
        List<User> expired = userRepository.findExpiredDeleted(threshold, Limit.of(batchSize));
        if (expired.isEmpty()) {
            return 0;
        }
        for (User user : expired) {
            userRepository.delete(user);
            log.info("Hard-deleted user id={} after grace period (deletedAt={})",
                    user.getId(), user.getDeletedAt());
        }
        return expired.size();
    }
}
