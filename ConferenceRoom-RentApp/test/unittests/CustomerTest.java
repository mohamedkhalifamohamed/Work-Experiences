package unittests;

import components.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Unit test class for the Customer class
public class CustomerTest {

    private Customer customer;

    @BeforeEach
    public void setUp() throws Exception {
        // Initialize a Customer instance for testing
        customer = new Customer();
    }

    @Test
    public void Given_Customer_WhenConstructed_ShouldDeclareQueuesAndExchange() throws Exception {
        // Ensure the customer is initialized properly
        assertNotNull(customer, "Customer instance should not be null.");
        assertNotNull(customer.QUEUE_REPLY_AGENTS, "Reply queue should be initialized.");
        assertTrue(customer.availableBuildingRooms.isEmpty(), "Initially, the available building rooms list should be empty.");
        assertTrue(customer.bookedRoomsByBuilding.isEmpty(), "Initially, the booked rooms by building map should be empty.");
    }

    @Test
    public void Given_Customer_WhenRequestingAvailability_ShouldNotThrowException() {
        // Test the availability request process
        assertDoesNotThrow(() -> customer.requestAvailability(), "Requesting room availability should not throw an exception.");
    }

    @Test
    public void Given_Customer_WhenReceivingAvailabilityUpdate_ShouldUpdateAvailableRooms() throws Exception {
        // Simulate receiving an availability response
        String response = "BuildingA:Room1, Room2\nBuildingB:Room3, Room4";

        // Simulate processing the response
        assertDoesNotThrow(() -> customer.processAvailabilityResponse(response), "Processing availability response should not throw an exception.");

        // Verify the available rooms map is updated
        assertTrue(customer.availableBuildingRooms.containsKey("BuildingA"), "BuildingA should be in the available rooms map.");
        assertTrue(customer.availableBuildingRooms.containsKey("BuildingB"), "BuildingB should be in the available rooms map.");
        assertEquals(2, customer.availableBuildingRooms.get("BuildingA").size(), "BuildingA should have 2 rooms available.");
        assertEquals(2, customer.availableBuildingRooms.get("BuildingB").size(), "BuildingB should have 2 rooms available.");
    }

    @Test
    public void Given_Customer_WhenBookingRoom_ShouldTrackBookedRooms() throws Exception {
        // Simulate room availability
        customer.availableBuildingRooms.put("BuildingA", List.of("Room1", "Room2"));

        // Simulate booking a room
        assertDoesNotThrow(() -> customer.processRoomBooking(1, "BuildingA", customer.availableBuildingRooms.get("BuildingA")),
                "Booking a valid room should not throw an exception.");

        // Verify the building is tracked in bookedRoomsByBuilding
        assertTrue(customer.bookedRoomsByBuilding.containsKey("BuildingA"),
                "BuildingA should be in the booked rooms map.");

        // Retrieve the booked room and reservation ID
        String roomId = "Room1";
        Map<String, String> bookedRooms = customer.bookedRoomsByBuilding.get("BuildingA");
        assertNotNull(bookedRooms, "Booked rooms for BuildingA should not be null.");
        assertTrue(bookedRooms.containsKey(roomId), "The booked rooms should contain Room1.");

        // Assert that the reservation ID exists for the booked room
        String reservationId = bookedRooms.get(roomId);
        assertNotNull(reservationId, "The reservation ID for Room1 should not be null.");
    }

    @Test
    public void Given_Customer_WhenBookingRoomWithNoAvailability_ShouldNotThrowException() {
        // Ensure no rooms are available
        customer.availableBuildingRooms.clear();

        // Attempt to book a room
        assertDoesNotThrow(() -> {
            customer.bookRoom();
        }, "Booking a room with no availability should not throw an exception.");

        // Verify no rooms are booked
        assertTrue(customer.bookedRoomsByBuilding.isEmpty(), "No rooms should be booked since there is no availability.");
    }

