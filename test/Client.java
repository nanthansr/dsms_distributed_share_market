package test;

import dsmsclient.FrontEnd;
import dsmsclient.FrontEndService;

public class Client {
    public static void main(String[] args) {
        try {
            FrontEndService service = new FrontEndService();
            FrontEnd fe = service.getFrontEndPort();

            // 1. Add shares
            System.out.println(fe.invoke("addShare:EQUITY NYKE010425 100"));
            System.out.println(fe.invoke("addShare:BONUS LONB010426 100"));

            // 2. List availability
            System.out.println(fe.invoke("listShareAvailability:EQUITY"));
            System.out.println(fe.invoke("listShareAvailability:BONUS"));

            // 3. Purchase shares
            System.out.println(fe.invoke("purchaseShare:NYKB1000 NYKE010425 EQUITY 3"));
            System.out.println(fe.invoke("purchaseShare:NYKB1001 LONB010426 BONUS 5"));
            System.out.println(fe.invoke("purchaseShare:LONB1001 LONB010426 BONUS 5"));

            // 4. View buyer shares
            System.out.println(fe.invoke("getShares:NYKB1000"));
            System.out.println(fe.invoke("getShares:LONB1001"));

            // 5. Sell shares
            System.out.println(fe.invoke("sellShare:NYKB1000 NYKE010425 1"));

            System.out.println(fe.invoke("listShareAvailability:EQUITY"));
            System.out.println(fe.invoke("listShareAvailability:BONUS"));

            // 6. Add another share
            System.out.println(fe.invoke("addShare:EQUITY NYKE010426 100"));

            // // 7. Swap shares
            System.out.println(fe.invoke("swapShares:NYKB1000 NYKE010425 EQUITY NYKE010426 EQUITY"));

            System.out.println(fe.invoke("getShares:NYKB1000"));

            System.out.println(fe.invoke("swapShares:NYKB1000 NYKE010426 EQUITY LONB010426 BONUS"));

            System.out.println(fe.invoke("getShares:NYKB1000"));

            // 8. Remove a share
            // System.out.println(fe.invoke("removeShare:NYKE010426 EQUITY"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
