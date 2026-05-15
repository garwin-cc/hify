package com.hify.workflow.engine.executor;

import com.hify.common.http.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiCallNodeExecutorTest {

    @Test
    void should_resolve_body_and_extract_json_path_when_api_call_succeeds() {
        CapturingHttpClient httpClient = new CapturingHttpClient("{\"data\":{\"id\":42,\"name\":\"done\"}}");
        ApiCallNodeExecutor executor = new ApiCallNodeExecutor(httpClient);
        ExecutionContext ctx = new ExecutionContext(99L, "alice");

        executor.execute(
                new WorkflowNode("api", "API_CALL", "API 调用"),
                new ApiCallConfig(
                        "https://api.example.test/users",
                        "PATCH",
                        Map.of("X-User", "{{start.userMessage}}"),
                        "{\"name\":\"{{start.userMessage}}\"}",
                        "$.data.id",
                        "userId"),
                ctx);

        assertThat(httpClient.method).isEqualTo("PATCH");
        assertThat(httpClient.headers).containsEntry("X-User", "alice");
        assertThat(httpClient.body).isEqualTo("{\"name\":\"alice\"}");
        assertThat(ctx.get("api", "userId")).isEqualTo("42");
    }

    @Test
    void should_write_error_variable_when_api_call_fails() {
        CapturingHttpClient httpClient = new CapturingHttpClient("");
        httpClient.failure = new LlmApiException(LlmApiException.Type.UNKNOWN, 500, "LLM 请求失败（HTTP 500）");
        ApiCallNodeExecutor executor = new ApiCallNodeExecutor(httpClient);
        ExecutionContext ctx = new ExecutionContext(99L, "alice");

        assertThatThrownBy(() -> executor.execute(
                new WorkflowNode("api", "API_CALL", "API 调用"),
                new ApiCallConfig(
                        "https://api.example.test/users",
                        "DELETE",
                        Map.of(),
                        null,
                        null,
                        "response"),
                ctx))
                .hasMessageContaining("LLM 请求失败");

        assertThat(ctx.get("api", "error")).asString().contains("LLM 请求失败");
    }

    private static class CapturingHttpClient extends LlmHttpClient {

        private final String response;
        private String method;
        private Map<String, String> headers;
        private String body;
        private RuntimeException failure;

        private CapturingHttpClient(String response) {
            this.response = response;
        }

        @Override
        public String request(String method, String url, Map<String, String> headers, String body, int timeoutSeconds) {
            this.method = method;
            this.headers = headers;
            this.body = body;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}