    @Test
    public void Given_Customer_WhenCancellingBooking_ShouldUpdateBookedRooms() throws Exception {
        // Simulate a booked room
        String roomId = "Room1";
        String buildingName = "BuildingA";
        String reservationId = UUID.randomUUID().toString();
        customer.bookedRoomsByBuilding.putIfAbsent(buildingName, new HashMap<>());
        customer.bookedRoomsByBuilding.get(buildingName).put(roomId, reservationId);

        // Simulate cancelling the room
        int roomChoice = 1; // Assume the user chooses the first room
        assertDoesNotThrow(() -> customer.processRoomCancellation(buildingName, roomChoice), "Cancelling a booked room should not throw an exception.");

        // Verify the room is removed from the booked rooms map by cancelling a room again
        assertDoesNotThrow(() -> customer.processRoomCancellation(buildingName, roomChoice), "Cancelling a booked room booked room again should not throw an exception.");
    }

    @Test
    public void Given_Customer_WhenCancellingNonExistingBooking_ShouldHandleGracefully() {
        // Attempt to cancel a non-existing booking
        assertDoesNotThrow(() -> {
            String buildingName = "NonExistentBuilding";
            int roomChoice = 1;
            customer.processRoomCancellation(buildingName, roomChoice);
        }, "Cancelling a non-existing booking should not throw an exception.");
    }

    @Test
    public void Given_Customer_WhenReceivingBookingConfirmation_ShouldPrintConfirmation() {
        // Simulate receiving a booking confirmation
        String response = "Reservation successful.";

        // Process the response
        assertDoesNotThrow(() -> customer.processBookingResponse(response), "Processing a booking confirmation should not throw an exception.");
    }

    @Test
    public void Given_Customer_WhenReceivingCancellationConfirmation_ShouldPrintConfirmation() {
        // Simulate receiving a cancellation confirmation
        String response = "Reservation cancelled.";

        // Process the response
        assertDoesNotThrow(() -> customer.processCancellationResponse(response), "Processing a cancellation confirmation should not throw an exception.");
    }

    @Test
    public void Given_Customer_WhenReceivingUnknownResponse_ShouldHandleGracefully() throws Exception {
        // Simulate receiving an unknown response
        String response = "Unknown response";

        // Process the response
        assertDoesNotThrow(() -> customer.listenForResponses(), "Processing an unknown response should not throw an exception.");
    }

    @Test
    public void Given_Customer_WhenSendingRoomAndReservation_ShouldSendCorrectMessage() {
        // Test the sendRoomAndReservation method
        assertDoesNotThrow(() -> {
            String roomId = "Room1";
            String reservationId = UUID.randomUUID().toString();
            String correlationId = UUID.randomUUID().toString();
            customer.sendRoomAndReservation("MAKE_RESERVATION", roomId, reservationId, correlationId, "BuildingA");
        }, "Sending a room and reservation ID should not throw an exception.");
    }

    @Test
    public void Given_Customer_WhenBookingAndCancelling_ShouldTrackCorrectly() throws Exception {
        // Test booking and cancelling a room
        String buildingName = "BuildingA";
        String roomId = "Room1";
        String reservationId = UUID.randomUUID().toString();

        // Simulate booking
        customer.bookedRoomsByBuilding.putIfAbsent(buildingName, new HashMap<>());
        customer.bookedRoomsByBuilding.get(buildingName).put(roomId, reservationId);

        assertTrue(customer.bookedRoomsByBuilding.containsKey(buildingName), "The booking should be tracked under the building.");
        assertEquals(reservationId, customer.bookedRoomsByBuilding.get(buildingName).get(roomId), "The reservation ID should match.");

        // Simulate cancelling
        customer.sendRoomAndReservation("CANCEL_RESERVATION", roomId, reservationId, UUID.randomUUID().toString(), buildingName);
        customer.bookedRoomsByBuilding.get(buildingName).remove(roomId);

        assertFalse(customer.bookedRoomsByBuilding.get(buildingName).containsKey(roomId), "The room should no longer be tracked after cancellation.");
    }
}