package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>(); // мапа всех подключенных каналов

    public static void main(String[] args) {
        try {
            Selector selector = Selector.open(); //
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(8000));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("8000");

            while (true) {
                int countChannel = selector.select();//блокирует текущий поток, пока хотя бы один канал не будет готов к событиям, для которых он зарегистрирован
                if (countChannel == 0) {
                    System.out.println("continue");
                    continue;
                }
                Set<SelectionKey> setKeys = selector.selectedKeys();
                Iterator<SelectionKey> keysIterator = setKeys.iterator();
                while (keysIterator.hasNext()) {
                    SelectionKey key = keysIterator.next();
                    try {
                        if (key.isAcceptable()) {// key.channel() == serverSocketChannel
/*В этом if принимаем соединение от клиента  + регистрируем канал на чтение*/
                            acceptConnection(selector, serverSocketChannel);

                        } else if (key.isReadable() && (key.attachment() == null)) {
/*в этом if читаем Имя Юзера и добавляем к SelectionKey вложение в виде его Имени. Потом тащим это Имя везде*/
                            logIn(key);

                        } else if (key.isReadable() && (key.attachment() != null)) {
/*в этом if принимаем сообщение и регистрируем SelectionKey на запись*/
                            readMessage(selector, key);
                        } else if (key.isWritable()) {
/*в этом if отправляем сообщение всем Юзерам*/
                            sendMessageEveryone(selector, key);
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

    private static void acceptConnection(Selector selector, ServerSocketChannel serverSocketChannel) throws IOException {
        System.out.println("Соединены с Клиентом");
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        sockets.put(clientChannel, ByteBuffer.allocate(2 << 10));
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private static void logIn(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer clientBuffer = sockets.get(clientChannel);
        clientChannel.read(clientBuffer);

        String userName = new String(clientBuffer.array(),
                0,
                clientBuffer.remaining(),
                StandardCharsets.UTF_8
        );

        key.attach(userName);
        System.out.println("Клиент зарегистрирован: " + key.attachment());
        clientBuffer.clear();
    }

    private static void readMessage(Selector selector, SelectionKey key) throws IOException {
        System.out.println("Прнимаем сообщение от Юзера");
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer channelBuffer = sockets.get(clientChannel);
        int countBytes = clientChannel.read(channelBuffer);

        clientChannel.register(selector, SelectionKey.OP_WRITE, key.attachment());

        if (countBytes == -1) {
            System.out.println("Юзер вышел из списка");
            sockets.remove(clientChannel);
            clientChannel.close();
        }
    }

    private static void sendMessageEveryone(Selector selector, SelectionKey key) throws IOException {
        System.out.println("Отправляем сообщения всем Юзерам");
        for (SocketChannel channel : sockets.keySet()) {
            ByteBuffer channelBuffer = sockets.get(channel);
            channelBuffer.flip();

            String textFromClient = new String (channelBuffer.array(),
                    channelBuffer.position(),
                    channelBuffer.remaining(),
                    StandardCharsets.UTF_8);
            String textForAllUsers = key.attachment() + " : " + textFromClient;
            System.out.println(textForAllUsers);
            channelBuffer.clear();
            channel.write(ByteBuffer.wrap(textForAllUsers.getBytes(StandardCharsets.UTF_8)));

            channel.register(selector, SelectionKey.OP_READ, key.attachment());
        }
    }
}