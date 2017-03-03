package com.io.study;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.concurrent.CountDownLatch;

/**
 * 一个SocketClientRequestThread线程模拟一个客户端请求
 * @author: zouzhihui
 * @date: 2017-03-02 15-16
 */
public class SocketClientRequestThread implements Runnable {

    static {
        BasicConfigurator.configure();
    }

    private static final Log LOGGER = LogFactory.getLog(SocketClientRequestThread.class);

    private CountDownLatch countDownLatch;

    /**
     * 这个线程的编号
     */
    private Integer clientIndex;

    /**
     * countDownLatch是java提供的同步计数器
     * 当计数器数值减为0时，所有受其影响而等待的线程将会被激活。这样保证模拟并发请求的真实性
     * @param countDownLatch
     * @param clientIndex
     */
    public SocketClientRequestThread(CountDownLatch countDownLatch, Integer clientIndex) {
        this.countDownLatch = countDownLatch;
        this.clientIndex = clientIndex;
    }

    public void run() {
        Socket socket = null;
        OutputStream clientRequest = null;
        InputStream clientResponse = null;
        try {
            socket = new Socket("localhost", 83);
            clientRequest = socket.getOutputStream();
            clientResponse = socket.getInputStream();

            // 等待，知道SocketClientDemo完成所有线程的启动，然后所有线程一起发送请求
            this.countDownLatch.await();

            clientRequest.write(URLEncoder.encode("这是第" + this.clientIndex + " 个客户端的请求11。", "UTF-8").getBytes());
            clientRequest.flush();
            clientRequest.write(URLEncoder.encode("这是第" + this.clientIndex + " 个客户端的请求22。over", "UTF-8").getBytes());

            // 在这里等待，直到服务器返回信息
            LOGGER.info("第" + this.clientIndex + " 个客户端的请求发送完成，等待服务器返回信息");
            int maxLen = 1024;
            byte[] contextBytes = new byte[maxLen];
            int readLen;
            String message = "";
            while ((readLen = clientResponse.read(contextBytes, 0, maxLen)) != -1) {
                message += new String(contextBytes, 0, readLen);
            }
            LOGGER.info("接收到来自服务器的信息：" + message);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            try {
                if (null != clientRequest) {
                    clientRequest.close();
                }
                if (null != clientResponse) {
                    clientResponse.close();
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
