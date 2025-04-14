package dsms;

import dsms.LoggerUtility;
import java.io.*;
import java.util.Base64;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.jws.WebMethod;
import javax.jws.WebParam;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.ws.Service;
import java.net.URL;

@WebService(endpointInterface = "dsms.DsmsServerInterface", serviceName = "DsmsServerService", targetNamespace = "http://dsms/")
public class DsmsServer implements DsmsServerInterface {

    private final Set<Integer> processedRequests = ConcurrentHashMap.newKeySet();
    private final String replicaId;

    public DsmsServer(String replicaId) {
        this.replicaId = replicaId;
        System.out.println("[INIT] " + replicaId + " manages: NYK, LON, TOK");
    }

    private static final Logger adminLogger = LoggerUtility.getAdminLogger();
    private static final Logger clientLogger = LoggerUtility.getClientLogger();
    private static final Logger transactionLogger = LoggerUtility.getTransactionLogger();

    private static final Map<String, Map<String, Integer>> shareDatabase = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> buyerShares = new ConcurrentHashMap<>();
    private static final Map<String, Map<LocalDate, Integer>> buyerPurchaseHistory = new ConcurrentHashMap<>();
    private static final int MAX_OUT_OF_CITY_PURCHASES = 3;
    private final ReentrantLock lock = new ReentrantLock();

    static {
        shareDatabase.put("EQUITY", new ConcurrentHashMap<>());
        shareDatabase.put("BONUS", new ConcurrentHashMap<>());
        shareDatabase.put("DIVIDEND", new ConcurrentHashMap<>());
    }

    // Helper methods to enforce roles
    private boolean isAdmin(String userID) {
        // Assume admin IDs begin with NYKA, LONA, or TOKA
        String prefix = userID.substring(0, 4).toUpperCase();
        return prefix.equals("NYKA") || prefix.equals("LONA") || prefix.equals("TOKA");
    }

    private boolean isBuyer(String userID) {
        // Assume buyer IDs begin with NYKB, LONB, or TOKB
        String prefix = userID.substring(0, 4).toUpperCase();
        return prefix.equals("NYKB") || prefix.equals("LONB") || prefix.equals("TOKB");
    }

    // ================= ADMIN FUNCTIONS =================

    @Override
    public String addShare(String adminID, String shareType, String shareID, int quantity) {
        if (!isAdmin(adminID)) {
            return "ERROR: " + adminID + " is not authorized to perform admin operations.";
        }
        shareType = shareType.trim().toUpperCase();
        shareID = shareID.trim();
        shareDatabase.computeIfAbsent(shareType, k -> new ConcurrentHashMap<>()).put(shareID, quantity);
        adminLogger.info("Admin " + adminID + " added " + quantity + " shares of " + shareID + " to " + shareType);
        return "Added " + quantity + " shares of " + shareID + " to " + shareType;
    }

    @Override
    public String removeShare(String adminID, String shareType, String shareID) {
        if (!isAdmin(adminID)) {
            return "ERROR: " + adminID + " is not authorized to perform admin operations.";
        }
        shareType = shareType.trim().toUpperCase();
        shareID = shareID.trim();
        if (!shareDatabase.containsKey(shareType) || !shareDatabase.get(shareType).containsKey(shareID)) {
            return "Share " + shareID + " not found under " + shareType + " shares.";
        }
        shareDatabase.get(shareType).remove(shareID);
        adminLogger.info("Admin " + adminID + " removed share: " + shareID + " from " + shareType);
        return "Removed share: " + shareID;
    }

    @Override
    public String listShareAvailability(String adminID, String shareType) {
        if (!isAdmin(adminID)) {
            return "ERROR: " + adminID + " is not authorized to perform admin operations.";
        }
        shareType = shareType.trim().toUpperCase();
        StringBuilder availability = new StringBuilder();
        availability.append("Global Market: ")
                .append(shareDatabase.getOrDefault(shareType, new ConcurrentHashMap<>()));
        adminLogger.info("Admin " + adminID + " requested share availability for " + shareType);
        return availability.toString().replace("{", "").replace("}", "").trim();
    }

    // ================= BUYER FUNCTIONS =================

