package com.benxiang.sokect;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {
    private Socket socket = null;
    private String ip, port, sendData;
    private TextView content;
    private Button send, connect, receiver;
    private EditText ipAddress, portNumber, sendContent;
    private HandlerThread sendHandlerThread;
    private Handler sendHandler;
    private InputStream mInputStream;
    private OutputStream ou;
    private ReceiveThread receiveThread;
    private DatagramSocket datagramSocket;
    //定义消息
    private Bundle bundle;

    //更新UI
    @SuppressLint("HandlerLeak")
    public Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            if (msg.what == 0x11) {
                content.append("receive:" + bundle.getString("msg") + "\n");
            } else if (msg.what == 0x12) {
                content.append("send:" + bundle.getString("msg") + "\n");
            } else if (msg.what == 0x14) {
                content.append(bundle.getString("msg") + "\n");
            } else if (msg.what == 0x15) {
                content.append(bundle.getString("msg") + "\n");
            } else {
                content.append("error:" + bundle.getString("msg") + "\n");
            }
        }
    };

    //发送线程
    private Runnable sendRunnable = new Runnable() {
        @Override
        public void run() {
            //向服务器发送信息
            try {
                ou.write(sendData.getBytes("gbk"));
                ou.flush();
                sendMessage(sendData, (byte) 0x12);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        content = (TextView) findViewById(R.id.tv_content);
        send = (Button) findViewById(R.id.btn_send);
        connect = (Button) findViewById(R.id.btn_connect);
        ipAddress = (EditText) findViewById(R.id.et_ip_address);
        portNumber = (EditText) findViewById(R.id.et_port_number);
        sendContent = (EditText) findViewById(R.id.et_send_content);
        receiver = findViewById(R.id.btn_receive);

        //接收消息定义
        bundle = new Bundle();
        //线程池
        sendHandlerThread = new HandlerThread("SendThread", 5);
        sendHandlerThread.start();
        sendHandler = new Handler(sendHandlerThread.getLooper());


        connect.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ip = ipAddress.getText().toString().trim();
                port = portNumber.getText().toString().trim();
                if (!ip.equals("") && !port.equals("")) {
                    connectSocket(ip, Integer.parseInt(port));
                } else {
                    sendMessage("IP地址和端口号不能为空！", (byte) 0x13);
                }
            }
        });
        send.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                sendData = sendContent.getText().toString().trim();
                if (!"".equals(sendData)) {
                    send();
                } else {
                    sendMessage("发送内容不能为空！", (byte) 0x13);
                }
            }
        });

        receiver.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new UDPReceiveThread().start();
            }
        });
    }

    /**
     * 更新UI内容
     */
    private void sendMessage(String sendStr, byte msgWhat) {
        bundle.putString("msg", sendStr);
        Message msg = new Message();
        msg.what = msgWhat;
        msg.setData(bundle);
        myHandler.sendMessage(msg);
    }

    /**
     * 连接服务器
     */
    private void connectSocket(final String ipAddress, final int port) {
        if (socket == null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        socket = new Socket();
                        socket.connect(new InetSocketAddress(ipAddress, port), 1000); //端口号为30000
                        //获取输入输出流
                        ou = socket.getOutputStream();
                        mInputStream = socket.getInputStream();
                        receiveThread = new ReceiveThread();
                        receiveThread.start();
                    } catch (IOException e) {
                        sendMessage("服务器连接失败！请检查IP地址和端口号是否有误！", (byte) 0x13);
                        e.printStackTrace();
                    }

                }
            }).start();
        }
    }

    /**
     * 连接UDP
     */
    private void UDPConnect() throws SocketException {
        if (datagramSocket == null) {
            datagramSocket = new DatagramSocket(8888);
        }
    }

    /**
     * 发送TCP
     */
    private void send() {
        sendHandler.post(sendRunnable);
    }

    /**
     * 接收UDP广播
     *
     * @param datagramPacket
     * @throws IOException
     */
    private void recvPacket(DatagramPacket datagramPacket) throws IOException {
        if (datagramSocket != null) {
            datagramSocket.receive(datagramPacket);
            datagramSocket.setSoTimeout(2000);
        }
    }

    /**
     * 接收TCP子线程
     */
    class ReceiveThread extends Thread {
        @Override
        public void run() {
            String strBuffer = "";
            while (!isInterrupted()) {
                try {
                    if (mInputStream == null) return;
                    byte[] buffer = new byte[512];
                    int size = mInputStream.read(buffer);
                    if (size > 0) {
                        //读取发来服务器信息
                        strBuffer = new String(buffer);
                        sendMessage(strBuffer, (byte) 0x11);
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 接收UDP广播后连接TCP
     */
    class UDPReceiveThread extends Thread {
        @Override
        public void run() {
            byte[] by = new byte[1024];
            DatagramPacket datagramPacket = new DatagramPacket(by, by.length);
            while (true) {
                try {
                    UDPConnect();
                    Thread.sleep(1000);
                    recvPacket(datagramPacket);
                    String str = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
                    String quest_ip = datagramPacket.getAddress().toString().trim();
                    sendMessage("IPAddress:" + quest_ip + "---receiveContent:" + str, (byte) 0x15);
                    Thread.sleep(500);
                    if (!quest_ip.equals("") && socket == null) {
                        int i = quest_ip.indexOf('/');
                        quest_ip = quest_ip.substring(i + 1);
                        sendMessage("连接TCP:" + quest_ip.trim(), (byte) 0x14);
                        connectSocket(quest_ip.trim(), 9999);
                    }
                } catch (IOException e) {
                    sendMessage(e.getMessage(), (byte) 0x13);
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}
