CREATE SCHEMA IF NOT EXISTS notification;
CREATE TABLE notifications
(
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT       NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    channel    VARCHAR(16)  NOT NULL,
    recipient  VARCHAR(255) NOT NULL,
    subject    VARCHAR(255),
    content    TEXT,
    status     VARCHAR(16)  NOT NULL,
    sent_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT uk_notifications_order_type_channel UNIQUE (order_id, type, channel)
);
CREATE INDEX idx_notifications_order_id ON notifications (order_id);
CREATE INDEX idx_notifications_status ON notifications (status);
