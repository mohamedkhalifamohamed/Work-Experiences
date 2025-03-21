package unittests;

import components.Building;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingTest {

    private Building building;

    @BeforeEach
    public void setUp() throws Exception {
        // Create a Building instance for testing
        building = new Building("TestBuilding");
    }

    @Test
    public void Given_Building_WhenConstructed_ShouldInitializeRooms() {
        // Ensure the building instance is initialized
        assertNotNull(building, "Building instance should not be null.");

        // Verify that the building has 10 rooms, all marked as "available"
        String availableRooms = building.getBuildingAvailability();
        assertNotNull(availableRooms, "Available rooms string should not be null.");
        assertTrue(
                availableRooms.contains("Room1") && availableRooms.contains("Room10"),
                "Available rooms should include Room1 through Room10."
        );

        // Count the number of rooms in the availability string
        String[] rooms = availableRooms.split(", ");
        assertEquals(10, rooms.length, "There should be 10 rooms initialized.");
    }

    @Test
    public void Given_Building_WhenAvailabilityUpdateIsSent_ShouldNotThrowException() {
        // Test that sending an availability update does not throw any exceptions
        assertDoesNotThrow(() -> building.sendBuildingUpdate(), "Sending a building update should not throw exceptions.");
    }

    @Test
    public void Given_Building_WhenRoomIsBooked_ShouldMarkAsBooked() {
        // Book a room and verify the booking was successful
        assertTrue(building.bookRoom("Room1", "reservationId123"), "Room1 should be successfully booked.");

        // Verify the room's status is updated to "booked"
        assertEquals("booked", building.roomStatus.get("Room1"), "Room1 should be marked as booked.");

        // Attempt to book the same room again and verify it fails
        assertFalse(building.bookRoom("Room1", "reservationId456"), "Room1 should not be bookable again.");

        // Verify the room's status remains "booked"
        assertEquals("booked", building.roomStatus.get("Room1"), "Room1 should remain marked as booked.");
    }

    @Test
    public void Given_Building_WhenBookingIsCancelled_ShouldMarkAsAvailable() {
        // Book a room for testing cancellation
        assertTrue(building.bookRoom("Room1", "reservationId123"), "Room1 should be successfully booked for cancellation test.");

        // Cancel the booking with the correct reservation ID
        assertTrue(building.cancelBooking("Room1", "reservationId123"), "Cancellation should succeed for Room1.");

        // Verify the room's status is updated to "available"
        assertEquals("available", building.roomStatus.get("Room1"), "Room1 should be marked as available after cancellation.");

        // Verify the room is now listed as available
        String availableRooms = building.getBuildingAvailability();
        assertTrue(availableRooms.contains("Room1"), "Room1 should appear in the list of available rooms.");

        // Attempt to cancel the booking with an invalid reservation ID
        assertFalse(building.cancelBooking("Room1", "wrongReservationId"), "Cancellation should fail with an invalid reservation ID.");
    }

    @Test
    public void Given_Building_WhenHandlingUnknownRequest_ShouldNotThrowException() {
        // Test that handling an unknown request does not throw any exceptions
        assertDoesNotThrow(() -> building.handleRequest("UNKNOWN:Test", "testCorrelationId"),
                "Handling an unknown request should not throw exceptions.");
    }

    @Test
    public void Given_Building_WhenRequestingRoomAvailability_ShouldReturnCorrectStatuses() {
        // Verify that all rooms are available initially
        for (int i = 1; i <= 10; i++) {
            assertEquals("available", building.roomStatus.get("Room" + i), "Room" + i + " should initially be available.");
        }

        // Book a room
        assertTrue(building.bookRoom("Room1", "reservationId123"), "Room1 should be successfully booked.");

        // Verify the room's status is "booked"
        assertEquals("booked", building.roomStatus.get("Room1"), "Room1 should be marked as booked.");

        // Verify that other rooms remain available
        for (int i = 2; i <= 10; i++) {
            assertEquals("available", building.roomStatus.get("Room" + i), "Room" + i + " should remain available.");
        }
    }

    @Test
    public void Given_Building_WhenReservingAndCancellingMultipleRooms_ShouldUpdateStateCorrectly() {
        // Book multiple rooms
        assertTrue(building.bookRoom("Room1", "reservationId123"), "Room1 should be successfully booked.");
        assertTrue(building.bookRoom("Room2", "reservationId456"), "Room2 should be successfully booked.");

        // Verify that the booked rooms are marked as "booked"
        assertEquals("booked", building.roomStatus.get("Room1"), "Room1 should be marked as booked.");
        assertEquals("booked", building.roomStatus.get("Room2"), "Room2 should be marked as booked.");

        // Cancel the bookings
        assertTrue(building.cancelBooking("Room1", "reservationId123"), "Cancellation should succeed for Room1.");
        assertTrue(building.cancelBooking("Room2", "reservationId456"), "Cancellation should succeed for Room2.");

        // Verify that the rooms are now marked as "available"
        assertEquals("available", building.roomStatus.get("Room1"), "Room1 should be marked as available.");
        assertEquals("available", building.roomStatus.get("Room2"), "Room2 should be marked as available.");
    }
}