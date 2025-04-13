package test;

import java.util.Scanner;
import dsmsclient.FrontEnd;
import dsmsclient.FrontEndService;

public class ClientMenu {

    private static FrontEnd fe;

    public static void main(String[] args) {
        FrontEndService service = new FrontEndService();
        fe = service.getFrontEndPort();

        Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome to the DSMS Client Menu");
        System.out.println("Please select your role:");
        System.out.println("1. Admin");
        System.out.println("2. Buyer");
        int roleChoice = Integer.parseInt(scanner.nextLine().trim());
        String role = (roleChoice == 1) ? "Admin" : "Buyer";
        System.out.print("Enter your " + role + " ID: ");
        String userID = scanner.nextLine().trim();

        boolean exit = false;
        while (!exit) {
            if (role.equals("Admin")) {
                System.out.println("\n--- Admin Menu ---");
                System.out.println("1. Add Share");
                System.out.println("2. Remove Share");
                System.out.println("3. List Share Availability");
                System.out.println("4. Exit");
                System.out.print("Select an option: ");
                int option = Integer.parseInt(scanner.nextLine().trim());
                switch (option) {
                    case 1:
                        // Add Share expects: adminID shareType shareID quantity
                        System.out.print("Enter share type (EQUITY, BONUS, DIVIDEND): ");
                        String addType = scanner.nextLine().trim().toUpperCase();
                        System.out.print("Enter share ID: ");
                        String addID = scanner.nextLine().trim();
                        System.out.print("Enter quantity: ");
                        int addQty = Integer.parseInt(scanner.nextLine().trim());
                        String addCmd = "addShare:" + userID + " " + addType + " " + addID + " " + addQty;
                        System.out.println("Result: " + fe.invoke(addCmd));
                        break;
                    case 2:
                        // Remove Share expects: adminID shareType shareID
                        System.out.print("Enter share type: ");
                        String remType = scanner.nextLine().trim().toUpperCase();
                        System.out.print("Enter share ID: ");
                        String remID = scanner.nextLine().trim();
                        String remCmd = "removeShare:" + userID + " " + remType + " " + remID;
                        System.out.println("Result: " + fe.invoke(remCmd));
                        break;
                    case 3:
                        // List Share Availability expects: adminID shareType
                        System.out.print("Enter share type: ");
                        String listType = scanner.nextLine().trim().toUpperCase();
                        String listCmd = "listShareAvailability:" + userID + " " + listType;
                        System.out.println("Result: " + fe.invoke(listCmd));
                        break;
                    case 4:
                        exit = true;
                        break;
                    default:
                        System.out.println("Invalid option");
                }
            } else {
                // Buyer Menu
                System.out.println("\n--- Buyer Menu ---");
                System.out.println("1. Purchase Share");
                System.out.println("2. Get Shares");
                System.out.println("3. Sell Share");
                System.out.println("4. Swap Shares");
                System.out.println("5. Exit");
                System.out.print("Select an option: ");
                int option = Integer.parseInt(scanner.nextLine().trim());
                switch (option) {
                    case 1:
                        // Purchase Share expects: buyerID shareID shareType quantity
                        System.out.print("Enter share ID: ");
                        String buyID = scanner.nextLine().trim();
                        System.out.print("Enter share type: ");
                        String buyType = scanner.nextLine().trim().toUpperCase();
                        System.out.print("Enter quantity: ");
                        int buyQty = Integer.parseInt(scanner.nextLine().trim());
                        String buyCmd = "purchaseShare:" + userID + " " + buyID + " " + buyType + " " + buyQty;
                        System.out.println("Result: " + fe.invoke(buyCmd));
                        break;
                    case 2:
                        // Get Shares expects: buyerID
                        String getCmd = "getShares:" + userID;
                        System.out.println("Result: " + fe.invoke(getCmd));
                        break;
                    case 3:
                        // Sell Share expects: buyerID shareID quantity
                        System.out.print("Enter share ID: ");
                        String sellID = scanner.nextLine().trim();
                        System.out.print("Enter quantity to sell: ");
                        int sellQty = Integer.parseInt(scanner.nextLine().trim());
                        String sellCmd = "sellShare:" + userID + " " + sellID + " " + sellQty;
                        System.out.println("Result: " + fe.invoke(sellCmd));
                        break;
                    case 4:
                        // Swap Shares expects: buyerID oldShareID oldShareType newShareID newShareType
                        System.out.print("Enter old share ID: ");
                        String oldID = scanner.nextLine().trim();
                        System.out.print("Enter old share type: ");
                        String oldType = scanner.nextLine().trim().toUpperCase();
                        System.out.print("Enter new share ID: ");
                        String newID = scanner.nextLine().trim();
                        System.out.print("Enter new share type: ");
                        String newType = scanner.nextLine().trim().toUpperCase();
                        String swapCmd = "swapShares:" + userID + " " + oldID + " " + oldType + " " + newID + " " + newType;
                        System.out.println("Result: " + fe.invoke(swapCmd));
                        break;
                    case 5:
                        exit = true;
                        break;
                    default:
                        System.out.println("Invalid option");
                }
            }
        }
        scanner.close();
        System.out.println("Exiting Client Menu. Goodbye!");
    }
}
