import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.imageio.ImageIO;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.opencv.*;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;
import org.opencv.videoio.VideoCapture;
public class DroicamClientGUI extends JFrame {
    private JPanel controlPanel;
    private JButton connectButton;
    private JButton disconnectButton;
    private JButton captureButton;
    private JLabel imageDisplay;
    private JTextField ipField;
    private JComboBox<String> qualityComboBox;
    private static Process exeProcess;

    public DroicamClientGUI() {
        setTitle("Droicam Client");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Khu vực hiển thị hình ảnh
        imageDisplay = new JLabel();
        imageDisplay.setBackground(Color.BLACK);
        imageDisplay.setOpaque(true);
        imageDisplay.setHorizontalAlignment(SwingConstants.CENTER);
        imageDisplay.setText("Kết nối để xem video từ camera");
        add(imageDisplay, BorderLayout.CENTER);

        // Panel điều khiển chứa các nút và trường nhập IP
        controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout());

        // Trường nhập IP
        ipField = new JTextField(15);
        ipField.setToolTipText("Nhập địa chỉ IP của camera");

        // ComboBox chọn chất lượng video
        qualityComboBox = new JComboBox<>();
        qualityComboBox.addItem("360x360");
        qualityComboBox.addItem("720x720");
        qualityComboBox.addItem("1080x1080");
        qualityComboBox.setToolTipText("Chọn chất lượng video");

        // Tạo các nút chức năng
        connectButton = new JButton("Kết nối");
        disconnectButton = new JButton("Ngắt kết nối");
        disconnectButton.setEnabled(false);
        captureButton = new JButton("Chụp ảnh");

        // Thêm các thành phần vào panel điều khiển
        controlPanel.add(new JLabel("Địa chỉ IP:"));
        controlPanel.add(ipField);
        controlPanel.add(new JLabel("Chất lượng:"));
        controlPanel.add(qualityComboBox);
        controlPanel.add(connectButton);
        controlPanel.add(disconnectButton);
        controlPanel.add(captureButton);

        // Thêm panel điều khiển vào cửa sổ
        add(controlPanel, BorderLayout.SOUTH);

        // Thêm sự kiện cho các nút
        qualityComboBox.addActionListener(e->selectedCombobox());
        connectButton.addActionListener(e -> connectToCamera());
        disconnectButton.addActionListener(e -> disconnectFromCamera());
        captureButton.addActionListener(e -> captureImage());

        // MenuBar
        createMenuBar();
    }
