package test;

import dsmsclient.FrontEnd;
import dsmsclient.FrontEndService;

public class Client {
    public static void main(String[] args) {
        try {
            FrontEndService service = new FrontEndService();
            FrontEnd fe = service.getFrontEndPort();

            // 1. Add shares (ADMIN functions require adminID as first parameter)
            System.out.println(fe.invoke("addShare:NYKA1000 EQUITY NYKE010425 100"));
            System.out.println(fe.invoke("addShare:LONA1000 BONUS LONB010426 100"));

            // 2. List availability (ADMIN functions)
            System.out.println(fe.invoke("listShareAvailability:NYKA1000 EQUITY"));
            System.out.println(fe.invoke("listShareAvailability:LONA1000 BONUS"));

            // 3. Purchase shares (BUYER functions)
            System.out.println(fe.invoke("purchaseShare:NYKB1000 NYKE010425 EQUITY 3"));
            System.out.println(fe.invoke("purchaseShare:NYKB1001 LONB010426 BONUS 5"));
            System.out.println(fe.invoke("purchaseShare:LONB1001 LONB010426 BONUS 5"));

            // 4. View buyer shares (BUYER function)
            System.out.println(fe.invoke("getShares:NYKB1000"));
            System.out.println(fe.invoke("getShares:LONB1001"));

            // 5. Sell shares (BUYER function)
            System.out.println(fe.invoke("sellShare:NYKB1000 NYKE010425 1"));

            // Recheck availability after sell
            System.out.println(fe.invoke("listShareAvailability:NYKA1000 EQUITY"));
            System.out.println(fe.invoke("listShareAvailability:LONA1000 BONUS"));

            // 6. Add another share (ADMIN function)
            System.out.println(fe.invoke("addShare:NYKA1000 EQUITY NYKE010426 100"));

            // 7. Swap shares (BUYER function)
            // This should succeed as both shares are EQUITY.
            System.out.println(fe.invoke("swapShares:NYKB1000 NYKE010425 EQUITY NYKE010426 EQUITY"));
            System.out.println(fe.invoke("getShares:NYKB1000"));

            // 8. Swap shares (BUYER function)
            // This should fail since share types differ: EQUITY vs BONUS.
            System.out.println(fe.invoke("swapShares:NYKB1000 NYKE010426 EQUITY LONB010426 BONUS"));
            System.out.println(fe.invoke("getShares:NYKB1000"));

            // 9. Remove a share (ADMIN function)
             System.out.println(fe.invoke("removeShare:NYKA1000 EQUITY NYKE010426"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