    @Override
    public String purchaseShare(String buyerID, String shareID, String shareType, int quantity) {
        if (!isBuyer(buyerID)) {
            return "ERROR: " + buyerID + " is not authorized to perform buyer operations.";
        }
        String buyerCity = buyerID.substring(0, 3);
        String shareCity = shareID.substring(0, 3);
        shareType = shareType.trim().toUpperCase();
        boolean isLocalPurchase = shareDatabase.containsKey(shareType) && shareDatabase.get(shareType).containsKey(shareID);
        int availableShares = isLocalPurchase ? shareDatabase.get(shareType).get(shareID) : -1;
        if (availableShares < quantity) {
            return "Error: Not enough shares available.";
        }
        int totalOutOfCityShares = buyerPurchaseHistory.getOrDefault(buyerID, new ConcurrentHashMap<>())
                .entrySet().stream()
                .filter(e -> !buyerCity.equals(shareCity) && ChronoUnit.DAYS.between(e.getKey(), LocalDate.now()) < 7)
                .mapToInt(Map.Entry::getValue)
                .sum();
        if (!buyerCity.equals(shareCity) && (totalOutOfCityShares + quantity) > MAX_OUT_OF_CITY_PURCHASES) {
            return "Error: Buyer cannot purchase more than " + MAX_OUT_OF_CITY_PURCHASES + " out-of-city shares per week.";
        }
        shareDatabase.get(shareType).put(shareID, availableShares - quantity);
        List<String> buyerShareList = buyerShares.computeIfAbsent(buyerID, k -> new ArrayList<>());
        for (int i = 0; i < quantity; i++) {
            buyerShareList.add(shareID + ":" + shareType);
        }
        buyerPurchaseHistory.computeIfAbsent(buyerID, k -> new ConcurrentHashMap<>())
                .merge(LocalDate.now(), quantity, Integer::sum);
        clientLogger.info(buyerID + " purchased " + quantity + " of " + shareID);
        return "Purchase successful.";
    }

    @Override
    public String getShares(String buyerID) {
        if (!isBuyer(buyerID)) {
            return "ERROR: " + buyerID + " is not authorized to perform buyer operations.";
        }
        List<String> buyerShareList = buyerShares.get(buyerID);
        if (buyerShareList == null || buyerShareList.isEmpty()) {
            return "No shares found for " + buyerID;
        }
        Map<String, Integer> shareCount = new HashMap<>();
        for (String entry : buyerShareList) {
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            String shareID = parts[0];
            String shareType = parts[1];
            String stockName = shareType + " (" + shareID + ")";
            shareCount.put(stockName, shareCount.getOrDefault(stockName, 0) + 1);
        }
        StringBuilder shares = new StringBuilder();
        shares.append("Shares owned by " + buyerID + ":\n");
        for (Map.Entry<String, Integer> entry : shareCount.entrySet()) {
            shares.append(entry.getKey()).append(" : ").append(entry.getValue()).append("\n");
        }
        transactionLogger.info("Got shares for " + buyerID);
        return shares.toString();
    }

    @Override
    public String sellShare(String buyerID, String shareID, int quantity) {
        if (!isBuyer(buyerID)) {
            return "ERROR: " + buyerID + " is not authorized to perform buyer operations.";
        }
        String shareCity = shareID.substring(0, 3);
        String buyerCity = buyerID.substring(0, 3);
        String shareType = findShareType(shareID);
        if (shareType == null) {
            return "Error: Share type not found.";
        }
        String key = shareID + ":" + shareType;
        List<String> buyerShareList = buyerShares.getOrDefault(buyerID, new ArrayList<>());
        long ownedShares = buyerShareList.stream().filter(share -> share.equals(key)).count();
        if (ownedShares < quantity) {
            return "Error: Buyer only owns " + ownedShares + " shares, but is trying to sell " + quantity;
        }
        int removed = 0;
        Iterator<String> iterator = buyerShareList.iterator();
        while (iterator.hasNext() && removed < quantity) {
            if (iterator.next().equals(key)) {
                iterator.remove();
                removed++;
            }
        }
        transactionLogger.info(buyerID + " sold " + quantity + " " + shareID);
        shareDatabase.get(shareType).put(shareID, shareDatabase.get(shareType).getOrDefault(shareID, 0) + quantity);
        return "Successfully sold " + quantity + " shares of " + shareID + " back to the local market.";
    }