private void selectedCombobox() {
    	String quanlity  = (String)qualityComboBox.getSelectedItem();
    	try 
    	{
        	DatagramSocket data = new DatagramSocket(5001);
            String ipAddress = ipField.getText();
            InetAddress serverAddress = InetAddress.getByName(ipAddress);
            DatagramPacket packet = new DatagramPacket(quanlity.getBytes(), quanlity.getBytes().length, serverAddress, 5001);
            data.send(packet);
            data.close();
		} 
    	catch (Exception e) 
    	{
			
		}
    }
    
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem exitMenuItem = new JMenuItem("Exit");
        exitMenuItem.addActionListener(e -> System.exit(0));

        fileMenu.add(exitMenuItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }
    Thread cameraThread;
 // Phương thức chuyển đổi Mat thành BufferedImage
    private BufferedImage MatToBufferedImage(Mat mat) {
        int width = mat.width();
        int height = mat.height();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double[] pixel = mat.get(y, x); // Lấy pixel từ Mat
                int argb = ((int) pixel[2] & 0xFF) << 16 | // Red
                           ((int) pixel[1] & 0xFF) << 8  | // Green
                           ((int) pixel[0] & 0xFF);       // Blue
                image.setRGB(x, y, argb);
            }
        }

        return image;
    }
    private boolean isRunning = false; // Cờ để kiểm tra trạng thái luồng
    private Mat frame;
    private void connectToCamera() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        
        String ipAddress = ipField.getText();
        String quality = (String) qualityComboBox.getSelectedItem();

        if (ipAddress.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập địa chỉ IP!");
        } else {
        	connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            try {
                if (exeProcess == null) {
                    String exePath = "E:\\KI5\\PBL4_2\\examples\\TestCamSocket\\x64\\Release\\TestCamSocket.exe";
                    exeProcess = Runtime.getRuntime().exec(new String[]{exePath, ipAddress});
                    
                    Thread outputThread = new Thread(() -> {
                        try {
                            BufferedReader stdOutput = new BufferedReader(new InputStreamReader(exeProcess.getInputStream()));
                            String s;
                            while ((s = stdOutput.readLine()) != null) {
                            	System.out.println("Output: " + s);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    Thread errorThread = new Thread(() -> {
                        try {
                            BufferedReader stdError = new BufferedReader(new InputStreamReader(exeProcess.getErrorStream()));
                            String s;
                            while ((s = stdError.readLine()) != null) {
                                System.out.println("Error: " + s);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    outputThread.start();
                    errorThread.start();
                    JOptionPane.showMessageDialog(this, "Đã kết nối với camera tại IP: " + ipAddress + "\nChất lượng video: " + quality);
                    imageDisplay.setText("");

                    // Khởi động cameraThread với điều kiện isRunning
                    isRunning = true;
                    cameraThread = new Thread(() -> {
                        VideoCapture camera = new VideoCapture(2);
                        if (!camera.isOpened()) {
                            System.out.println("Không thể mở camera");
                            return;
                        }
                        this.frame = new Mat();
                        while (isRunning) {  // Sử dụng cờ isRunning để kiểm tra
                            camera.read(frame);

                            if (frame.empty()) {
                                System.out.println("Không có khung hình");
                                break;
                            }

                            ImageIcon imageIcon = new ImageIcon(MatToBufferedImage(frame));
                            SwingUtilities.invokeLater(() -> imageDisplay.setIcon(imageIcon));
                        }
                        camera.release();
                    });

                    cameraThread.start();
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Lỗi khi chạy file EXE: " + e.getMessage());
            }
        }
    }

    private void disconnectFromCamera() {
        if (exeProcess != null) {
            exeProcess.destroy();
            exeProcess = null;
            isRunning = false;  // Đặt cờ isRunning thành false để dừng cameraThread
            
            try {
                DatagramSocket data = new DatagramSocket(5000);
                String message = "close";
                String ipAddress = ipField.getText();
                InetAddress serverAddress = InetAddress.getByName(ipAddress);
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.getBytes().length, serverAddress, 5000);
                data.send(packet);
                data.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            JOptionPane.showMessageDialog(this, "Đã ngắt kết nối camera!");
            imageDisplay.setText("Kết nối để xem video từ camera");
            imageDisplay.setIcon(null);
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            this.frame = null;
        } else {
            JOptionPane.showMessageDialog(this, "Chưa có kết nối nào để ngắt!");
        }
    }



    private void captureImage() {
        try {
        	if (this.frame == null || this.frame.empty()) {
                JOptionPane.showMessageDialog(this, "Không có ảnh để chụp!");
                return;
            }
        	
        	BufferedImage img = MatToBufferedImage(this.frame); 
        	String timetmp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        	
        	JFileChooser dirChooser = new JFileChooser();
            dirChooser.setDialogTitle("Chọn thư mục để lưu ảnh");
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            
            if(dirChooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) {
            	File selectDir = dirChooser.getSelectedFile();
            	String fileName = selectDir.getAbsolutePath() + File.separator +  "image_"+timetmp + ".jpg";
                ImageIO.write(img, "jpg", new File(fileName));
                JOptionPane.showMessageDialog(this, "Chụp ảnh thành công! Lưu vào file " + fileName);
            }else {
            	JOptionPane.showMessageDialog(this,"Chụp ảnh đã bị hủy ");
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi chụp ảnh: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DroicamClientGUI droicamClientGUI = new DroicamClientGUI();
            droicamClientGUI.setVisible(true);
        });
        
    }
}
