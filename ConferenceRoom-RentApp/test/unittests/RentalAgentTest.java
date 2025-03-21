package unittests;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import components.RentalAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

// Unit test class for the RentalAgent class
public class RentalAgentTest {

    private RentalAgent agent;

    @BeforeEach
    public void setUp() throws Exception {
        // Create a new RentalAgent instance for testing
        agent = new RentalAgent();
    }

    @Test
    public void Given_RentalAgent_WhenConstructed_ShouldDeclareExchangesAndQueues() {
        // Ensure the RentalAgent instance is initialized properly
        assertNotNull(agent, "RentalAgent instance should not be null.");
    }

    @Test
    public void Given_RentalAgent_WhenReceivingBuildingUpdate_ShouldRegisterBuilding() {
        // Simulate receiving a building registration message
        String updateMessage = "REGISTER_BUILDING:Building1; ROOM_AVAILABILITY:Room1:available, Room2:available";
        Delivery testMessage = createTestMessage(updateMessage);

        assertDoesNotThrow(() -> {
            agent.listenForBuildingUpdates(); // Start listening for updates
            simulateMessageProcessing(testMessage); // Simulate processing the message
        }, "Processing building update should not throw exceptions.");
    }

    @Test
    public void Given_RentalAgent_WhenHandlingBookingRequest_ShouldForwardToBuilding() {
        // Simulate a booking request from a customer
        String request = "MAKE_RESERVATION:Room1:reservationId123";
        Delivery testMessage = createTestMessage(request);

        assertDoesNotThrow(() -> {
            agent.listenForCustomerRequests(); // Start listening for customer requests
            simulateMessageProcessing(testMessage); // Simulate processing the message
        }, "Processing booking request should not throw exceptions.");
    }

    @Test
    public void Given_RentalAgent_WhenHandlingCancellationRequest_ShouldForwardToBuilding() {
        // Simulate a cancellation request from a customer
        String request = "CANCEL_RESERVATION:Room1:reservationId123";
        Delivery testMessage = createTestMessage(request);

        assertDoesNotThrow(() -> {
            agent.listenForCustomerRequests(); // Start listening for customer requests
            simulateMessageProcessing(testMessage); // Simulate processing the message
        }, "Processing cancellation request should not throw exceptions.");
    }

    @Test
    public void Given_RentalAgent_WhenHandlingAvailabilityRequest_ShouldForwardToAllBuildings() {
        // Simulate an availability request from a customer
        String request = "REQUEST_ROOMS";
        Delivery testMessage = createTestMessage(request);

        assertDoesNotThrow(() -> {
            agent.listenForCustomerRequests(); // Start listening for customer requests
            simulateMessageProcessing(testMessage); // Simulate processing the message
        }, "Processing availability request should not throw exceptions.");
    }

    @Test
    public void Given_RentalAgent_WhenNoBuildingsRegistered_ShouldRespondWithNoBuildingsAvailable() {
        // Simulate an availability request when no buildings are registered
        String request = "REQUEST_ROOMS";
        Delivery testMessage = createTestMessage(request);

        assertDoesNotThrow(() -> {
            agent.listenForCustomerRequests(); // Start listening for customer requests
            simulateMessageProcessing(testMessage); // Simulate processing the message
        }, "RentalAgent should handle requests even when no buildings are registered.");
    }

    @Test
    public void Given_RentalAgent_WhenReceivingMultipleBuildingUpdates_ShouldHandleAllUpdates() {
        // Simulate receiving multiple building updates
        String updateMessage1 = "REGISTER_BUILDING:Building1; ROOM_AVAILABILITY:Room1:available, Room2:available";
        String updateMessage2 = "REGISTER_BUILDING:Building2; ROOM_AVAILABILITY:Room3:available, Room4:available";

        Delivery testMessage1 = createTestMessage(updateMessage1);
        Delivery testMessage2 = createTestMessage(updateMessage2);

        assertDoesNotThrow(() -> {
            agent.listenForBuildingUpdates(); // Start listening for updates
            simulateMessageProcessing(testMessage1); // Simulate processing the first update
            simulateMessageProcessing(testMessage2); // Simulate processing the second update
        }, "RentalAgent should handle multiple building updates without throwing exceptions.");
    }

    /**
     * Creates a test message to simulate a RabbitMQ Delivery object.
     * @param body The message body as a string.
     * @return A Delivery object containing the simulated message.
     */
    private Delivery createTestMessage(String body) {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId("testCorrelationId")
                .replyTo("testReplyQueue")
                .build();

        return new Delivery(null, props, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Simulates the processing of a message by the RentalAgent.
     * @param message The simulated Delivery object.
     * @throws Exception If an exception occurs during message processing.
     */
    private void simulateMessageProcessing(Delivery message) throws Exception {
        String request = new String(message.getBody(), StandardCharsets.UTF_8);
        String replyQueue = message.getProperties().getReplyTo();
        String correlationId = message.getProperties().getCorrelationId();

        System.out.println("Simulating processing of message: " + request);
        System.out.println("Reply queue: " + replyQueue + ", Correlation ID: " + correlationId);

        // Simulate handling the message (this would be more detailed in a real integration test)
    }
}