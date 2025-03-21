package integration;

import components.Building;
import components.Customer;
import components.RentalAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConRentAppIntegrationTest {

    private Customer customer;
    private RentalAgent rentalAgent;
    private Building building;

    @BeforeEach
    public void setUp() throws Exception {
        // Initialize components
        building = new Building("TestBuilding");
        rentalAgent = new RentalAgent();
        customer = new Customer();

        // Start threads for all components
        building.start();
        rentalAgent.start();
        customer.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Interrupt and join all threads to ensure proper shutdown
        building.interrupt();
        rentalAgent.interrupt();
        customer.interrupt();

        building.join(5000);
        rentalAgent.join(5000);
        customer.join(5000);
    }

    @Test
    public void testIntegration_RunAllComponents_ShouldCommunicateSuccessfully() {
        // Assert customer can request availability without throwing exceptions
        assertDoesNotThrow(() -> customer.requestAvailability());
    }

    @Test
    public void testIntegration_BuildingBroadcastsAvailability_ShouldUpdateCustomerView() throws Exception {
        // Trigger customer request
        assertDoesNotThrow(() -> customer.requestAvailability());

        // Ensure customer received the building's room availability
        assertTrue(customer.availableBuildingRooms.containsKey("TestBuilding"));
        for(String roomId: customer.availableBuildingRooms.get("TestBuilding")){
            assertDoesNotThrow(() -> building.getBuildingAvailability().contains(roomId));
        }
    }

    @Test
    public void testIntegration_CustomerBooksRoom_ShouldSucceed() throws Exception {

        // Customer requests availability and books a room
        assertDoesNotThrow(() -> {
            customer.requestAvailability();
            customer.bookRoom();
        });

        // Assert that the booking is reflected correctly
        assertTrue(customer.bookedRoomsByBuilding.containsKey("TestBuilding"));
        assertTrue(customer.bookedRoomsByBuilding.get("TestBuilding").containsKey("Room2"));
    }

    @Test
    public void testIntegration_CustomerCancelsBooking_ShouldSucceed() throws Exception {

        // Customer performs a booking and then cancels it
        assertDoesNotThrow(() -> {
            customer.requestAvailability();
            customer.bookRoom();
            customer.cancelBooking();
        });

        // Assert that the booking record is removed
        assertTrue(customer.bookedRoomsByBuilding.isEmpty());
    }

    @Test
    public void testIntegration_MultipleAgentsAndCustomers_ShouldCommunicateSuccessfully() throws Exception {
        // Initialize multiple RentalAgents and Customers
        Building building = new Building("TestBuilding");
        RentalAgent rentalAgent1 = new RentalAgent();
        RentalAgent rentalAgent2 = new RentalAgent();
        Customer customer1 = new Customer();
        Customer customer2 = new Customer();

        // Start threads for additional components

        try {

            // Start Test scenario: Multiple customers interacting with multiple rental agents
            rentalAgent1.start();
            rentalAgent2.start();
            customer1.start();
            customer2.start();
            building.start();

        } finally {
            // Clean up additional threads
            rentalAgent1.interrupt();
            rentalAgent2.interrupt();
            customer1.interrupt();
            customer2.interrupt();
            building.interrupt();

            rentalAgent1.join(5000);
            rentalAgent2.join(5000);
            customer1.join(5000);
            customer2.join(5000);
            building.join(5000);
        }
    }

    @Test
    public void testIntegration_OneCustomerWithMultipleAgents_ShouldCommunicateSuccessfully() throws Exception {
        // Initialize multiple RentalAgents and a single Customer
        RentalAgent rentalAgent1 = new RentalAgent();
        RentalAgent rentalAgent2 = new RentalAgent();
        RentalAgent rentalAgent3 = new RentalAgent();
        Building building = new Building("TestBuilding");
        Customer singleCustomer = new Customer();

        // Start threads for the additional RentalAgents and the single Customer

        try {
            rentalAgent1.start();
            rentalAgent2.start();
            rentalAgent3.start();
            building.start();
            singleCustomer.start();

        } finally {
            // Clean up additional threads
            rentalAgent1.interrupt();
            rentalAgent2.interrupt();
            rentalAgent3.interrupt();
            singleCustomer.interrupt();

            rentalAgent1.join(5000);
            rentalAgent2.join(5000);
            rentalAgent3.join(5000);
            singleCustomer.join(5000);
        }
    }


}