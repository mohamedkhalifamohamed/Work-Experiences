package components;

import com.rabbitmq.client.Channel;
import utils.RabbitMQUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Customer extends Thread {
    private static final String EXCHANGE_AGENTS = "agents_exchange";
    private final Channel channel;
    public final String QUEUE_REPLY_AGENTS = "agents_reply_queue";
    public final String QUEUE_CUSTOMER_SPECIFIC_REPLY_AGENTS;
    public final Map<String, Map<String, String>> bookedRoomsByBuilding;
    private final Map<String, String> pendingRequests; // Maps correlation ID to request type
    public final Map<String, List<String>> availableBuildingRooms;
    public final String customerName;

    public Customer(String customerName) throws Exception {
        this.channel = RabbitMQUtil.getConnection().createChannel();
        this.bookedRoomsByBuilding = new ConcurrentHashMap<>();
        this.availableBuildingRooms = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>(); // Initialize pending requests tracker
        this.customerName = customerName;
        this.QUEUE_CUSTOMER_SPECIFIC_REPLY_AGENTS = customerName + "_reply_queue";
        RabbitMQUtil.declareAndBindQueue(channel, QUEUE_REPLY_AGENTS, EXCHANGE_AGENTS, "agents_reply");
        RabbitMQUtil.declareAndBindQueue(channel, QUEUE_CUSTOMER_SPECIFIC_REPLY_AGENTS, EXCHANGE_AGENTS, customerName);
        RabbitMQUtil.declareDirectExchange(channel, EXCHANGE_AGENTS);
        listenForResponses();
    }

    public static void main(String[] args) throws Exception {
        Customer customer = new Customer("Customer4");
        customer.requestAvailability();
        customer.waitForAvailabilityUpdate();
        customer.bookRoom();
        customer.cancelBooking();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\nEnter a command (book, cancel, availability, exit): ");
            String command = scanner.nextLine().toLowerCase();

            switch (command) {
                case "book":
                    customer.bookRoom();
                    break;

                case "cancel":
                    customer.cancelBooking();
                    break;

                case "availability":
                    customer.requestAvailability();
                    break;

                case "exit":
                    System.out.println("Goodbye!");
                    System.exit(0);

                default:
                    System.out.println("Unknown command. Please enter 'book', 'cancel', 'availability', or 'exit'.");
            }
        }
    }

    /**
     * Displays all available buildings and their rooms in a nicely formatted way.
     */
    public void displayAvailableBuildingWithRooms() {
        System.out.println("Available Buildings and Rooms:");
        if (availableBuildingRooms.isEmpty()) {
            System.out.println("No buildings or rooms are currently available.");
            return;
        }

        availableBuildingRooms.forEach((building, rooms) -> {
            System.out.println("Building: " + building);
            for (int i = 0; i < rooms.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + rooms.get(i));
            }
        });
    }

    /**
     * Listens for responses from the server and processes them.
     */
    public void listenForResponses() throws IOException {
        RabbitMQUtil.consumeMessages(channel, QUEUE_REPLY_AGENTS, (consumerTag, message) -> {
            String response = new String(message.getBody(), StandardCharsets.UTF_8).trim();
            String correlationId = message.getProperties().getCorrelationId().trim();

            if ("building_broadcast".equalsIgnoreCase(correlationId)) {
                processBuildingBroadcast(response);
            } else if (correlationId.startsWith("availability")) {
                processAvailabilityResponse(response);
            }

        });


        RabbitMQUtil.consumeMessages(channel, QUEUE_CUSTOMER_SPECIFIC_REPLY_AGENTS, (consumerTag, message) -> {
            String response = new String(message.getBody(), StandardCharsets.UTF_8).trim();
            String correlationId = message.getProperties().getCorrelationId().trim();// Get correlation ID

            System.out.println("Customer received message. Correlation ID: " + correlationId + ", Response: " + response);

            if (!pendingRequests.containsKey(correlationId)) {
                System.out.println("Received a response with an unknown or unexpected correlation ID: " + correlationId + " RESPONSE: " + response);
            } else {
                String requestType = pendingRequests.remove(correlationId); // Remove the correlation ID from tracking

                // Process the response based on the request type
                switch (requestType) {
                    case "availability":
                        processAvailabilityResponse(response);
                        break;

                    case "book":
                        processBookingResponse(response);
                        break;

                    case "cancel":
                        processCancellationResponse(response);
                        break;

                    default:
                        System.out.println("Received an unknown response: " + response);
                }
            }
        });
    }

    private void processBuildingBroadcast(String response) {
        synchronized (availableBuildingRooms) {
            // Parse the response
            String[] parts = response.split("; ROOM_AVAILABILITY:");
            if (parts.length == 2) {
                String buildingName = parts[0].trim();
                String[] rooms = parts[1].split(",\\s*");

                // Extract only the room names that are available
                List<String> availableRoomNames = new ArrayList<>();
                for (String room : rooms) {
                    String[] roomParts = room.split(":");
                    if (roomParts.length == 2) {
                        String roomName = roomParts[0].trim();
                        String roomStatus = roomParts[1].trim();

                        // Only add rooms that are marked as "available"
                        if ("available".equalsIgnoreCase(roomStatus)) {
                            availableRoomNames.add(roomName);
                        }
                    }
                }

                // Update the availableBuildingRooms map
                availableBuildingRooms.put(buildingName, availableRoomNames);
                System.out.println("Updated available rooms for " + buildingName + ": " + availableBuildingRooms.get(buildingName));
            } else {
                System.out.println("Invalid broadcast message format: " + response);
            }
        }
    }

    /**
     * Processes the availability response to update the available buildings and their rooms.
     */
    public void processAvailabilityResponse(String response) {
        synchronized (availableBuildingRooms) {
            availableBuildingRooms.clear(); // Clear the previous availability data

            // Split the response by line (each line contains a building and its rooms)
            String[] buildingResponses = response.split("\n");
            for (String buildingResponse : buildingResponses) {
                String[] parts = buildingResponse.split(":");
                if (parts.length == 2) {
                    String buildingName = parts[0].trim();
                    String[] rooms = parts[1].split(",\\s*");

                    // Trim each room name before adding it to the list
                    List<String> trimmedRooms = new ArrayList<>();
                    for (String room : rooms) {
                        trimmedRooms.add(room.trim());
                    }

                    availableBuildingRooms.put(buildingName, trimmedRooms);
                }
            }
        }
    }

    /**
     * Processes the booking response to provide user feedback.
     */
    public void processBookingResponse(String response) {
        System.out.println(response);
    }

    /**
     * Processes the cancellation response to provide user feedback.
     */
    public void processCancellationResponse(String response) {
        System.out.println(response);
    }

    /**
     * Sends a request to check room availability.
     */
    public void requestAvailability() throws IOException {
        String correlationId = "availability"; // Generate a unique correlation ID
        pendingRequests.put(correlationId, "availability");
        sendMessageToAgents(customerName + ":" + "REQUEST_ROOMS", correlationId);
    }

    public void bookRoom() throws IOException {

        // Request room availability from the system (for all buildings)
        requestAvailability();
        waitForAvailabilityUpdate();

        String buildingName = getTheFirstBuildingName();

        synchronized (availableBuildingRooms) {
            // Check if the building exists in the availability map
            if (!availableBuildingRooms.containsKey(buildingName)) {
                System.out.println("BuildingName: " + buildingName + " does not exist!");
                return;
            }

            // Get available rooms for the specified building
            List<String> availableRooms = availableBuildingRooms.get(buildingName);
            if (availableRooms.isEmpty()) {
                System.out.println("No rooms are available in " + buildingName + " to book at the moment.");
                return;
            }

            // Display the available rooms in the building
            displayAvailableRoomsForBuilding(buildingName, availableRooms);

            // Get the user's choice of room
            int choice = 1;

            // Process the room booking for the selected room
            processRoomBooking(choice, buildingName, availableRooms);
        }
    }

    private String getTheFirstBuildingName() {
        if (!availableBuildingRooms.isEmpty()) {
            List<String> buildingNames = new ArrayList<>(availableBuildingRooms.keySet());
            return buildingNames.get(0);
        } else {
            return "There are currently no available buildings.";
        }
    }

    /**
     * Displays the available rooms for a specific building.
     */
    private void displayAvailableRoomsForBuilding(String buildingName, List<String> availableRooms) {
        System.out.println("Available rooms in " + buildingName + ":");
        for (int i = 0; i < availableRooms.size(); i++) {
            System.out.println((i + 1) + ". " + availableRooms.get(i));
        }
    }

    /**
     * Processes a room booking request for a specific building and room.
     */
    public void processRoomBooking(int choice, String buildingName, List<String> availableRooms) throws IOException {
        // Retrieve the room ID based on the user's choice
        String roomId = availableRooms.get(choice - 1);

        // Generate a unique reservation ID and correlation ID
        String reservationId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        pendingRequests.put(correlationId, "book"); // Track the request type with the correlation ID

        // Send booking request to the specific building
        sendRoomAndReservation("MAKE_RESERVATION", roomId, reservationId, correlationId, buildingName, customerName);

        // Track the booking under the selected building
        bookedRoomsByBuilding.putIfAbsent(buildingName, new HashMap<>());
        bookedRoomsByBuilding.get(buildingName).put(roomId, reservationId); // Add the room booking

        System.out.println("Booking request sent for Room: " + roomId + ", in Building: " + buildingName +
                ", Reservation ID: " + reservationId);
    }

    /**
     * Waits for the availability list to update.
     */
    public void waitForAvailabilityUpdate() {
        try {
            Thread.sleep(500); // Wait briefly for the availability list to update
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread interrupted while waiting for availability update.");
        }
    }

    /**
     * Displays the booked rooms.
     */
    private void displayBookedBuildingWithRooms() {
        if (bookedRoomsByBuilding.isEmpty()) {
            System.out.println("You have no booked rooms.");
            return;
        }

        System.out.println("Your booked rooms grouped by building:");
        int buildingIndex = 1;

        // Iterate through each building
        for (Map.Entry<String, Map<String, String>> buildingEntry : bookedRoomsByBuilding.entrySet()) {
            String buildingName = buildingEntry.getKey();
            Map<String, String> rooms = buildingEntry.getValue();

            System.out.println(buildingIndex++ + ". Building: " + buildingName);

            // Display rooms for the current building
            int roomIndex = 1;
            for (Map.Entry<String, String> roomEntry : rooms.entrySet()) {
                String roomId = roomEntry.getKey();
                String reservationId = roomEntry.getValue();
                System.out.println("   " + roomIndex++ + ". Room: " + roomId + ", Reservation ID: " + reservationId);
            }
        }
    }

    /**
     * Handles the room cancellation process.
     */
    public void cancelBooking() throws IOException {
        if (bookedRoomsByBuilding.isEmpty()) {
            System.out.println("You have no booked rooms to cancel.");
            return;
        }

        displayBookedBuildingWithRooms(); // Display all booked rooms grouped by buildings

        // Ask the user for the building name
        System.out.println("Enter the name of the building where you want to cancel a room: ");
        String buildingName = getTheFirstBuildingName();

        // Check if the building exists in the booked rooms
        if (!bookedRoomsByBuilding.containsKey(buildingName)) {
            System.out.println("No bookings found for the specified building.");
            return;
        }

        // Display rooms for the selected building
        displayBookedRoomsInBuilding(buildingName);

        // Get the user's room choice
        int roomChoice = 1;

        // Process the cancellation for the selected room
        processRoomCancellation(buildingName, roomChoice);
    }

    /**
     * Displays the booked rooms for a specific building.
     */
    private void displayBookedRoomsInBuilding(String buildingName) {
        Map<String, String> bookedRoomsInBuilding = bookedRoomsByBuilding.get(buildingName);
        System.out.println("Booked rooms in Building: " + buildingName);

        int index = 1;
        for (Map.Entry<String, String> entry : bookedRoomsInBuilding.entrySet()) {
            System.out.println(index++ + ". Room: " + entry.getKey() + ", Reservation ID: " + entry.getValue());
        }
    }

    /**
     * Processes a room cancellation request for a specific building and room.
     */
    public void processRoomCancellation(String buildingName, int choice) throws IOException {
        Map<String, String> roomsInBuilding = bookedRoomsByBuilding.get(buildingName);

        if (roomsInBuilding == null) {
            System.out.println("No bookings found for the specified building: " + buildingName);
            return;
        }

        // Get the room ID and reservation ID based on the user's choice
        String roomId = (String) roomsInBuilding.keySet().toArray()[choice - 1];
        String reservationId = roomsInBuilding.get(roomId);
        String correlationId = UUID.randomUUID().toString(); // Generate a unique correlation ID

        pendingRequests.put(correlationId, "cancel"); // Track the cancellation request

        // Send the cancellation request to the specific building
        sendRoomAndReservation("CANCEL_RESERVATION", roomId, reservationId, correlationId, buildingName, customerName);

        // Remove the room from the building's booking list
        roomsInBuilding.remove(roomId);
        if (roomsInBuilding.isEmpty()) {
            bookedRoomsByBuilding.remove(buildingName); // Remove the building entry if no rooms remain
        }

        System.out.println("Cancellation request sent for Room: " + roomId + " in Building: " + buildingName +
                ", Reservation ID: " + reservationId);
    }


    public void sendRoomAndReservation(String action, String roomId, String reservationId, String correlationId, String buildingName, String customerName) throws IOException {
        String message = customerName + ":" + action + ":" + roomId + ":" + reservationId;
        sendMessageWithSpecificBuildingToAgents(message, buildingName, correlationId);
    }

    /**
     * Sends a message to the agents via RabbitMQ.
     */
    public void sendMessageWithSpecificBuildingToAgents(String message, String buildingName, String correlationID) throws IOException {
        RabbitMQUtil.publishMessage(
                channel,
                EXCHANGE_AGENTS,
                buildingName,
                message,
                QUEUE_REPLY_AGENTS,
                correlationID
        );
    }

    public void sendMessageToAgents(String message, String correlationID) throws IOException {
        RabbitMQUtil.publishMessage(
                channel,
                EXCHANGE_AGENTS,
                "building_broadcast",
                message,
                QUEUE_REPLY_AGENTS,
                correlationID
        );
    }
}
