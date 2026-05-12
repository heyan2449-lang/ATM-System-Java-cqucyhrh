import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ATMClient {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 2525;

    public static void main(String[] args) {
        String serverHost = DEFAULT_HOST;
        int serverPort = DEFAULT_PORT;

        if (args.length >= 1) {
            serverHost = args[0];
        }
        if (args.length >= 2) {
            try {
                serverPort = Integer.parseInt(args[1]);
                if (serverPort < 1024 || serverPort > 65535) {
                    System.err.println("端口号无效，使用默认端口 " + DEFAULT_PORT);
                    serverPort = DEFAULT_PORT;
                }
            } catch (NumberFormatException e) {
                System.err.println("端口号格式错误，使用默认端口 " + DEFAULT_PORT);
            }
        }

        try (Socket socket = new Socket(serverHost, serverPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("已连接到服务器 " + serverHost + ":" + serverPort);

            // 输入卡号
            System.out.print("请输入卡号: ");
            String userId = scanner.nextLine().trim();
            out.println("HELO " + userId);

            String response = in.readLine();
            if (response == null) {
                System.out.println("服务器无响应");
                return;
            }

            if (response.equals("500 AUTH REQUIRE")) {
                System.out.print("请输入口令: ");
                String passwd = scanner.nextLine().trim();
                out.println("PASS " + passwd);
                response = in.readLine();
                if (response.equals("525 OK!")) {
                    System.out.println("认证成功");
                    menuLoop(in, out, scanner);
                } else {
                    System.out.println("认证失败: " + response);
                }
            } else {
                System.out.println("服务器返回异常: " + response);
            }

            out.println("QUIT");
            String bye = in.readLine();
            if ("BYE".equals(bye)) {
                System.out.println("会话结束");
            }

        } catch (IOException e) {
            System.err.println("客户端错误: " + e.getMessage());
        }
    }

    private static void menuLoop(BufferedReader in, PrintWriter out, Scanner scanner) throws IOException {
        while (true) {
            System.out.println("\n请选择操作: BALA(查询余额) / WDRA <取款金额> / QUIT(退出)");
            String input = scanner.nextLine().trim();
            out.println(input);

            if (input.equalsIgnoreCase("QUIT")) {
                break;
            }

            String response = in.readLine();
            if (response == null) {
                System.out.println("连接断开");
                break;
            }

            if (response.startsWith("AMNT:")) {
                System.out.println("当前余额: " + response.substring(5) + " 元");
            } else if (response.equals("525 OK!")) {
                System.out.println("操作成功");
            } else if (response.equals("401 ERROR!")) {
                System.out.println("操作失败（余额不足或命令非法）");
            } else {
                System.out.println("服务器响应: " + response);
            }
        }
    }
}