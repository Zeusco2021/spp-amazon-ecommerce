package com.ecommerce.common.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    // Order topics
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_SHIPPED = "order.shipped";
    public static final String ORDER_DELIVERED = "order.delivered";

    // Inventory topics
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_UNAVAILABLE = "inventory.unavailable";
    public static final String INVENTORY_UPDATED = "inventory.updated";

    // Payment topics
    public static final String PAYMENT_SUCCESS = "payment.success";
    public static final String PAYMENT_FAILED = "payment.failed";

    // Product topics
    public static final String PRODUCT_CREATED = "product.created";
    public static final String PRODUCT_UPDATED = "product.updated";
    public static final String PRODUCT_DELETED = "product.deleted";
    public static final String CATEGORY_UPDATED = "category.updated";

    // Notification topics
    public static final String NOTIFICATION_EMAIL = "notification.email";
    public static final String NOTIFICATION_SMS = "notification.sms";

    // User activity topics
    public static final String USER_ACTIVITY = "user.activity";
}
