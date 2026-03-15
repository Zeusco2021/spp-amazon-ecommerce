package com.ecommerce.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Email service for sending transactional notifications.
 * Uses Spring Mail (SendGrid/AWS SES compatible via SMTP).
 * Requirements: 12.1, 12.2, 12.3, 12.4, 12.6
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${notification.email.from:noreply@ecommerce.com}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an order confirmation email.
     * Requirements: 12.1
     */
    public void sendOrderConfirmation(String toEmail, String orderNumber, BigDecimal total) {
        String subject = "Order Confirmed - " + orderNumber;
        String body = buildOrderConfirmationBody(orderNumber, total);
        sendEmail(toEmail, subject, body);
        logger.info("Order confirmation email sent for orderNumber={}", orderNumber);
    }

    /**
     * Sends a shipping notification email with tracking number.
     * Requirements: 12.2
     */
    public void sendShippingNotification(String toEmail, String orderNumber, String trackingNumber) {
        String subject = "Your Order Has Shipped - " + orderNumber;
        String body = buildShippingNotificationBody(orderNumber, trackingNumber);
        sendEmail(toEmail, subject, body);
        logger.info("Shipping notification email sent for orderNumber={}, trackingNumber={}", orderNumber, trackingNumber);
    }

    /**
     * Sends a delivery confirmation email.
     * Requirements: 12.3
     */
    public void sendDeliveryConfirmation(String toEmail, String orderNumber) {
        String subject = "Your Order Has Been Delivered - " + orderNumber;
        String body = buildDeliveryConfirmationBody(orderNumber);
        sendEmail(toEmail, subject, body);
        logger.info("Delivery confirmation email sent for orderNumber={}", orderNumber);
    }

    /**
     * Sends an order cancellation email with reason.
     * Requirements: 12.4
     */
    public void sendCancellationNotification(String toEmail, String orderNumber, String reason) {
        String subject = "Order Cancelled - " + orderNumber;
        String body = buildCancellationBody(orderNumber, reason);
        sendEmail(toEmail, subject, body);
        logger.info("Cancellation email sent for orderNumber={}", orderNumber);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            logger.error("Failed to send email to={}, subject={}: {}", to, subject, e.getMessage(), e);
            throw e;
        }
    }

    private String buildOrderConfirmationBody(String orderNumber, BigDecimal total) {
        return String.format(
                "Thank you for your order!\n\n" +
                "Order Number: %s\n" +
                "Total: $%s\n\n" +
                "We are processing your order and will notify you when it ships.\n\n" +
                "Thank you for shopping with us!",
                orderNumber, total != null ? total.toPlainString() : "N/A");
    }

    private String buildShippingNotificationBody(String orderNumber, String trackingNumber) {
        return String.format(
                "Great news! Your order has shipped.\n\n" +
                "Order Number: %s\n" +
                "Tracking Number: %s\n\n" +
                "You can use the tracking number to monitor your delivery.\n\n" +
                "Thank you for shopping with us!",
                orderNumber, trackingNumber != null ? trackingNumber : "N/A");
    }

    private String buildDeliveryConfirmationBody(String orderNumber) {
        return String.format(
                "Your order has been delivered!\n\n" +
                "Order Number: %s\n\n" +
                "We hope you enjoy your purchase. If you have any issues, please contact our support team.\n\n" +
                "Thank you for shopping with us!",
                orderNumber);
    }

    private String buildCancellationBody(String orderNumber, String reason) {
        return String.format(
                "Your order has been cancelled.\n\n" +
                "Order Number: %s\n" +
                "Reason: %s\n\n" +
                "If you have any questions, please contact our support team.\n\n" +
                "Thank you for your understanding.",
                orderNumber, reason != null ? reason : "Cancelled by request");
    }
}
