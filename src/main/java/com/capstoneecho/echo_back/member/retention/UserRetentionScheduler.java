package com.capstoneecho.echo_back.member.retention;

import com.capstoneecho.echo_back.global.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 매일 새벽 한 번, grace period 가 지난 탈퇴 사용자를 정리한다.
// 운영 시각 (Asia/Seoul) 의 한산한 시간대에 돌도록 cron 을 03:30 에 둔다 — DB lock 영향 최소화.
// 한 번의 sweep 에서 너무 많은 row 를 들고 오면 트랜잭션이 길어지므로 BATCH_SIZE 로 잘라 처리한다.
@Component
public class UserRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserRetentionScheduler.class);

    // 한 사이클당 최대 처리 사용자 수. 한 사용자당 자식 데이터 (recordings 등) 가 수십~수백 row 이므로
    // 100 으로 둬 트랜잭션 시간을 수 초 이내로 묶는다.
    private static final int BATCH_SIZE = 100;

    private final UserRetentionService retentionService;
    private final int graceDays;

    public UserRetentionScheduler(UserRetentionService retentionService, AppProperties appProperties) {
        this.retentionService = retentionService;
        AppProperties.Member member = appProperties.member();
        AppProperties.Member.Retention retention = member == null ? null : member.retention();
        if (retention == null) {
            throw new IllegalStateException("app.member.retention.grace-days must be configured");
        }
        if (retention.graceDays() < 0) {
            throw new IllegalStateException("app.member.retention.grace-days must be >= 0");
        }
        this.graceDays = retention.graceDays();
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void sweep() {
        int total = 0;
        // 한 사이클 안에서 잘린 배치들을 연속으로 돌려 잔여가 batchSize 미만이 될 때까지 정리한다.
        // sweep 자체가 하루 1회만 도는 cron 이라 무한 루프 위험은 없다.
        while (true) {
            int removed = retentionService.sweepExpired(graceDays, BATCH_SIZE);
            total += removed;
            if (removed < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("UserRetentionScheduler sweep done: removed={} graceDays={}", total, graceDays);
        }
    }
}
