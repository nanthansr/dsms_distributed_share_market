package dsms.test;

import static org.junit.jupiter.api.Assertions.*;

import dsms.DsmsServer;
import dsms.DsmsServerInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;

public class DsmsServerTest {

    private DsmsServerInterface server;

    @BeforeEach
    public void setup() {
        server = new DsmsServer("testReplica");
        HashMap<String, Object> emptyState = new HashMap<>();
        emptyState.put("shareDatabase", new HashMap<String, HashMap<String, Integer>>());
        emptyState.put("buyerShares", new HashMap<String, java.util.List<String>>());
        emptyState.put("buyerPurchaseHistory", new HashMap<String, HashMap<LocalDate, Integer>>());
        try {
            String serialized = Base64.getEncoder().encodeToString(serialize(emptyState));
            server.syncSystemState(serialized);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] serialize(Object obj) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    @Test
    public void testAddShare_AdminSuccess() {
        String response = server.addShare("NYKA1000", "EQUITY", "NYKE010425", 100);
        assertTrue(response.contains("Added 100 shares of NYKE010425"), "Admin should be able to add shares.");
    }

    @Test
    public void testAddShare_InvalidAdmin() {
        String response = server.addShare("NYKB1000", "EQUITY", "NYKE010425", 100);
        assertTrue(response.startsWith("ERROR:"), "A non-admin ID should not be allowed to add shares.");
    }

    @Test
    public void testListAvailability_Admin() {
        server.addShare("NYKA1000", "EQUITY", "NYKE010425", 100);
        String availability = server.listShareAvailability("NYKA1000", "EQUITY");
        assertTrue(availability.contains("NYKE010425"), "Availability should list the added share.");
        assertTrue(availability.contains("100"), "Availability should show the correct quantity.");
    }

    @Test
    public void testPurchaseShare_BuyerSuccess() {
        server.addShare("NYKA1000", "EQUITY", "NYKE010425", 100);
        String response = server.purchaseShare("NYKB1000", "NYKE010425", "EQUITY", 3);
        assertEquals("Purchase successful.", response, "A buyer should be able to purchase shares successfully.");
    }

    @Test
    public void testGetShares_Buyer() {
        server.addShare("NYKA1000", "EQUITY", "NYKE010425", 100);
        server.purchaseShare("NYKB1000", "NYKE010425", "EQUITY", 3);
        String shares = server.getShares("NYKB1000");
        assertTrue(shares.contains("NYKE010425"), "The purchased share should be listed for the buyer.");
    }

    @Test
    public void testSellShare_Buyer() {
        server.addShare("NYKA1000", "EQUITY", "NYKE010425", 100);
        server.purchaseShare("NYKB1000", "NYKE010425", "EQUITY", 3);
        String response = server.sellShare("NYKB1000", "NYKE010425", 1);
        assertTrue(response.contains("Successfully sold 1"), "The buyer should be able to sell shares successfully.");
        String shares = server.getShares("NYKB1000");
        assertTrue(shares.contains("2"), "After selling, the share count should be correct.");
    }

    @Test
    public void testSwapShares_Valid() {
        server.addShare("NYKA1000", "EQUITY", "NYKE010425", 100);
        server.addShare("NYKA1000", "EQUITY", "NYKE010426", 100);
        server.purchaseShare("NYKB1000", "NYKE010425", "EQUITY", 5);
        String response = server.swapShares("NYKB1000", "NYKE010425", "EQUITY", "NYKE010426", "EQUITY");
        assertTrue(response.contains("Share swap successful"), "Swap between shares of the same type should be successful.");
        String shares = server.getShares("NYKB1000");
        assertFalse(shares.contains("NYKE010425"), "After swap, the old share should be removed.");
        assertTrue(shares.contains("NYKE010426"), "After swap, the new share should be added.");
    }

    @Test
    public void testSwapShares_InvalidDifferentType() {
        server.addShare("NYKA1000", "EQUITY", "NYKE010425", 100);
        server.addShare("LONA1000", "BONUS", "LONB010426", 100);
        server.purchaseShare("NYKB1000", "NYKE010425", "EQUITY", 5);
        String response = server.swapShares("NYKB1000", "NYKE010425", "EQUITY", "LONB010426", "BONUS");
        assertTrue(response.startsWith("ERROR:"), "Swapping shares of different types should fail.");
    }

    @Test
    public void testFailureTolerance_SyncState() {
        DsmsServer server1 = new DsmsServer("testReplica1");
        DsmsServer server2 = new DsmsServer("testReplica2");
        try {
            HashMap<String, Object> emptyState = new HashMap<>();
            emptyState.put("shareDatabase", new HashMap<String, HashMap<String, Integer>>());
            emptyState.put("buyerShares", new HashMap<String, java.util.List<String>>());
            emptyState.put("buyerPurchaseHistory", new HashMap<String, HashMap<LocalDate, Integer>>());
            String serialized = Base64.getEncoder().encodeToString(serialize(emptyState));
            server1.syncSystemState(serialized);
            server2.syncSystemState(serialized);
        } catch (Exception e) {
            e.printStackTrace();
        }

        server1.addShare("NYKA1000", "EQUITY", "NYKE010425", 100);
        server1.purchaseShare("NYKB1000", "NYKE010425", "EQUITY", 3);
        String state1 = server1.getSystemState();
        server2.syncSystemState(state1);
        String shares1 = server1.getShares("NYKB1000");
        String shares2 = server2.getShares("NYKB1000");
        assertEquals(shares1, shares2, "Server2 should exactly match Server1's state after synchronization.");
    }

    @Test
    public void testResetAndResyncFrom_Failure() {
        String response = server.resetAndResyncFrom("http://invalid-url");
        assertTrue(response.startsWith("ERROR:"), "resetAndResyncFrom should fail when an invalid URL is provided.");
    }
}
