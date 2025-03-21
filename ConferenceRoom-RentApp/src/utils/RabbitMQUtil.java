package utils;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RabbitMQUtil {

    private static final String RABBITMQ_HOST = "localhost";
    private static final String RABBITMQ_USERNAME = "guest";
    private static final String RABBITMQ_PASSWORD = "guest";
    private static final int RABBITMQ_PORT = 5672;

    // Method to establish and return a RabbitMQ connection
    public static Connection getConnection() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        factory.setUsername(RABBITMQ_USERNAME);
        factory.setPassword(RABBITMQ_PASSWORD);
        factory.setPort(RABBITMQ_PORT);  // Set the RabbitMQ port

        // Return a new connection using these settings
        return factory.newConnection();
    }

    // Method to declare a direct exchange
    public static void declareDirectExchange(Channel channel, String exchangeName) throws IOException {
        channel.exchangeDeclare(exchangeName, "direct");
    }

    // Method to declare a fanout exchange
    public static void declareFanoutExchange(Channel channel, String exchangeName) throws IOException {
        channel.exchangeDeclare(exchangeName, "fanout");
    }

    // Method to declare a queue and bind it to an exchange with a specific routing key
    public static void declareAndBindQueue(Channel channel, String queueName, String exchangeName, String routingKey) throws IOException {
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, exchangeName, routingKey);
    }

    // Method to declare a temporary queue (used for replies)
    public static String declareTemporaryQueue(Channel channel) throws IOException {
        return channel.queueDeclare().getQueue();
    }

    // Method to publish a message with optional properties (e.g., replyTo, correlationId)
    public static void publishMessage(Channel channel, String exchange, String routingKey, String message, String replyQueue, String correlationId) throws IOException {
        AMQP.BasicProperties props = new AMQP.BasicProperties
                .Builder()
                .replyTo(replyQueue)  // Set reply-to queue
                .correlationId(correlationId)  // Set correlation ID
                .build();

        channel.basicPublish(exchange, routingKey, props, message.getBytes(StandardCharsets.UTF_8));
    }

    // Method to consume messages from a queue and handle the delivery using a callback
    public static void consumeMessages(Channel channel, String queueName, DeliverCallback deliverCallback) throws IOException {
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }

    // Method to send a reply to a message
    public static void sendReply(Channel channel, String replyToQueue, String correlationId, String replyMessage) throws IOException {
        if (replyToQueue != null) {
            AMQP.BasicProperties replyProps = new AMQP.BasicProperties.Builder()
                    .correlationId(correlationId)  // Include correlation ID in the reply
                    .build();
            channel.basicPublish("", replyToQueue, replyProps, replyMessage.getBytes(StandardCharsets.UTF_8));
        }
    }
}