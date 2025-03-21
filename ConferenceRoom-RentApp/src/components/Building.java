package components;

import com.rabbitmq.client.Channel;
import utils.RabbitMQUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Building extends Thread {
    private static final String EXCHANGE_BUILDINGS = "buildings_exchange";
    private static final String EXCHANGE_BUILDING_REQUESTS = "building_requests_exchange";
    private static final String QUEUE_REQUESTS = "building_requests_queue";
    private final String buildingName;
    public final Map<String, String> roomStatus;
    private final Map<String, String> roomReservations;
    private final Channel channel;
    private ScheduledExecutorService scheduler;

    public Building(String buildingName) throws Exception {
        this.buildingName = buildingName;
        this.roomStatus = new ConcurrentHashMap<>();
        this.roomReservations = new ConcurrentHashMap<>();
        this.channel = RabbitMQUtil.getConnection().createChannel();


        RabbitMQUtil.declareFanoutExchange(channel, EXCHANGE_BUILDINGS);
        RabbitMQUtil.declareDirectExchange(channel, EXCHANGE_BUILDING_REQUESTS);
        RabbitMQUtil.declareAndBindQueue(channel,QUEUE_REQUESTS, EXCHANGE_BUILDING_REQUESTS,buildingName);

        initializeRooms();
        setupMessageScheduler();
        listenForRequests();
    }

    private void initializeRooms() {
        for (int i = 1; i <= 10; i++) {
            roomStatus.put("Room" + i, "available");
        }
    }

    private void setupMessageScheduler() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sendBuildingUpdate();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    public void sendBuildingUpdate() throws IOException {
        StringBuilder message = new StringBuilder();

        message.append("REGISTER_BUILDING:").append(buildingName).append("; ");

        message.append("ROOM_AVAILABILITY:");
        for (Map.Entry<String, String> entry : roomStatus.entrySet()) {
            message.append(entry.getKey()).append(":").append(entry.getValue()).append(", ");
        }
        if (message.charAt(message.length() - 2) == ',') {
            message.setLength(message.length() - 2);
        }

        channel.basicPublish(EXCHANGE_BUILDINGS, "", null, message.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Building sent update: " + message);
    }

    public void listenForRequests() throws IOException {
        RabbitMQUtil.consumeMessages(channel, QUEUE_REQUESTS, (consumerTag, message) -> {
            String request = new String(message.getBody(), StandardCharsets.UTF_8);
            String correlationId = message.getProperties().getCorrelationId();
            String replyTo = message.getProperties().getReplyTo(); // Extract reply-to property
            System.out.println("Building received request: " + request);
            handleRequest(request, correlationId, replyTo);
        });
    }

    public void handleRequest(String request, String correlationID, String replyTo) throws IOException {
        System.out.println("Building handling request. Correlation ID: " + correlationID + ", Request: " + request);
        String[] parts = request.split(":");
        String action = parts[0].trim();
        String response;

        switch (action) {
            case "REQUEST_ROOMS":
                response = getBuildingAvailability();
                break;
            case "MAKE_RESERVATION":
                String roomId = parts[1].trim();
                String reservationId = parts[2].trim();
                response = bookRoom(roomId, reservationId) ? "booked " + roomId + " successfully"  : roomId + " is already booked";
                break;
            case "CANCEL_RESERVATION":
                roomId = parts[1].trim();
                reservationId = parts[2].trim();
                response = cancelBooking(roomId, reservationId) ? "cancelled " + roomId + "'s reservation" : "Invalid cancellation";
                break;
            default:
                response = "Unknown request.";
        }
        sendResponseToAgents(response, correlationID, replyTo);
    }

    public void sendResponseToAgents(String response, String correlationID, String replyTo) throws IOException {
        RabbitMQUtil.publishMessage(
                channel,
                "", // Default exchange
                replyTo, // Use the reply-to queue as the routing key
                response,
                null, // Not needed
                correlationID
        );
        System.out.println("Building sent response to reply-to queue: " + replyTo + ". Correlation ID: " + correlationID + ", Response: " + response);
    }

    public boolean bookRoom(String roomId, String reservationId) {
        if (roomStatus.containsKey(roomId) && roomStatus.get(roomId).equals("available")) {
            roomStatus.put(roomId, "booked");
            roomReservations.put(roomId, reservationId);
            try {
                sendBuildingUpdate();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public boolean cancelBooking(String roomId, String reservationId) {
        if (roomReservations.containsKey(roomId) && roomReservations.get(roomId).equals(reservationId)) {
            roomStatus.put(roomId, "available");
            roomReservations.remove(roomId);
            try {
                sendBuildingUpdate();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public String getBuildingAvailability() {
        StringBuilder availableRooms = new StringBuilder();
        availableRooms.append(buildingName).append(": ");
        roomStatus.forEach((roomId, status) -> {
            if ("available".equals(status)) {
                availableRooms.append(roomId.trim()).append(", ");
            }
        });
        if (!availableRooms.isEmpty()) {
            availableRooms.setLength(availableRooms.length() - 2);
        }
        return availableRooms.toString();
    }

    public static void main(String[] args) throws Exception {
        new Building("BuildingB");
    }
}