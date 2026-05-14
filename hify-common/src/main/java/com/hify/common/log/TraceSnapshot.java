package com.hify.common.log;

import java.util.Map;

public record TraceSnapshot(Map<String, String> mdc) {
}
