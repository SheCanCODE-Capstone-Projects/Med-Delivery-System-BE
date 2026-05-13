package com.meddelivery.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SendGridEmailService {

    private final SendGrid sendGrid;
    private final String fromEmail;

    public SendGridEmailService(
            @Value("${app.sendgrid.api-key}") String apiKey,
            @Value("${app.mail.from}") String fromEmail) {
        this.sendGrid = new SendGrid(apiKey);
        this.fromEmail = fromEmail;
        log.info("SendGrid Email Service initialized with from: {}", fromEmail);
    }

    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            Email from = new Email(fromEmail, "MedDelivery");
            Email toEmail = new Email(to);
            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, toEmail, content);
            
            // Add reply-to to improve deliverability
            mail.setReplyTo(new Email(fromEmail));

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("✅ Email sent successfully to: {} (Status: {})", to, response.getStatusCode());
            } else {
                log.error("❌ SendGrid API error: Status {}, Body: {}", 
                    response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }
}
