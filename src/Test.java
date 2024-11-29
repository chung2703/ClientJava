import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

public class Test {

    private static final int SERVER_PORT = 5000; // Cổng server cố định
    private static final int MAX_UDP_SIZE = 1400; // Kích thước tối đa của gói UDP
    private static final int HEADER_SIZE = 12; // Kích thước phần tiêu đề của gói tin
    private static boolean running = true; // Cờ điều khiển vòng lặp

    private static class PacketHeader {
        int packetID;
        int packetOrder;
        int totalPackets;

        public PacketHeader(int packetID, int packetOrder, int totalPackets) {
            this.packetID = packetID;
            this.packetOrder = packetOrder;
            this.totalPackets = totalPackets;
        }
    }

    // Phân tích tiêu đề gói tin
    private static PacketHeader parseHeader(byte[] packetData) {
        int packetID = bytesToInt(packetData, 0);
        int packetOrder = bytesToInt(packetData, 4);
        int totalPackets = bytesToInt(packetData, 8);
        return new PacketHeader(packetID, packetOrder, totalPackets);
    }

    // Chuyển đổi 4 byte thành số nguyên
    private static int bytesToInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24 |
               (bytes[offset + 1] & 0xFF) << 16 |
               (bytes[offset + 2] & 0xFF) << 8 |
               (bytes[offset + 3] & 0xFF);
    }

    // Kiểm tra xem dữ liệu có phải là JPEG hợp lệ không
    private static boolean isValidJPEG(byte[] data) {
        return data.length > 2 &&
               (data[0] & 0xFF) == 0xFF &&
               (data[1] & 0xFF) == 0xD8 &&
               (data[data.length - 2] & 0xFF) == 0xFF &&
               (data[data.length - 1] & 0xFF) == 0xD9;
    }

    // Phương thức nhận hình ảnh từ server
    private static void receiveImages(String serverIp, int clientPort) {
        try (DatagramSocket socket = new DatagramSocket(clientPort)) {
            InetAddress serverAddress = InetAddress.getByName(serverIp);

            // Gửi yêu cầu "GetVideo" đến server
            byte[] request = "GetVideo".getBytes();
            DatagramPacket requestPacket = new DatagramPacket(request, request.length, serverAddress, SERVER_PORT);
            socket.send(requestPacket);
            System.out.println("Request sent to server.");

            // Bộ đệm để lưu các gói tin nhận được
            ConcurrentHashMap<Integer, Map<Integer, byte[]>> packetBuffer = new ConcurrentHashMap<>();
            ConcurrentHashMap<Integer, Integer> totalPacketsMap = new ConcurrentHashMap<>();

            // Tạo giao diện hiển thị hình ảnh
            JFrame frame = new JFrame("Received Image");
            JLabel label = new JLabel();
            frame.add(label);
            frame.setSize(800, 800);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);

            while (running) {
                byte[] buffer = new byte[MAX_UDP_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                int dataLength = packet.getLength();
                if (dataLength < HEADER_SIZE) {
                    System.err.println("Invalid packet size.");
                    continue;
                }

                // Phân tích tiêu đề gói tin
                PacketHeader header = parseHeader(buffer);
                int dataSize = dataLength - HEADER_SIZE;
                byte[] imageData = new byte[dataSize];
                System.arraycopy(buffer, HEADER_SIZE, imageData, 0, dataSize);

                // Lưu gói tin vào bộ đệm
                packetBuffer.computeIfAbsent(header.packetID, k -> new HashMap<>()).put(header.packetOrder, imageData);
                totalPacketsMap.put(header.packetID, header.totalPackets);

                // Kiểm tra xem đã nhận đủ các gói tin chưa
                if (packetBuffer.get(header.packetID).size() == header.totalPackets) {
                    Map<Integer, byte[]> packets = packetBuffer.remove(header.packetID);
                    totalPacketsMap.remove(header.packetID);

                    ByteArrayOutputStream fullImage = new ByteArrayOutputStream();
                    for (int i = 0; i < header.totalPackets; i++) {
                        fullImage.write(packets.get(i));
                    }
                    byte[] finalImage = fullImage.toByteArray();

                    if (isValidJPEG(finalImage)) {
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(finalImage));
                        if (image != null) {
                            ImageIcon icon = new ImageIcon(image);
                            label.setIcon(icon);
                            label.repaint();
                        }
                    } else {
                        System.err.println("Invalid JPEG data.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Phương thức chính
    public static void main(String[] args) {
        // Tạo giao diện nhập thông tin
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        JTextField ipField = new JTextField();
        JTextField portField = new JTextField();
        panel.add(new JLabel("Server IP Address:"));
        panel.add(ipField);
        panel.add(new JLabel("Client Port:"));
        panel.add(portField);

        // Hiển thị hộp thoại nhập thông tin
        int result = JOptionPane.showConfirmDialog(null, panel, "Enter Server Details", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            System.out.println("Operation cancelled.");
            return;
        }

        String serverIp = ipField.getText().trim();
        String clientPortInput = portField.getText().trim();

        // Kiểm tra đầu vào
        if (serverIp.isEmpty() || clientPortInput.isEmpty()) {
            System.err.println("IP Address and Port are required.");
            return;
        }

        int clientPort;
        try {
            clientPort = Integer.parseInt(clientPortInput);
        } catch (NumberFormatException e) {
            System.err.println("Invalid Port Number.");
            return;
        }

        // Nhận hình ảnh
        receiveImages(serverIp, clientPort);
    }
}
