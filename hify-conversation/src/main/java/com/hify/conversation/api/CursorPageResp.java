package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CursorPageResp<T> {

    private List<T> records;
    private Long nextCursorId;
    private LocalDateTime nextCursorTime;
    private Boolean hasMore;

    public static <T> CursorPageResp<T> of(List<T> records, Long nextCursorId,
                                           LocalDateTime nextCursorTime, boolean hasMore) {
        CursorPageResp<T> resp = new CursorPageResp<>();
        resp.setRecords(records);
        resp.setNextCursorId(nextCursorId);
        resp.setNextCursorTime(nextCursorTime);
        resp.setHasMore(hasMore);
        return resp;
    }
}
