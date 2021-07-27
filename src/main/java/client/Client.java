package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress("localhost", 8000));
            Scanner scanner = new Scanner(System.in);

            while(true) {
                //BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String lineToChat = scanner.nextLine(); //wrap - для переноса БайтовогоМассива в Буффер
                ByteBuffer bufferToServer = ByteBuffer.wrap(lineToChat.getBytes(StandardCharsets.UTF_8));
                socketChannel.write(bufferToServer);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
