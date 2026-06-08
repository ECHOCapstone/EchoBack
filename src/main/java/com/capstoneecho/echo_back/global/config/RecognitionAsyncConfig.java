package com.capstoneecho.echo_back.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 음소 인식(/transcribe) HTTP 호출을 canonical 생성과 겹쳐 실행하기 위한 전용 스레드 풀.
// canonical 을 요구하지 않는 모델(baseline·slplab)에 한해, transcribe 를 이 풀에서 비동기로 돌리고
// 호출 스레드는 canonical 생성을 동시에 진행한다(PhonemeRecognizer 참고).
//
// 풀이 포화되면 CallerRunsPolicy 로 호출 스레드가 직접 실행한다 — 병렬화 이득만 잃을 뿐
// 작업이 거부되거나 큐가 무한히 쌓여 메모리가 포화되지 않는다.
@Configuration
public class RecognitionAsyncConfig {

    @Bean
    public Executor recognitionTranscribeExecutor(
            @Value("${app.recognition.transcribe-pool-core:4}") int corePoolSize,
            @Value("${app.recognition.transcribe-pool-max:16}") int maxPoolSize,
            @Value("${app.recognition.transcribe-queue-capacity:64}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("recog-transcribe-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 종료 시 진행 중인 인식 호출이 끊기지 않도록 대기 후 닫는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
