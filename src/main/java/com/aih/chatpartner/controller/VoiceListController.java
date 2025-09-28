package com.aih.chatpartner.controller;

import com.aih.chatpartner.config.QiniuConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@RequestMapping("/voice")
public class VoiceListController {

    private final QiniuConfig qiniuConfig;
    private final RestTemplate restTemplate;

    @Autowired
    public VoiceListController(QiniuConfig qiniuConfig) {
        this.qiniuConfig = qiniuConfig;
        this.restTemplate = new RestTemplate();
    }

    @GetMapping("/list")
    public ResponseEntity<String> listVoices() {
        try {
            String base = qiniuConfig.getBaseUrl();
            if (base == null || base.isBlank()) {
                base = "https://openai.qiniu.com/v1";
            }
            String url = base.endsWith("/") ? (base + "voice/list") : (base + "/voice/list");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + qiniuConfig.getApiKey());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            HttpHeaders outHeaders = new HttpHeaders();
            outHeaders.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(resp.getBody(), outHeaders, resp.getStatusCode());
        } catch (Exception e) {
            log.error("Fetch Qiniu voice list error", e);
            return ResponseEntity.status(502).body("{\"error\":\"voice list failed: " + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
