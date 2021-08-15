package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class Server {
    private static Map<SocketChannel, String> session = new HashMap<>(); // мапа для всех сессий. значение - имя Юзера
    private static Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>(); // мапа всех подключенных каналов
    private static volatile List<String> listUsers = new ArrayList<>();

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
                Iterator<SelectionKey> keysIterator = setKeys.iterator();//размер iteartor всегда = 1
                while (keysIterator.hasNext()) {
                    SelectionKey key = keysIterator.next();
                    keysIterator.remove();
                    if (key.isAcceptable()) {
/*В этом if принимаем соединение от клиента  + регистрируем канал на чтение*/
                        acceptConnection(selector, serverSocketChannel);

                    } else if (key.isReadable() && !session.containsKey((SocketChannel) key.channel())) {
/*в этом if читаем Имя Юзера и добавляем в мапу session имя*/
                        logIn(key, selector);

                    } else if (key.isReadable() && session.containsKey((SocketChannel) key.channel())) {
/*в этом if принимаем сообщение и регистрируем SelectionKey на запись*/
                        readMessage(key, selector);
                    } else if (key.isWritable()) {
/*в этом if отправляем сообщения всем Клиентам*/
                        sendEveryoneMessage(selector, key);
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

    private static void logIn(SelectionKey key, Selector selector) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer clientBuffer = sockets.get(clientChannel);
        clientChannel.read(clientBuffer);

        String userName = new String(clientBuffer.array(),
                0,
                clientBuffer.remaining(),
                StandardCharsets.UTF_8
        );

        session.put(clientChannel, userName);
        System.out.println("Клиент зарегистрирован: " + userName);
        clientBuffer.clear();
        clientChannel.register(selector, SelectionKey.OP_WRITE);

        messageForEveryone("подключился пользователь " + userName, selector);
    }

    private static void readMessage(SelectionKey key, Selector selector) throws IOException {
        System.out.println("Прнимаем сообщение от Юзера");
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer channelBuffer = sockets.get(clientChannel);
        channelBuffer.clear();
        int countBytes = clientChannel.read(channelBuffer);

        clientChannel.register(selector, SelectionKey.OP_WRITE);

        if (countBytes == -1) {
            System.out.println("Юзер вышел из списка");
            sockets.remove(clientChannel);
            clientChannel.close();
        }
    }

    private static void sendEveryoneMessage(Selector selector, SelectionKey key) throws ClosedChannelException {
        System.out.println("Отправляем сообщения всем Юзерам");
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer clientBuffer = sockets.get(clientChannel);
        clientBuffer.flip();
        String userName = session.get(clientChannel);
        String message = userName + ": " + new String(clientBuffer.array(),
                0,
                clientBuffer.remaining(),
                StandardCharsets.UTF_8
        );
        messageForEveryone(message, selector);
    }

    private static void messageForEveryone(String message, Selector selector) {
        session.forEach((socketChannel, userName) -> {
            String fullMessage = message; //String fullMessage = "'" + userName + "': " + message;
            ByteBuffer bufferForMessage = ByteBuffer.wrap(fullMessage.getBytes(StandardCharsets.UTF_8));
            bufferForMessage.flip();

            try {
                socketChannel.register(selector, SelectionKey.OP_WRITE);
                System.out.println("для клиента=" + userName + " - " + fullMessage);
                sockets.get(socketChannel).clear();//NEW
                socketChannel.write(bufferForMessage);
                socketChannel.register(selector, SelectionKey.OP_READ);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}