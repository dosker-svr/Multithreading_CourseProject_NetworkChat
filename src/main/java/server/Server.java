package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static Map<SocketChannel, String> session = new HashMap<>();
    private static Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>();
    private static volatile List<String> listUsers = new ArrayList<>();

    public static void main(String[] args) {
        start();
    }

    public static void start() {
        try {
            Selector selector = Selector.open(); //
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(8000));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("8000");

            while (true) {
                int countChannel = selector.select();
                if (countChannel == 0) {
                    System.out.println("continue");
                    continue;
                }
                Set<SelectionKey> setKeys = selector.selectedKeys();
                Iterator<SelectionKey> keysIterator = setKeys.iterator();
                while (keysIterator.hasNext()) {
                    SelectionKey key = keysIterator.next();
                    try {
                        if (key.isAcceptable()) {
                            acceptConnectAndRegisterChannelToRead(selector, serverSocketChannel);

                        } else if (key.isReadable() && !session.containsKey((SocketChannel) key.channel())) {
                                logIn((SocketChannel) key.channel());
                        } else if (key.isReadable() && session.containsKey((SocketChannel) key.channel())) {

                            readMessage(key);
                        }
                    } finally {
                        keysIterator.remove();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void acceptConnectAndRegisterChannelToRead(Selector selector, ServerSocketChannel serverSocketChannel) {
        System.out.println("Соединены с Клиентом");
        try {
            SocketChannel clientChannel = serverSocketChannel.accept();
            clientChannel.configureBlocking(false);
            sockets.put(clientChannel, ByteBuffer.allocate(2 << 10));
            clientChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void logIn(SocketChannel clientChannel) {
        try {
            ByteBuffer bufferForName = ByteBuffer.allocate(2 << 10);
            int byteFromClient = clientChannel.read(bufferForName);
            System.out.println("кол-во байт в имени=" + byteFromClient);
            bufferForName.flip();
            String userName = new String(bufferForName.array(), StandardCharsets.UTF_8);
            session.put(clientChannel, userName);
            sendEveryoneMessage("подключился пользователь");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void readMessage(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer channelBuffer = ByteBuffer.allocate(2 << 10);
        int numRead = 0;
        try {
            int countByte = channel.read(channelBuffer);
            numRead = countByte;
            if (countByte == -1) {
                session.remove(channel);
                sendEveryoneMessage("вышел из чата");
                channel.close();
                key.cancel();
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
// понять что происходит дальше:
        byte[] data = new byte[numRead];
        System.arraycopy(channelBuffer.array(), 0, data, 0, numRead);
        String gotData = new String(data);
        System.out.println("Got: " + gotData);
        sendEveryoneMessage(gotData);
    }

    private static void sendEveryoneMessage(String message) {
        session.forEach((socketChannel, userName) -> {
            String fullMessage = "'" + userName + "': " + message;
            ByteBuffer bufferForMessage = ByteBuffer.wrap(fullMessage.getBytes(StandardCharsets.UTF_8));
            bufferForMessage.flip();
            try {
                System.out.println("пишем клиенту= "+fullMessage);
                socketChannel.write(bufferForMessage);
                bufferForMessage.flip();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
