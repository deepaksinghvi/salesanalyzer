package com.qcom.salesanalyzer.forecaster.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ArgoWorkflowService {

    @Value("${app.argo.server-url}")
    private String argoServerUrl;

    @Value("${app.argo.namespace}")
    private String namespace;

    @Value("${app.argo.workflow-template.prophet}")
    private String prophetTemplate;

    @Value("${app.argo.workflow-template.xgboost}")
    private String xgboostTemplate;

    @Value("${app.argo.service-account}")
    private String serviceAccount;

    private final HttpClient httpClient = buildTrustAllHttpClient();

    private static HttpClient buildTrustAllHttpClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new java.security.SecureRandom());
            return HttpClient.newBuilder().sslContext(sslContext).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all HTTP client", e);
        }
    }
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public String submitForecastWorkflow(String tenantId, String algorithm) throws Exception {
        Map<String, Object> workflow = buildWorkflowPayload(tenantId, algorithm);
        Map<String, Object> body = Map.of("workflow", workflow);
        String json = jsonMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(argoServerUrl + "/api/v1/workflows/" + namespace))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Argo server returned error " + response.statusCode() + ": " + response.body());
        }

        log.info("Argo workflow submitted for tenant {}, response: {}", tenantId, response.statusCode());
        Map<?, ?> responseMap = jsonMapper.readValue(response.body(), Map.class);
        Map<?, ?> metadata = (Map<?, ?>) responseMap.get("metadata");
        return metadata != null ? (String) metadata.get("name") : "unknown";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildWorkflowPayload(String tenantId, String algorithm) {
        String templateName = "xgboost".equalsIgnoreCase(algorithm) ? xgboostTemplate : prophetTemplate;
        String prefix = "xgboost".equalsIgnoreCase(algorithm) ? "xgb-forecast-" : "sales-forecast-";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generateName", prefix + tenantId.substring(0, 8) + "-");
        metadata.put("namespace", namespace);

        Map<String, Object> argument = new HashMap<>();
        argument.put("name", "tenant-id");
        argument.put("value", tenantId);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", List.of(argument));

        Map<String, Object> spec = new HashMap<>();
        spec.put("workflowTemplateRef", Map.of("name", templateName));
        spec.put("arguments", arguments);
        spec.put("serviceAccountName", serviceAccount);

        Map<String, Object> workflow = new HashMap<>();
        workflow.put("apiVersion", "argoproj.io/v1alpha1");
        workflow.put("kind", "Workflow");
        workflow.put("metadata", metadata);
        workflow.put("spec", spec);

        return workflow;
    }
}