    @Override
    public String swapShares(String buyerID, String oldShareID, String oldShareType, String newShareID, String newShareType) {
        if (!isBuyer(buyerID)) {
            return "ERROR: " + buyerID + " is not authorized to perform buyer operations.";
        }
        // Enforce swap only for shares of the same type
        if (!oldShareType.equalsIgnoreCase(newShareType)) {
            return "ERROR: Swap failed. Only shares of the same type can be swapped.";
        }
        lock.lock();
        try {
            String oldKey = oldShareID + ":" + oldShareType.toUpperCase();
            String newKey = newShareID + ":" + newShareType.toUpperCase();
            List<String> buyerShareList = buyerShares.get(buyerID);
            if (buyerShareList == null || !buyerShareList.contains(oldKey)) {
                return "Error: Buyer does not own the old share.";
            }
            int ownedQuantity = (int) buyerShareList.stream().filter(s -> s.equals(oldKey)).count();
            int availableQuantity = shareDatabase.getOrDefault(newShareType.toUpperCase(), new ConcurrentHashMap<>())
                    .getOrDefault(newShareID, 0);
            if (availableQuantity < ownedQuantity) {
                return "Error: Not enough available shares to perform the swap.";
            }
            int removed = 0;
            Iterator<String> it = buyerShareList.iterator();
            while (it.hasNext() && removed < ownedQuantity) {
                if (it.next().equals(oldKey)) {
                    it.remove();
                    removed++;
                }
            }
            for (int i = 0; i < ownedQuantity; i++) {
                buyerShareList.add(newKey);
            }
            // Update shareDatabase: return the old shares and deduct the new ones.
            shareDatabase.get(oldShareType.toUpperCase()).put(oldShareID,
                    shareDatabase.get(oldShareType.toUpperCase()).getOrDefault(oldShareID, 0) + ownedQuantity);
            shareDatabase.get(newShareType.toUpperCase()).put(newShareID,
                    shareDatabase.get(newShareType.toUpperCase()).getOrDefault(newShareID, 0) - ownedQuantity);
            clientLogger.info(buyerID + " swapped " + ownedQuantity + " of " + oldShareID + " for " + newShareID);
            return "Share swap successful: " + oldShareID + " replaced with " + newShareID;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Swap failed.";
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String cancelReservation(String shareID, String shareType, int amount) {
        shareType = shareType.trim().toUpperCase();
        shareID = shareID.trim();
        shareDatabase.computeIfAbsent(shareType, k -> new ConcurrentHashMap<>());
        shareDatabase.get(shareType).merge(shareID, amount, Integer::sum);
        System.out.println("[CANCEL] Restored " + amount + " shares to " + shareID + " (" + shareType + ")");
        return "Reservation cancelled. " + amount + " shares restored to " + shareID;
    }

    // HELPER METHOD: Finds share type based on shareID present in the database.
    private String findShareType(String shareID) {
        for (String type : shareDatabase.keySet()) {
            if (shareDatabase.get(type).containsKey(shareID)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String reassignShare(String shareID, String shareType, String quantityStr) {
        int quantity = Integer.parseInt(quantityStr);
        shareDatabase.get(shareType).put(shareID, shareDatabase.get(shareType).getOrDefault(shareID, 0) + quantity);
        return "Successfully reassigned " + quantity + " shares of " + shareID + " to the market.";
    }

    @Override
    public int getShareQuantity(String shareID, String shareType) {
        return shareDatabase.getOrDefault(shareType, new ConcurrentHashMap<>())
                .getOrDefault(shareID, 0);
    }

    @Override
    public String getSystemState() {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("shareDatabase", shareDatabase);
            state.put("buyerShares", buyerShares);
            state.put("buyerPurchaseHistory", buyerPurchaseHistory);
            return Base64.getEncoder().encodeToString(serialize(state));
        } catch (Exception e) {
            return "ERROR:STATE_SERIALIZATION_FAILED";
        }
    }

    @Override
    public void syncSystemState(String serializedState) {
        try {
            Map<String, Object> state = (Map<String, Object>) deserialize(Base64.getDecoder().decode(serializedState));
            shareDatabase.clear();
            buyerShares.clear();
            buyerPurchaseHistory.clear();
            shareDatabase.putAll((Map<String, Map<String, Integer>>) state.get("shareDatabase"));
            buyerShares.putAll((Map<String, List<String>>) state.get("buyerShares"));
            buyerPurchaseHistory.putAll((Map<String, Map<LocalDate, Integer>>) state.get("buyerPurchaseHistory"));
        } catch (Exception e) {
            System.out.println("[SYNC ERROR] Failed to restore system state.");
        }
    }

    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        return baos.toByteArray();
    }

    private Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return ois.readObject();
    }

    public boolean hasProcessed(int requestId) {
        return processedRequests.contains(requestId);
    }

    public void markProcessed(int requestId) {
        processedRequests.add(requestId);
    }

    @Override
    public String resetAndResyncFrom(String wsdlUrl) {
        try {
            QName qname = new QName("http://dsms/", "DsmsServerService");
            Service syncService = Service.create(new URL(wsdlUrl), qname);
            DsmsServerInterface source = syncService.getPort(DsmsServerInterface.class);
            String encoded = source.getSystemState();
            this.syncSystemState(encoded);
            return "SUCCESS: Resynced from " + wsdlUrl;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: Could not resync from " + wsdlUrl;
        }
    }
}
