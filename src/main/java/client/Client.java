package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress("localhost", 8000));
            Selector selector = Selector.open();

            Scanner scanner = new Scanner(System.in);
            System.out.println("Введите свой Логин:");
            String nameUser = scanner.nextLine();
            socketChannel.write(ByteBuffer.wrap(nameUser.getBytes(StandardCharsets.UTF_8)));


            while(true) {
                System.out.println("Введите сообщение для всего чата:");
                String lineToChat = scanner.nextLine();
                ByteBuffer bufferToServer = ByteBuffer.wrap(lineToChat.getBytes(StandardCharsets.UTF_8));
                socketChannel.write(bufferToServer);
                bufferToServer.flip();
                ByteBuffer bufFromServer = ByteBuffer.allocate(2 << 16);
                socketChannel.read(bufFromServer);
                System.out.println(new String(bufFromServer.array(),StandardCharsets.UTF_8));

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
