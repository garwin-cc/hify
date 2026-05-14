package com.hify.common.task;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;

public class TaskRejectedException extends BizException {

    public TaskRejectedException(String message) {
        super(ErrorCode.TASK_QUEUE_FULL, message);
    }
}
