package com.notify.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Send SMS notification through HTTP returns ACCEPTED")
    void sendSms_throughHttp() throws Exception {
        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"SMS\",\"recipient\":\"+254711100001\",\"body\":\"Test\",\"idempotencyKey\":\"http-sms-001\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.channel").value("SMS"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    @DisplayName("Send email with template resolves variables")
    void sendEmail_withTemplate() throws Exception {
        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"EMAIL\",\"recipient\":\"test@example.com\",\"templateId\":\"otp\",\"templateParams\":{\"code\":\"123456\",\"expiry\":\"5\"},\"idempotencyKey\":\"http-email-001\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.subject").value("Verification Code"));
    }

    @Test
    @DisplayName("Bulk send queues all recipients")
    void bulkSend_queuesAll() throws Exception {
        mockMvc.perform(post("/api/notifications/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"SMS\",\"recipients\":[\"+254711100002\",\"+254711100003\"],\"body\":\"Bulk msg\",\"batchId\":\"http-bulk-001\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("Duplicate idempotency key returns same notification")
    void duplicate_returnsSame() throws Exception {
        String body = "{\"channel\":\"SMS\",\"recipient\":\"+254711100004\",\"body\":\"Dup test\",\"idempotencyKey\":\"http-dup-001\"}";

        MvcResult first = mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        MvcResult second = mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();

        Long firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        Long secondId = objectMapper.readTree(second.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(firstId, secondId);
    }

    @Test
    @DisplayName("Get notification by ID returns data")
    void getById_returnsData() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"PUSH\",\"recipient\":\"device-abc\",\"body\":\"Push test\",\"idempotencyKey\":\"http-get-001\"}"))
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/notifications/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipient").value("device-abc"));
    }

    @Test
    @DisplayName("Stats endpoint returns health status")
    void stats_returnsHealth() throws Exception {
        mockMvc.perform(get("/api/notifications/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.healthStatus").exists())
                .andExpect(jsonPath("$.data.totalNotifications").exists());
    }

    @Test
    @DisplayName("Templates endpoint lists all templates")
    void templates_listAll() throws Exception {
        mockMvc.perform(get("/api/notifications/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otp").exists())
                .andExpect(jsonPath("$.data.welcome").exists());
    }

    @Test
    @DisplayName("Channel health returns per-channel stats")
    void channelHealth_returnsPerChannel() throws Exception {
        // Generate some notifications first
        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"SMS\",\"recipient\":\"+254711100005\",\"body\":\"Health test\",\"idempotencyKey\":\"http-health-001\"}"));

        mockMvc.perform(get("/api/notifications/channels/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.SMS").exists())
                .andExpect(jsonPath("$.data.EMAIL").exists());
    }

    @Test
    @DisplayName("Cancel a queued notification")
    void cancel_queuedNotification() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"SMS\",\"recipient\":\"+254711100006\",\"body\":\"Cancel me\",\"idempotencyKey\":\"http-cancel-001\",\"scheduledAt\":\"2099-01-01T00:00:00\"}"))
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/notifications/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    @DisplayName("Scheduled endpoint returns pending scheduled notifications")
    void scheduled_returnsPending() throws Exception {
        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"SMS\",\"recipient\":\"+254711100007\",\"body\":\"Scheduled\",\"idempotencyKey\":\"http-sched-001\",\"scheduledAt\":\"2099-12-31T23:59:59\"}"));

        mockMvc.perform(get("/api/notifications/scheduled"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unknown template returns 400")
    void unknownTemplate_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"SMS\",\"recipient\":\"+254711100008\",\"templateId\":\"fake\",\"idempotencyKey\":\"http-fake-tpl\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Recipient notifications returns history")
    void recipientHistory_returnsData() throws Exception {
        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"SMS\",\"recipient\":\"+254711100009\",\"body\":\"History test\",\"idempotencyKey\":\"http-hist-001\"}"));

        mockMvc.perform(get("/api/notifications/recipient/+254711100009"))
                .andExpect(status().isOk());
    }
}
