
package dsms;

import java.io.IOException;
import java.util.logging.*;

public class LoggerUtility {
    private static final String ADMIN_LOG_FILE = "admin_logs.txt";
    private static final String CLIENT_LOG_FILE = "client_logs.txt";
    private static final String TRANSACTION_LOG_FILE = "transactions_logs.txt";

    public static Logger getAdminLogger() {
        return createLogger("AdminLogger", ADMIN_LOG_FILE);
    }

    public static Logger getClientLogger() {
        return createLogger("ClientLogger", CLIENT_LOG_FILE);
    }

    public static Logger getTransactionLogger() {
        return createLogger("TransactionLogger", TRANSACTION_LOG_FILE);
    }

    private static Logger createLogger(String name, String fileName) {
        Logger logger = Logger.getLogger(name);
        logger.setUseParentHandlers(false);

        try {
            FileHandler handler = new FileHandler(fileName, true); // Append mode true
            handler.setFormatter(new SimpleFormatter());

            // Remove copies of logs to console
            for (Handler h : logger.getHandlers()) {
                logger.removeHandler(h);
            }

            logger.addHandler(handler);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return logger;
    }
}
