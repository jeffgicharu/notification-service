package com.notify.template;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple template engine with {{variable}} placeholder substitution.
 * Templates are registered at startup and resolved at send time.
 */
@Component
@Slf4j
public class TemplateEngine {

    private final Map<String, Template> templates = new HashMap<>();

    @PostConstruct
    void init() {
        register("transaction_success",
                "M-Wallet Transaction",
                "{{txnId}} confirmed. KES {{amount}} sent to {{recipient}}. " +
                "New balance: KES {{balance}}. Ref: {{txnId}}");

        register("transaction_received",
                "M-Wallet Received",
                "You have received KES {{amount}} from {{sender}}. " +
                "New balance: KES {{balance}}. Ref: {{txnId}}");

        register("otp",
                "Verification Code",
                "Your verification code is {{code}}. Valid for {{expiry}} minutes. " +
                "Do not share this code with anyone.");

        register("welcome",
                "Welcome to M-Wallet",
                "Welcome {{name}}! Your M-Wallet account is now active. " +
                "Dial *384# to get started.");

        register("low_balance",
                "Low Balance Alert",
                "Your M-Wallet balance is KES {{balance}}. " +
                "Top up to continue enjoying our services.");

        register("loan_due",
                "Loan Reminder",
                "Your loan of KES {{amount}} is due on {{dueDate}}. " +
                "Repay via M-Wallet to avoid penalties.");

        register("promotion",
                "M-Wallet Offer",
                "{{message}}");

        register("password_reset",
                "Password Reset",
                "Your password reset code is {{code}}. " +
                "This code expires in {{expiry}} minutes.");

        log.info("Loaded {} notification templates", templates.size());
    }

    public void register(String id, String subject, String bodyTemplate) {
        templates.put(id, new Template(id, subject, bodyTemplate));
    }

    public ResolvedTemplate resolve(String templateId, Map<String, String> params) {
        Template template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Unknown template: " + templateId);
        }

        String subject = substitute(template.subject(), params);
        String body = substitute(template.body(), params);

        return new ResolvedTemplate(subject, body);
    }

    public boolean exists(String templateId) {
        return templates.containsKey(templateId);
    }

    public Map<String, Template> getAll() {
        return Map.copyOf(templates);
    }

    private String substitute(String text, Map<String, String> params) {
        if (params == null || params.isEmpty()) return text;

        String result = text;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    public record Template(String id, String subject, String body) {}
    public record ResolvedTemplate(String subject, String body) {}
}
