package components;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import utils.RabbitMQUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RentalAgent extends Thread {
    private static final String EXCHANGE_BUILDINGS = "buildings_exchange";
    private static final String EXCHANGE_BUILDING_REQUESTS = "building_requests_exchange";
    private static final String EXCHANGE_AGENTS = "agents_exchange";
    private static final String QUEUE_CUSTOMER_BUILDING_REQUESTS = "customer_requests_queue";
    private static final String QUEUE_CUSTOMER_AVAILABILITY_REQUESTS = "customer_availability_requests_queue";
    private static final String QUEUE_BUILDING_RESPONSES = "building_responses_queue";
    private static final String QUEUE_CUSTOMER_RESPONSES = "agents_reply_queue";
    private static final String QUEUE_CUSTOMER_SPECIFIC_RESPONSES = "customer_specific_responses_queue";
    private static final String ROUTING_KEY_AGENT_REPLY = "agents_reply";

    private final Channel channel;
    private final Set<String> knownBuildings;
    private final Map<String, String> knownCustomersAndTheirCorrelationIDs;
    private final Map<String, String> knownCorrelationIdRequestsAndTheirCustomers;

    public RentalAgent() throws Exception {
        this.channel = RabbitMQUtil.getConnection().createChannel();
        this.knownBuildings = new HashSet<>();
        this.knownCorrelationIdRequestsAndTheirCustomers = new ConcurrentHashMap<>();
        this.knownCustomersAndTheirCorrelationIDs = new ConcurrentHashMap<>();

        // Declare necessary exchanges and queues
        RabbitMQUtil.declareFanoutExchange(channel, EXCHANGE_BUILDINGS);
        RabbitMQUtil.declareDirectExchange(channel, EXCHANGE_AGENTS);

        RabbitMQUtil.declareAndBindQueue(channel, QUEUE_CUSTOMER_AVAILABILITY_REQUESTS, EXCHANGE_AGENTS, "building_broadcast");
        RabbitMQUtil.declareAndBindQueue(channel, QUEUE_BUILDING_RESPONSES, EXCHANGE_BUILDINGS, "");

        channel.queueDeclare(QUEUE_CUSTOMER_BUILDING_REQUESTS, true, false, false, null);
        channel.queueDeclare(QUEUE_CUSTOMER_SPECIFIC_RESPONSES, true, false, false, null);


        // Start listening for messages
        listenForBuildingUpdates();
        listenForCustomerRequests();
        listenForBuildingResponses();
        listenForCustomerSpecificResponses();
    }

    public void listenForBuildingUpdates() throws IOException {
        String updatesQueue = "rental_agent_updates_queue"; // Queue for receiving all building updates
        channel.queueDeclare(updatesQueue, true, false, false, null);

        channel.queueBind(updatesQueue, EXCHANGE_BUILDINGS, "");

        DeliverCallback updateCallback = (consumerTag, message) -> {
            String updateMessage = new String(message.getBody(), StandardCharsets.UTF_8);
            System.out.println("RentalAgent received update: " + updateMessage);

            if (updateMessage.startsWith("REGISTER_BUILDING:")) {
                String buildingName = updateMessage.split(";")[0].split(":")[1];
                if (knownBuildings.add(buildingName)) {
                    System.out.println("RentalAgent registered new building: " + buildingName);
                    try {
                        RabbitMQUtil.declareAndBindQueue(channel, QUEUE_CUSTOMER_BUILDING_REQUESTS, EXCHANGE_AGENTS, buildingName);
                    } catch (IOException e) {
                        System.err.println("Failed to declare and bind queue for building: " + buildingName);
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("RentalAgent already knows about building: " + buildingName);
                }
            }

            if (updateMessage.contains("ROOM_AVAILABILITY:")) {
                String availability = updateMessage.split("ROOM_AVAILABILITY:")[1].trim();
                System.out.println("RentalAgent received room availability: " + availability);
            }
        };

        channel.basicConsume(updatesQueue, true, updateCallback, consumerTag -> {
        });
        System.out.println("RentalAgent subscribed to buildings exchange for updates.");
    }

    public void listenForCustomerRequests() throws IOException {
        // Listen for availability requests
        RabbitMQUtil.consumeMessages(channel, QUEUE_CUSTOMER_AVAILABILITY_REQUESTS, (consumerTag, message) -> {
            String request = new String(message.getBody(), StandardCharsets.UTF_8);
            String correlationId = message.getProperties().getCorrelationId();
            String[] requestParts = request.split(":");
            String customerName = requestParts[0];

            synchronized (knownCustomersAndTheirCorrelationIDs) {
                knownCustomersAndTheirCorrelationIDs.put(customerName, correlationId);
                System.out.println("putting customer: " + customerName + " with CorrelationId: " + correlationId);
                knownCorrelationIdRequestsAndTheirCustomers.put(correlationId, customerName);
                System.out.println("putting CorrelationId: " + correlationId + " with customer: " + customerName);
            }

            StringBuilder filteredAvailabilityRequest = new StringBuilder();
            for (int i = 1; i < requestParts.length; i++) {
                filteredAvailabilityRequest.append(requestParts[i]);
            }

            System.out.println("RentalAgent received availability request: " + filteredAvailabilityRequest + "from customer: " + customerName);

            // Forward the availability request to all known buildings
            for (String building : knownBuildings) {
                sendMessageToBuilding(String.valueOf(filteredAvailabilityRequest), building, correlationId);
            }
        });

        // Listen for building-specific requests (e.g., booking, cancellation)
        RabbitMQUtil.consumeMessages(channel, QUEUE_CUSTOMER_BUILDING_REQUESTS, (consumerTag, message) -> {
            String request = new String(message.getBody(), StandardCharsets.UTF_8);
            String correlationId = message.getProperties().getCorrelationId().trim();
            String buildingName = message.getEnvelope().getRoutingKey(); // Building name as routing key

            System.out.println("RentalAgent received request for building " + buildingName + ": " + request);

            // Match the routing key (building name) with known buildings
            if (knownBuildings.contains(buildingName)) {
                // Forward the request to the building requests queue only if the building name exists
                String[] parts = request.split(":");
                String customerName = parts[0].trim();
                StringBuilder filteredRequest = new StringBuilder();

                for (int i = 1; i < parts.length; i++) {
                    filteredRequest.append(parts[i]).append(":");
                }

                synchronized (knownCustomersAndTheirCorrelationIDs) {
                        knownCustomersAndTheirCorrelationIDs.put(customerName, correlationId);
                    System.out.println("putting customer: " + customerName + " with CorrelationId: " + correlationId);
                        knownCorrelationIdRequestsAndTheirCustomers.put(correlationId, customerName);
                    System.out.println("putting CorrelationId: " + correlationId + " with customer: " + customerName);
                }

                sendMessageToBuilding(String.valueOf(filteredRequest), buildingName, correlationId);
            } else {
                System.out.println("RentalAgent received request for unknown building: " + buildingName);
                sendMessageToCustomer("Building: " + buildingName + " does not exist!", correlationId);
            }
        });
    }

    public void listenForBuildingResponses() throws IOException {
        // Listen for responses from buildings
        RabbitMQUtil.consumeMessages(channel, QUEUE_BUILDING_RESPONSES, (consumerTag, message) -> {
            String response = new String(message.getBody(), StandardCharsets.UTF_8);
            String correlationId = message.getProperties().getCorrelationId();

            System.out.println("RentalAgent received response from building: " + response);

            // Filter the response to only include room availability
            if (response.startsWith("REGISTER_BUILDING:")) {
                // Split the response by ';' to separate building registration and room availability
                String[] parts = response.split(";");
                String buildingName = parts[0].split(":")[1].trim(); // Extract building name

                String roomAvailability = null;
                for (String part : parts) {
                    if (part.trim().startsWith("ROOM_AVAILABILITY:")) {
                        roomAvailability = part.split("ROOM_AVAILABILITY:")[1].trim(); // Extract room availability
                        break;
                    }
                }

                if (roomAvailability != null) {
                    // Construct the filtered message
                    String filteredMessage = buildingName + "; ROOM_AVAILABILITY:" + roomAvailability;

                    // Forward the filtered message to the customer with "building broadcast" correlation ID
                    sendMessageToCustomer(filteredMessage, "building_broadcast");
                }
            }
        });

    }

    public void listenForCustomerSpecificResponses() throws IOException {
        RabbitMQUtil.consumeMessages(channel, QUEUE_CUSTOMER_SPECIFIC_RESPONSES, (consumerTag, message) -> {
            String response = new String(message.getBody(), StandardCharsets.UTF_8);
            String correlationId = message.getProperties().getCorrelationId().trim();


            synchronized (knownCorrelationIdRequestsAndTheirCustomers) {
                String customerName = knownCorrelationIdRequestsAndTheirCustomers.get(correlationId);
                if (customerName != null) {
                    sendMessageToSpecificCustomer(response, correlationId, customerName);
                } else {
                    System.out.println("could not find customer " + customerName + " with that correlationID: " + correlationId);
                }
            }

        });
    }

    private void sendMessageToSpecificCustomer(String response, String correlationId, String customerName) throws IOException {
        RabbitMQUtil.publishMessage(
                channel,
                EXCHANGE_AGENTS,
                customerName,
                response,
                QUEUE_CUSTOMER_SPECIFIC_RESPONSES,
                correlationId
        );

        System.out.println("RentalAgent sending response to customer: " + customerName + ", with Correlation ID: " + correlationId + ", Response: " + response);

    }

    private void sendMessageToBuilding(String message, String buildingName, String correlationId) throws IOException {
        RabbitMQUtil.publishMessage(
                channel,
                EXCHANGE_BUILDING_REQUESTS,
                buildingName, // Routing key
                message,
                QUEUE_CUSTOMER_SPECIFIC_RESPONSES, // Set reply-to queue
                correlationId
        );
        System.out.println("RentalAgent forwarded message to building " + buildingName + ": " + message);
    }

    private void sendMessageToCustomer(String response, String correlationId) throws IOException {
        RabbitMQUtil.publishMessage(
                channel,
                EXCHANGE_AGENTS,
                ROUTING_KEY_AGENT_REPLY,
                response,
                QUEUE_CUSTOMER_RESPONSES,
                correlationId
        );

        System.out.println("RentalAgent sending response to customer. Correlation ID: " + correlationId + ", Response: " + response);
    }

    public static void main(String[] args) throws Exception {
        new RentalAgent();
    }
}