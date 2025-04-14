package dsms.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import javax.jws.WebService;
import javax.xml.ws.Endpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DsmsIntegrationTest {

    private Endpoint frontendEndpoint;
    private Endpoint replica1Endpoint;
    private Endpoint replica2Endpoint;

    private Thread sequencerThread;

    private final String FRONTEND_URL = "http://localhost:9000/dsms/frontend";
    private final String REPLICA1_URL = "http://localhost:8010/dsms/service";
    private final String REPLICA2_URL = "http://localhost:8020/dsms/service";

    @BeforeEach
    public void setup() throws Exception {
        frontendEndpoint = Endpoint.publish(FRONTEND_URL, new dsms.components.FrontEnd());
        System.out.println("[FRONTEND] Published at " + FRONTEND_URL);

        replica1Endpoint = Endpoint.publish(REPLICA1_URL, new dsms.DsmsServer("RM1"));
        System.out.println("[RM1] Published at " + REPLICA1_URL);

        replica2Endpoint = Endpoint.publish(REPLICA2_URL, new DelayedDsmsServer("RM2", 2000));
        System.out.println("[RM2] Published (with delay) at " + REPLICA2_URL);

        sequencerThread = new Thread(() -> {
            dsms.components.Sequencer.main(new String[0]);
        });
        sequencerThread.setDaemon(true);
        sequencerThread.start();
        System.out.println("[Sequencer] Started.");

        Thread.sleep(3000);
    }

    @AfterEach
    public void tearDown() {
        if (frontendEndpoint != null) {
            frontendEndpoint.stop();
            System.out.println("[FRONTEND] Stopped.");
        }
        if (replica1Endpoint != null) {
            replica1Endpoint.stop();
            System.out.println("[RM1] Stopped.");
        }
        if (replica2Endpoint != null) {
            replica2Endpoint.stop();
            System.out.println("[RM2] Stopped.");
        }
        if (sequencerThread != null) {
            sequencerThread.interrupt();
            System.out.println("[Sequencer] Interrupted.");
        }
    }

    @Test
    public void testIntegrationWithNetworkDelaysAndOrdering() throws Exception {
        URL wsdlURL = new URL(FRONTEND_URL + "?wsdl");
        dsmsclient.FrontEndService feService = new dsmsclient.FrontEndService(wsdlURL);
        dsmsclient.FrontEnd feClient = feService.getFrontEndPort();

        String adminResponse = feClient.invoke("addShare:NYKA1000 EQUITY NYKE010425 100");
        System.out.println("Admin Add Response: " + adminResponse);
        Thread.sleep(500);

        String response1 = feClient.invoke("purchaseShare:NYKB1000 NYKE010425 EQUITY 2");
        System.out.println("Response1: " + response1);
        Thread.sleep(500);

        String response2 = feClient.invoke("purchaseShare:NYKB1000 NYKE010425 EQUITY 2");
        System.out.println("Response2: " + response2);

        boolean success = response1.contains("Purchase successful") ||
                response2.contains("Purchase successful");
        assertTrue(success, "At least one purchase should be successful even under network delays.");
    }

    @Test
    public void testStateResynchronizationBetweenReplicas() throws Exception {
        dsms.DsmsServer replica1 = new dsms.DsmsServer("RM1");
        dsms.DsmsServer replica2 = new dsms.DsmsServer("RM2");

        replica1.addShare("NYKA1000", "EQUITY", "NYKE010425", 100);
        replica1.purchaseShare("NYKB1000", "NYKE010425", "EQUITY", 3);
        String stateReplica1 = replica1.getSystemState();

        replica2.syncSystemState(stateReplica1);

        String shares1 = replica1.getShares("NYKB1000");
        String shares2 = replica2.getShares("NYKB1000");
        assertEquals(shares1, shares2, "Replica2 should match Replica1's state after synchronization.");
    }

    @WebService(endpointInterface = "dsms.DsmsServerInterface", serviceName = "DelayedDsmsServer")
    public static class DelayedDsmsServer extends dsms.DsmsServer {
        private final long delayMillis;

        public DelayedDsmsServer(String replicaId, long delayMillis) {
            super(replicaId);
            this.delayMillis = delayMillis;
        }

        @Override
        public String purchaseShare(String buyerID, String shareID, String shareType, int quantity) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return super.purchaseShare(buyerID, shareID, shareType, quantity);
        }
    }
}
