package com.hify.knowledge.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.knowledge.infra.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentRecoveryRunner implements ApplicationRunner {

    private static final int STALE_PROCESSING_HOURS = 1;

    private final KnowledgeDocumentMapper documentMapper;

    @Override
    public void run(ApplicationArguments args) {
        int updated = documentMapper.update(null, Wrappers.lambdaUpdate(KnowledgeDocumentPo.class)
                .eq(KnowledgeDocumentPo::getParseStatus, "PROCESSING")
                .lt(KnowledgeDocumentPo::getUpdatedAt, LocalDateTime.now().minusHours(STALE_PROCESSING_HOURS))
                .set(KnowledgeDocumentPo::getParseStatus, "FAILED")
                .set(KnowledgeDocumentPo::getProcessStage, "FAILED")
                .set(KnowledgeDocumentPo::getProcessProgress, 0)
                .set(KnowledgeDocumentPo::getErrorCode, "INTERNAL_ERROR")
                .set(KnowledgeDocumentPo::getFailedStage, "PROCESSING")
                .set(KnowledgeDocumentPo::getRetryable, 1)
                .set(KnowledgeDocumentPo::getCancelRequested, 0)
                .set(KnowledgeDocumentPo::getErrorMessage, "服务重启或任务超时，文档处理已中断，请重试"));
        if (updated > 0) {
            log.warn("marked stale processing knowledge documents as failed count={}", updated);
        }
    }
}
