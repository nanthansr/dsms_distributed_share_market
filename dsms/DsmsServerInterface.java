package dsms;

import javax.jws.WebService;
import javax.jws.WebMethod;

@WebService(targetNamespace = "http://dsms/")
public interface DsmsServerInterface {

    @WebMethod
    String addShare(String adminID,String shareType, String shareID, int quantity);

    @WebMethod
    String removeShare(String adminID, String shareType, String shareID);

    @WebMethod
    String listShareAvailability(String adminID,String shareType);

    @WebMethod
    String getShares(String buyerID);

    @WebMethod
    String sellShare(String buyerID, String shareID, int quantity);

    @WebMethod
    String purchaseShare(String buyerID, String shareID, String shareType, int quantity);

    @WebMethod
    String swapShares(String buyerID, String oldShareID, String oldShareType, String newShareID, String newShareType);

    @WebMethod
    String cancelReservation(String shareID, String shareType, int amount);

    @WebMethod
    int getShareQuantity(String shareID, String shareType);

    @WebMethod
    String reassignShare(String shareID, String shareType, String quantityStr);

    // NEW: Replica State Sync Methods
    @WebMethod
    String getSystemState();

    @WebMethod
    void syncSystemState(String serializedState);

    @WebMethod
    String resetAndResyncFrom(String wsdlUrl);
}
