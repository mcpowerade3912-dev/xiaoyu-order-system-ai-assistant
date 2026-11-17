package com.xiaoyu.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyu.Dao.AiOperationLogDao;
import com.xiaoyu.common.BusinessException;
import com.xiaoyu.dto.AiAssistantRequest;
import com.xiaoyu.pojo.AiOperationLog;
import com.xiaoyu.service.AiAssistantService;
import com.xiaoyu.service.AiToolService;
import com.xiaoyu.service.SystemSettingsService;
import com.xiaoyu.utils.UserContext;
import com.xiaoyu.vo.AiAssistantVO;
import com.xiaoyu.vo.AiToolEvidenceVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {
    private static final Logger log = LoggerFactory.getLogger(AiAssistantServiceImpl.class);
    private static final String SYSTEM_INSTRUCTIONS =
            "你是订单管理系统的管理员运营助手。你只能依据工具返回的数据回答，"
                    + "不得编造订单、库存、金额或趋势。回答使用简洁中文，先给结论，再给关键明细。"
                    + "如果数据不足，明确说明。不得执行写操作，不得提供或推断客户手机号、详细地址等敏感信息。"
                    + "金额保留两位小数，订单状态使用：0待付款、1待发货、2已发货、3已完成、4已取消。";

    @Resource
    private AiToolService aiToolService;
    @Resource
    private AiOperationLogDao aiOperationLogDao;
    @Resource
    private UserContext userContext;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private SystemSettingsService systemSettingsService;

    @Value("${ai.openai.api-key:}")
    private String apiKey;
    @Value("${ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;
    @Value("${ai.openai.model:gpt-4.1-mini}")
    private String model;
    @Value("${ai.openai.timeout-seconds:60}")
    private Integer timeoutSeconds;
    @Value("${ai.openai.max-tool-rounds:3}")
    private Integer maxToolRounds;
    @Value("${ai.rate-limit-per-minute:30}")
    private Integer rateLimitPerMinute;

    private final Map<Long, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    @Override
    public AiAssistantVO ask(AiAssistantRequest request) {
        Long adminUserId = userContext.getUserId();
        enforceRateLimit(adminUserId);
        AiRuntimeConfig config = currentAiConfig();

        String question = request.getMessage().trim();
        long start = System.currentTimeMillis();
        String errorMessage = null;
        AiAssistantVO result;
        try {
            if (config.isConfigured()) {
                try {
                    result = askOpenAi(question, config);
                } catch (Exception e) {
                    errorMessage = sanitizeError(e);
                    log.warn("OpenAI 运营助手调用失败，切换到本地摘要模式: {}", errorMessage);
                    AiToolService.LocalAnalysis fallback = aiToolService.localAnalyze(question);
                    result = buildLocalResult(fallback);
                    result.setAnswer("AI 服务暂时不可用，已返回本地业务摘要。\n\n" + result.getAnswer());
                }
            } else {
                result = buildLocalResult(aiToolService.localAnalyze(question));
            }
        } catch (Exception e) {
            errorMessage = sanitizeError(e);
            log.error("运营助手处理失败", e);
            throw new BusinessException("运营助手暂时不可用，请稍后重试");
        }

        long duration = System.currentTimeMillis() - start;
        result.setDurationMs(duration);
        result.setGeneratedAt(LocalDateTime.now());
        result.setConfigured(config.isConfigured());
        writeAuditLog(adminUserId, question, result, errorMessage);
        return result;
    }

    @Override
    public Map<String, Object> getStatus() {
        AiRuntimeConfig config = currentAiConfig();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", config.isConfigured());
        status.put("mode", config.isConfigured() ? "openai" : "local");
        status.put("model", config.isConfigured() ? config.getModel() : "local-rule");
        status.put("description", config.isConfigured()
                ? "已配置 OpenAI 运营助手"
                : "AI 未启用或未配置 API Key，当前使用本地业务摘要模式");
        return status;
    }

    private AiAssistantVO askOpenAi(String question, AiRuntimeConfig config) throws Exception {
        List<Map<String, Object>> tools = buildTools();
        List<AiToolEvidenceVO> evidence = new ArrayList<>();
        List<Object> conversation = new ArrayList<>();
        conversation.add(Map.of("role", "user", "content", question));
        JsonNode response = sendResponse(config, Map.of(
                "model", config.getModel(),
                "instructions", SYSTEM_INSTRUCTIONS,
                "input", conversation,
                "tools", tools,
                "tool_choice", "auto",
                "store", false,
                "max_output_tokens", 1200));

        for (int round = 0; round < maxToolRounds; round++) {
            List<JsonNode> functionCalls = findFunctionCalls(response);
            if (functionCalls.isEmpty()) {
                AiAssistantVO result = new AiAssistantVO();
                result.setAnswer(extractAnswer(response));
                result.setMode("openai");
                result.setModel(config.getModel());
                result.setEvidence(evidence);
                return result;
            }

            List<Map<String, Object>> toolOutputs = new ArrayList<>();
            for (JsonNode call : functionCalls) {
                conversation.add(objectMapper.convertValue(call,
                        new TypeReference<Map<String, Object>>() {
                        }));
                String name = call.path("name").asText();
                Map<String, Object> arguments = parseArguments(call.path("arguments").asText());
                Map<String, Object> toolResult = aiToolService.execute(name, arguments);
                evidence.add(new AiToolEvidenceVO(name, arguments, toolResult));
                toolOutputs.add(Map.of(
                        "type", "function_call_output",
                        "call_id", call.path("call_id").asText(),
                        "output", objectMapper.writeValueAsString(toolResult)));
            }
            conversation.addAll(toolOutputs);

            Map<String, Object> followUp = new LinkedHashMap<>();
            followUp.put("model", config.getModel());
            followUp.put("instructions", SYSTEM_INSTRUCTIONS);
            followUp.put("input", conversation);
            followUp.put("tools", tools);
            followUp.put("tool_choice", "auto");
            followUp.put("store", false);
            followUp.put("max_output_tokens", 1200);
            response = sendResponse(config, followUp);
        }
        throw new IllegalStateException("AI 工具调用轮次超过限制");
    }

    private JsonNode sendResponse(AiRuntimeConfig config, Map<String, Object> body) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(config.getTimeoutSeconds(), 15)))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(config.getBaseUrl()) + "/responses"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI Responses API HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 300));
        }
        return objectMapper.readTree(response.body());
    }

    private List<Map<String, Object>> buildTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(functionTool("get_business_overview",
                "获取今日成交、累计成交、各状态订单数量、在售商品数量和低库存商品数量。",
                objectSchema(Collections.emptyMap())));

        Map<String, Object> orderProperties = new LinkedHashMap<>();
        orderProperties.put("status", property("integer", "订单状态：0待付款、1待发货、2已发货、3已完成、4已取消",
                Map.of("minimum", 0, "maximum", 4)));
        orderProperties.put("minAmount", property("number", "最小实付金额", Map.of("minimum", 0)));
        orderProperties.put("createdAfter", property("string", "创建日期下限，格式 yyyy-MM-dd", Map.of()));
        orderProperties.put("orderKeyword", property("string", "订单号关键字", Map.of()));
        orderProperties.put("limit", property("integer", "最多返回条数，1-50", Map.of("minimum", 1, "maximum", 50)));
        tools.add(functionTool("query_orders",
                "按状态、金额、日期或订单号查询订单。只读，不返回客户联系方式。",
                objectSchema(orderProperties)));

        Map<String, Object> stockProperties = new LinkedHashMap<>();
        stockProperties.put("threshold", property("integer", "库存阈值，包含等于阈值的商品",
                Map.of("minimum", 0)));
        stockProperties.put("limit", property("integer", "最多返回条数，1-50",
                Map.of("minimum", 1, "maximum", 50)));
        tools.add(functionTool("query_low_stock_goods",
                "查询在售且库存不高于指定阈值的商品。",
                objectSchema(stockProperties)));

        Map<String, Object> trendProperties = new LinkedHashMap<>();
        trendProperties.put("days", property("integer", "统计最近多少天，1-90",
                Map.of("minimum", 1, "maximum", 90)));
        tools.add(functionTool("get_sales_trend",
                "获取最近若干天已支付、已发货和已完成订单的每日成交趋势。",
                objectSchema(trendProperties)));
        return tools;
    }

    private Map<String, Object> functionTool(String name, String description,
                                              Map<String, Object> parameters) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("name", name);
        tool.put("description", description);
        tool.put("parameters", parameters);
        return tool;
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> property(String type, String description,
                                         Map<String, Object> constraints) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        property.putAll(constraints);
        return property;
    }

    private List<JsonNode> findFunctionCalls(JsonNode response) {
        List<JsonNode> calls = new ArrayList<>();
        JsonNode output = response.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                if ("function_call".equals(item.path("type").asText())) {
                    calls.add(item);
                }
            }
        }
        return calls;
    }

    private String extractAnswer(JsonNode response) {
        JsonNode outputText = response.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }
        JsonNode output = response.path("output");
        StringBuilder answer = new StringBuilder();
        if (output.isArray()) {
            for (JsonNode item : output) {
                if (!"message".equals(item.path("type").asText())) {
                    continue;
                }
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    if ("output_text".equals(part.path("type").asText())
                            && part.path("text").isTextual()) {
                        if (answer.length() > 0) {
                            answer.append('\n');
                        }
                        answer.append(part.path("text").asText());
                    }
                }
            }
        }
        if (answer.length() == 0) {
            throw new IllegalStateException("OpenAI 返回结果中没有可展示的文本");
        }
        return answer.toString();
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("AI 工具参数格式不正确", e);
        }
    }

    private AiAssistantVO buildLocalResult(AiToolService.LocalAnalysis analysis) {
        AiAssistantVO result = new AiAssistantVO();
        result.setAnswer(analysis.getAnswer());
        result.setMode("local");
        result.setModel("local-rule");
        result.setEvidence(analysis.getEvidence());
        return result;
    }

    private AiRuntimeConfig currentAiConfig() {
        String environmentKey = apiKey == null ? "" : apiKey.trim();
        String effectiveKey = systemSettingsService.getAiApiKey(environmentKey);
        boolean enabled = systemSettingsService.isAiEnabled(true);
        return new AiRuntimeConfig(
                enabled && effectiveKey != null && !effectiveKey.isBlank(),
                effectiveKey == null ? "" : effectiveKey.trim(),
                systemSettingsService.getAiBaseUrl(baseUrl),
                systemSettingsService.getAiModel(model),
                systemSettingsService.getAiTimeoutSeconds(timeoutSeconds));
    }

    private void enforceRateLimit(Long userId) {
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000;
        Deque<Long> window = requestWindows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.pollFirst();
            }
            if (window.size() >= rateLimitPerMinute) {
                throw new BusinessException("42901", "AI 助手调用过于频繁，请稍后再试");
            }
            window.addLast(now);
        }
    }

    private void writeAuditLog(Long adminUserId, String question,
                               AiAssistantVO result, String errorMessage) {
        try {
            AiOperationLog audit = new AiOperationLog();
            audit.setAdminUserId(adminUserId);
            audit.setQuestion(truncate(question, 500));
            audit.setAnswer(truncate(result.getAnswer(), 5000));
            audit.setMode(result.getMode());
            audit.setModel(result.getModel());
            audit.setToolCalls(truncate(toJson(result.getEvidence()), 4000));
            audit.setSuccess(1);
            audit.setErrorMessage(truncate(errorMessage, 500));
            audit.setDurationMs(result.getDurationMs());
            audit.setCreateTime(LocalDateTime.now());
            aiOperationLogDao.insert(audit);
        } catch (Exception e) {
            log.warn("AI 运营助手审计日志写入失败: {}", e.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String sanitizeError(Exception e) {
        if (e instanceof HttpTimeoutException) {
            return "请求 OpenAI 超时";
        }
        return truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 400);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Data
    @AllArgsConstructor
    private static class AiRuntimeConfig {
        private boolean configured;
        private String apiKey;
        private String baseUrl;
        private String model;
        private int timeoutSeconds;
    }
}
