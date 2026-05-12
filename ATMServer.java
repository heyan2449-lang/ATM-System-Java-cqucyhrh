import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class ATMServer {
    private static final int DEFAULT_PORT = 2525;
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    private static Map<String, String> userPasswords = new ConcurrentHashMap<>();
    private static Map<String, Double> userBalances = new ConcurrentHashMap<>();
    private static final ReentrantLock balanceFileLock = new ReentrantLock();
    private static final String USERS_FILE = "users.txt";
    private static final String BALANCES_FILE = "balances.txt";

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < MIN_PORT || port > MAX_PORT) {
                    System.err.println("端口号必须在 " + MIN_PORT + " ~ " + MAX_PORT + " 之间，使用默认端口 " + DEFAULT_PORT);
                    port = DEFAULT_PORT;
                }
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号，使用默认端口 " + DEFAULT_PORT);
            }
        }

        loadUserData();
        startServer(port);
    }

    private static void loadUserData() {
        // 加载密码
        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 2) {
                    userPasswords.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            System.err.println("读取 users.txt 失败: " + e.getMessage());
        }

        // 加载余额
        try (BufferedReader reader = new BufferedReader(new FileReader(BALANCES_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 2) {
                    userBalances.put(parts[0], Double.parseDouble(parts[1]));
                }
            }
        } catch (IOException e) {
            System.err.println("读取 balances.txt 失败: " + e.getMessage());
        }
    }

    private static void saveBalances() {
        balanceFileLock.lock();
        try (PrintWriter writer = new PrintWriter(new FileWriter(BALANCES_FILE))) {
            for (Map.Entry<String, Double> entry : userBalances.entrySet()) {
                writer.printf("%s %.2f%n", entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
            System.err.println("保存 balances.txt 失败: " + e.getMessage());
        } finally {
            balanceFileLock.unlock();
        }
    }

    private static void startServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("ATM 服务器启动，监听端口: " + port);
            ExecutorService threadPool = Executors.newCachedThreadPool();
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("服务器启动失败: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String currentUser = null;
        private enum State { STATE_INIT, STATE_AUTH_REQUIRED, STATE_LOGGED_IN }
        private State state = State.STATE_INIT;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                String line;
                while ((line = in.readLine()) != null) {
                    if (!processCommand(line.trim())) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("客户端连接异常: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {}
            }
        }

        private boolean processCommand(String cmd) {
            if (cmd.startsWith("HELO ")) {
                if (state != State.STATE_INIT) {
                    out.println("401 ERROR!");
                    return true;
                }
                String[] parts = cmd.split("\\s+");
                if (parts.length != 2) {
                    out.println("401 ERROR!");
                    return true;
                }
                String userId = parts[1];
                if (!userPasswords.containsKey(userId)) {
                    out.println("401 ERROR!");
                    return true;
                }
                currentUser = userId;
                state = State.STATE_AUTH_REQUIRED;
                out.println("500 AUTH REQUIRE");
                return true;
            }

            if (cmd.startsWith("PASS ")) {
                if (state != State.STATE_AUTH_REQUIRED) {
                    out.println("401 ERROR!");
                    return true;
                }
                String[] parts = cmd.split("\\s+");
                if (parts.length != 2) {
                    out.println("401 ERROR!");
                    return true;
                }
                String pwd = parts[1];
                if (userPasswords.get(currentUser).equals(pwd)) {
                    state = State.STATE_LOGGED_IN;
                    out.println("525 OK!");
                } else {
                    out.println("401 ERROR!");
                }
                return true;
            }

            if (cmd.equals("BALA")) {
                if (state != State.STATE_LOGGED_IN) {
                    out.println("401 ERROR!");
                    return true;
                }
                double balance = userBalances.get(currentUser);
                out.printf("AMNT:%.2f%n", balance);
                return true;
            }

            if (cmd.startsWith("WDRA ")) {
                if (state != State.STATE_LOGGED_IN) {
                    out.println("401 ERROR!");
                    return true;
                }
                String[] parts = cmd.split("\\s+");
                if (parts.length != 2) {
                    out.println("401 ERROR!");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(parts[1]);
                    if (amount <= 0) {
                        out.println("401 ERROR!");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    out.println("401 ERROR!");
                    return true;
                }
                double currentBalance = userBalances.get(currentUser);
                if (currentBalance >= amount) {
                    userBalances.put(currentUser, currentBalance - amount);
                    saveBalances();
                    out.println("525 OK!");
                } else {
                    out.println("401 ERROR!");
                }
                return true;
            }

            if (cmd.equals("QUIT")) {
                out.println("BYE");
                return false;
            }

            out.println("401 ERROR!");
            return true;
        }
    }
}