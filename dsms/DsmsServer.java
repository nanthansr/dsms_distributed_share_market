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

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.URL;

@WebService(endpointInterface = "dsms.DsmsServerInterface", serviceName = "DsmsServerService", targetNamespace = "http://dsms/")

public class DsmsServer implements DsmsServerInterface {

    // private final String cityCode;
    private final Set<Integer> processedRequests = ConcurrentHashMap.newKeySet();

    // public DsmsServer(String cityCode) {
    // System.out.println("[INIT] This server thinks it's: " + cityCode);
    // this.cityCode = cityCode;
    // }

    // private String getCityCode() {
    // return cityCode;
    // }

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
        shareDatabase.put("Equity", new ConcurrentHashMap<>());
        shareDatabase.put("Bonus", new ConcurrentHashMap<>());
        shareDatabase.put("Dividend", new ConcurrentHashMap<>());
    }

    // ADDSHARE
    @Override
    public String addShare(String type, String symbol, int quantity) {
        shareDatabase.computeIfAbsent(type.toUpperCase(), k -> new ConcurrentHashMap<>()).put(symbol, quantity);
        // .info logs the message with INFO level (which is the default level for the
        // logger)
        // and is used to log informational messages.
        adminLogger.info("Added " + quantity + " shares of " + symbol + " to " + type);
        return "Added " + quantity + " shares of " + symbol + " to " + type;
    }

    // REMOVESHARE
    @Override
    public String removeShare(String shareID, String shareType) {
        shareType = shareType.trim().toUpperCase();
        shareID = shareID.trim();
        if (!shareDatabase.containsKey(shareType) || !shareDatabase.get(shareType).containsKey(shareID)) {
            return "Share " + shareID + " not found under " + shareType + " shares.";
        }
        shareDatabase.get(shareType).remove(shareID);
        adminLogger.info("Removed share: " + shareID);
        return "Removed share: " + shareID;
    }

    // LISTSHARES
    @Override
    public String listShareAvailability(String shareType) {

        // Get local availability
        StringBuilder availability = new StringBuilder();
        availability.append("Global Market: ")
                .append(shareDatabase.getOrDefault(shareType, new ConcurrentHashMap<>()));

        adminLogger.info("Sent share availability for " + shareType);
        return availability.toString().replace("{", "").replace("}", "").trim();

    }

    @Override
    public String purchaseShare(String buyerID, String shareID, String shareType, int quantity) {
        // System.out.println("[DEBUG] buyerShares from Purchase method = " +
        // buyerShares);
        String buyerCity = buyerID.substring(0, 3);
        String shareCity = shareID.substring(0, 3);

        // Step 1: Check if the share exists locally
        boolean isLocalPurchase = shareDatabase.containsKey(shareType)
                && shareDatabase.get(shareType).containsKey(shareID);

        int availableShares = isLocalPurchase ? shareDatabase.get(shareType).get(shareID) : -1;

        // Step 2: If not found locally, check other cities
        if (!isLocalPurchase) {
            try {
                availableShares = Integer
                        .parseInt(queryRemoteServer(shareCity, "getShareQuantity", shareID, shareType));
            } catch (Exception e) {
                return "Error retrieving share availability from remote city.";
            }
        }

        // Step 3: Validate availability
        if (availableShares < quantity) {
            return "Error: Not enough shares available.";
        }

        // Step 4: Check if the buyer has exceeded the weekly out-of-city purchase limit
        int totalOutOfCityShares = buyerPurchaseHistory.getOrDefault(buyerID, new ConcurrentHashMap<>())
                .entrySet().stream()
                .filter(e -> !buyerCity.equals(shareCity) && ChronoUnit.DAYS.between(e.getKey(), LocalDate.now()) < 7)
                .mapToInt(Map.Entry::getValue)
                .sum();

        if (!buyerCity.equals(shareCity) && (totalOutOfCityShares + quantity) > MAX_OUT_OF_CITY_PURCHASES) {
            return "Error: Buyer cannot purchase more than " + MAX_OUT_OF_CITY_PURCHASES
                    + " out-of-city shares per week.";
        }

        // Step 5: If the share is local, update the local database
        if (isLocalPurchase) {
            shareDatabase.get(shareType).put(shareID, availableShares - quantity);
        } else {
            // Forward purchase request to remote server
            String purchaseResponse;
            try {
                purchaseResponse = queryRemoteServer(shareCity, "purchaseShare", buyerID, shareID, shareType,
                        String.valueOf(quantity));
                if (!purchaseResponse.contains("successful")) {
                    return "Error: Remote purchase failed.";
                }
            } catch (Exception e) {
                return "Error: Unable to process remote purchase.";
            }
        }

        // Step 6: Correctly store the purchased quantity in `buyerShares`
        List<String> buyerShareList = buyerShares.computeIfAbsent(buyerID, k -> new ArrayList<>());
        for (int i = 0; i < quantity; i++) {
            buyerShareList.add(shareID + ":" + shareType); // Add `quantity` instances of `shareID`
        }

        // Step 7: Update purchase history
        buyerPurchaseHistory.computeIfAbsent(buyerID, k -> new ConcurrentHashMap<>()).merge(LocalDate.now(), quantity,
                Integer::sum);

        clientLogger.info(buyerID + " purchased " + quantity + " of " + shareID);
        return "Purchase successful.";
    }

    // GETSHARES
    @Override
    public String getShares(String buyerID) {
        // System.out.println("[DEBUG] buyerShares from getShares method = " +
        // buyerShares);

        System.out.println("Processing getShares request for " + buyerID);

        // Get buyer's shares
        List<String> buyerShareList = buyerShares.get(buyerID);

        if (buyerShareList == null || buyerShareList.isEmpty()) {
            return "No shares found for " + buyerID;
        }

        // Count the number of each share owned by the buyer
        Map<String, Integer> shareCount = new HashMap<>();
        for (String entry : buyerShareList) {
            String[] parts = entry.split(":"); // Extract ShareID and ShareType
            if (parts.length != 2) {
                continue; // Skip malformed entries
            }
            String shareID = parts[0];
            String shareType = parts[1];
            String stockName = shareType + " (" + shareID + ")";

            shareCount.put(stockName, shareCount.getOrDefault(stockName, 0) + 1);
        }

        // Build output
        StringBuilder shares = new StringBuilder();
        shares.append("Global Market: ");
        shares.append("Shares owned by " + buyerID + ":\n");
        for (Map.Entry<String, Integer> entry : shareCount.entrySet()) {
            System.out.println("[DEBUG] shareCount = " + shareCount);
            shares.append(entry.getKey()).append(" : ").append(entry.getValue()).append("\n");
        }

        transactionLogger.info("Got shares for " + buyerID);
        return shares.toString();
    }

    @Override
    public String sellShare(String buyerID, String shareID, int quantity) {
        String shareCity = shareID.substring(0, 3);
        String buyerCity = buyerID.substring(0, 3);
        String shareType = findShareType(shareID);

        if (shareType == null) {
            return "Error: Share type not found.";
        }

        String key = shareID + ":" + shareType;
        // Get buyer's shares list
        List<String> buyerShareList = buyerShares.getOrDefault(buyerID, new ArrayList<>());

        // Count how many shares the buyer actually owns
        long ownedShares = buyerShareList.stream().filter(share -> share.equals(key)).count();

        // Ensure the buyer has enough shares to sell
        if (ownedShares < quantity) {
            return "Error: Buyer only owns " + ownedShares + " shares, but is trying to sell " + quantity;
        }

        // Remove `quantity` shares from buyer's records
        int removed = 0;
        Iterator<String> iterator = buyerShareList.iterator();
        while (iterator.hasNext() && removed < quantity) {
            if (iterator.next().equals(key)) {
                iterator.remove();
                removed++;
            }
        }

        transactionLogger.info(buyerID + " sold " + quantity + " " + shareID);

        // If local, reassign to the local market
        if (buyerCity.equals(shareCity)) {
            shareDatabase.get(shareType).put(shareID, shareDatabase.get(shareType).getOrDefault(shareID, 0) + quantity);
            return "Successfully sold " + quantity + " shares of " + shareID + " back to the local market.";
        } else {
            // Forward the request to the remote server to add `quantity` shares back
            try {
                return queryRemoteServer(shareCity, "reassignShare", shareID, shareType, String.valueOf(quantity));
            } catch (Exception e) {
                e.printStackTrace();
                return "Error: Unable to reassign remote share.";
            }
        }
    }

    // SWAPSHARES
    @Override
    public String swapShares(String buyerID, String oldShareID, String oldShareType, String newShareID,
            String newShareType) {
        lock.lock();
        try {
            String oldShareCity = oldShareID.substring(0, 3);
            String newShareCity = newShareID.substring(0, 3);

            String oldKey = oldShareID + ":" + oldShareType;
            String newKey = newShareID + ":" + newShareType;

            if (!buyerShares.containsKey(buyerID) || !buyerShares.get(buyerID).contains(oldKey)) {
                return "Error: Buyer does not own the old share.";
            }

            int ownedQuantity = (int) buyerShares.get(buyerID).stream().filter(id -> id.equals(oldKey)).count();

            int availableQuantity;
            if (oldShareCity.equals(newShareCity)) {
                String availabilityResponse = listShareAvailability(newShareType);
                if (!availabilityResponse.contains(newShareID)) {
                    return "Error: New share does not exist.";
                }
                availableQuantity = extractAvailableQuantity(availabilityResponse, newShareID);
            } else {
                String availabilityResponse = queryRemoteServer(newShareCity, "listShareAvailability", newShareType);
                if (!availabilityResponse.contains(newShareID)) {
                    return "Error: New share does not exist.";
                }
                availableQuantity = extractAvailableQuantity(availabilityResponse, newShareID);
            }

            if (availableQuantity < ownedQuantity) {
                return "Error: Not enough available shares in new city.";
            }

            String sellResponse = sellShare(buyerID, oldShareID, ownedQuantity);
            if (!sellResponse.contains("Successfully")) {
                return "Error: Could not sell old share, swap aborted.";
            }

            String purchaseResponse;
            if (oldShareCity.equals(newShareCity)) {
                purchaseResponse = purchaseShare(buyerID, newShareID, newShareType, ownedQuantity);
            } else {
                purchaseResponse = queryRemoteServer(newShareCity, "purchaseShare", buyerID, newShareID,
                        newShareType, String.valueOf(ownedQuantity));
            }

            if (!purchaseResponse.contains("successful")) {
                if (!oldShareCity.equals(newShareCity)) {
                    queryRemoteServer(oldShareCity, "purchaseShare", buyerID, oldShareID, oldShareType,
                            String.valueOf(ownedQuantity));
                    queryRemoteServer(newShareCity, "cancelReservation", newShareID, newShareType,
                            String.valueOf(ownedQuantity));
                }
                return "Error: Could not purchase new share, swap aborted.";
            }

            clientLogger.info(buyerID + " swapped " + oldShareID + " for " + newShareID);
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

    // HELPER METHODS
    private String findShareType(String shareID) {
        for (String type : shareDatabase.keySet()) {
            if (shareDatabase.get(type).containsKey(shareID)) {
                return type;
            }
        }
        return null;
    }

    private String queryRemoteServer(String cityCode, String method, String... params) throws Exception {
        String wsdlUrl = getWsdlUrl(cityCode);
        if (cityCode == null) {
            return "Error: Unknown or unsupported city code '" + cityCode + "'";
        }
        System.out.println("DEBUG: Querying " + cityCode + " at " + wsdlUrl + " for " + method);
        try {
            URL url = new URL(wsdlUrl);
            QName qname = new QName("http://dsms/", "DsmsServerService");
            Service service = Service.create(url, qname);
            DsmsServerInterface remoteServer = service.getPort(DsmsServerInterface.class);

            switch (method) {
                case "cancelReservation":
                    return remoteServer.cancelReservation(params[0], params[1], Integer.parseInt(params[2]));
                case "addShare":
                    return remoteServer.addShare(params[0], params[1], Integer.parseInt(params[2]));
                case "reassignShare":
                    return remoteServer.reassignShare(params[0], params[1], params[2]);
                case "getShareQuantity":
                    return String.valueOf(remoteServer.getShareQuantity(params[0], params[1]));
                case "getShares":
                    return remoteServer.getShares(params[0]);
                case "listShareAvailability":
                    return remoteServer.listShareAvailability(params[0]);
                case "sellShare":
                    return remoteServer.sellShare(params[0], params[1], Integer.parseInt(params[2]));
                case "purchaseShare":
                    return remoteServer.purchaseShare(params[0], params[1], params[2], Integer.parseInt(params[3]));
                case "swapShares":
                    return remoteServer.swapShares(params[0], params[1], params[2], params[3], params[4]);
                default:
                    return "Error: Unknown method.";
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Connection failed for " + wsdlUrl + ": " + e.getMessage());
            e.printStackTrace();
            return "Error: Unable to connect to remote server.";
        }
    }

    // reassigns a share to its original local market : part of the sellShare method
    @Override
    public String reassignShare(String shareID, String shareType, String quantityStr) {
        int quantity = Integer.parseInt(quantityStr);
        shareDatabase.get(shareType).put(shareID, shareDatabase.get(shareType).getOrDefault(shareID, 0) + quantity);
        return "Successfully reassigned " + quantity + " shares of " + shareID + " to the market.";
    }

    // gets the quantity of a share in the local database : part of the
    // purchaseShare method
    @Override
    public int getShareQuantity(String shareID, String shareType) {
        return shareDatabase.getOrDefault(shareType, new ConcurrentHashMap<>())
                .getOrDefault(shareID, 0);
    }

    // // reserves a share for a buyer : part of the swapShares method
    // @Override
    // public String reserveShare(String shareID, String quantity) {
    // return "Share " + shareID + " reserved.";
    // }

    // @Override
    // public String cancelReservation(String shareID) {
    // return "Reservation for share " + shareID + " cancelled.";
    // }

    private String getWsdlUrl(String cityCode) {
        switch (cityCode.toUpperCase()) {
            case "NYK":
                return "http://localhost:8010/dsms/service?wsdl";
            case "LON":
                return "http://localhost:8120/dsms/service?wsdl";
            case "TOK":
                return "http://localhost:8230/dsms/service?wsdl";
            default:
                return null;
        }
    }

    private int extractAvailableQuantity(String availabilityResponse, String targetShareID) {
        String[] entries = availabilityResponse.split(","); // split by comma to get each entry
        for (String entry : entries) { // Iterate over each entry
            if (entry.contains(targetShareID)) {
                String[] parts = entry.trim().split("="); // Split by = to get the shareID and quantity
                return Integer.parseInt(parts[parts.length - 1]); // Last value should be the quantity
            }
        }
        return 0;
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
            // 1. Decode the incoming Base64 state string into an object
            Map<String, Object> state = (Map<String, Object>) deserialize(Base64.getDecoder().decode(serializedState));

            // 2. Clear existing local data
            shareDatabase.clear();
            buyerShares.clear();
            buyerPurchaseHistory.clear();

            // 3. Replace with the new, synced data
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
