package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;/*
 1 - чем отличается keys от selectedKeys
     1 - вроде как keys - неизменяемый, selectedKeys - только для удаления
2 - конкретный key во множестве Selector: это зарегистрированное событие(OP_ACCEPT , OP_READ, OP_WRITE...)
3 - почему при вводе у клиента новой строки, у неё остаётся хвост от старой? затирается ровно кол-во введенных символов (указатель, position)
     3 - решено с помощью position + remaining
4 - Selector - сущность ПРОСЛУШИВАНИЯ каналов. Каналы регистрируют 'событие/состояние' в Selector во множество Set<SelectionKey>.
 'событие/состояние' - это SelectionKey(иначе события/эвенты). Т.е. Selector состоит из множества SelectorKey.
 SelectorKey может быть OP_ACCEPT, OP_CONNECT, OP_READ, OP_WRITE.
 В SelectorKey - это Канал + Селектор

 ИСПРАВИТЬ поломки:
 Если один клиент прерывает соединение, сервер выкидывает исключение и выключается. ИСПРАВИТЬ (Удаленный хост принудительно разорвал существующее подключение)
 Остановлено на:
 - Создали Общение Клиент-Сервер.
 - Указания адреса и № порта в настройках в txt или ...
 - Клиенту перед отправкой сообщения нужно указать своё имя.
 - Сервер принимает сообщение. Добавляет к нему имя + время. Транслирует всем (или придумать в другое местро)*/

public class Server {
    private static Map<SocketChannel, String> session = new HashMap<>();
    private static Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>(); // мапа всех подключенных каналов
    private static volatile List<String> listUsers = new ArrayList<>();

    public static void main(String[] args) {
        start();
    }

    public static void start() {
        try {
            Selector selector = Selector.open(); //
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(8000));
            serverSocketChannel.configureBlocking(false);// все операции Канала станут неБлокирующими
// регистрируем селектор за опред.каналом +|+ и операцию, на которой регистрируемся (регистрируемся с Операцией_Соединения):
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);// иначе: Регистрируем событие входящего соединения
            System.out.println("8000");

            while (true) {
                int countChannel = selector.select();//этот метод блокирует текущий поток, пока хотя бы один канал не будет готов к событиям, для которых он зарегистрирован
                if (countChannel == 0) {
                    System.out.println("continue");
                    continue;
                }
                Set<SelectionKey> setKeys = selector.selectedKeys();
                Iterator<SelectionKey> keysIterator = setKeys.iterator();
                while (keysIterator.hasNext()) {
                    SelectionKey key = keysIterator.next();
                    try {
                        if (key.channel() == serverSocketChannel) { //key.isAcceptable()
/*В этом if принимаем соединение от клиента  + регистрируем канал на чтение*/
                            acceptConnectAndRegisterChannelToRead(selector, serverSocketChannel);

                        } else if(key.isReadable()) {
                            /*в этом if принимаем сообщение*/
                            readMessage(key);
                        }
//                        else if (key.isReadable() && (key.attachment() == null)) {
///*в этом if читаем Имя Юзера и добавляем к SelectionKey вложение в виде его Имени. Потом тащим это Имя везде*/
//                            SocketChannel clientChannel = (SocketChannel) key.channel();
//                            ByteBuffer clientBuffer = sockets.get(clientChannel);
//                            clientChannel.read(clientBuffer);
//
//                            String userName = new String(clientBuffer.array(),
//                                    0,
//                                    clientBuffer.remaining(),
//                                    StandardCharsets.UTF_8
//                            );
//
//                            key.attach(userName);
//                            System.out.println("Клиент зарегистрирован: " + key.attachment());
//                            listUsers.add(userName);
//                            clientBuffer.clear();
//                            //clientBuffer.flip();
//
//                        } else if (key.isReadable() && (key.attachment() != null)) {
///*в этом if принимаем сообщение и регистрируем SelectionKey на запись*/
//                            System.out.println("Прнимаем сообщение от Юзера");
//                            SocketChannel clientChannel = (SocketChannel) key.channel();
//                            ByteBuffer channelBuffer = sockets.get(clientChannel);
//                            int countBytes = clientChannel.read(channelBuffer);
//
//                            clientChannel.register(selector, SelectionKey.OP_WRITE, key.attachment());
//
//                            if (countBytes == -1) {
//                                System.out.println("Юзер вышел из списка");
//                                sockets.remove(clientChannel);
//                                clientChannel.close();
//                            }
//
//
//                        } else if (key.isWritable()) {
//                            System.out.println("Отправляем сообщения всем Юзерам");
//                            SocketChannel messageChannel = (SocketChannel) key.channel();
//                            ByteBuffer messageBuffer = sockets.get(messageChannel);
//                            messageBuffer.flip();
//                            String messFromClient = new String(messageBuffer.array(),
//                                    messageBuffer.position(),
//                                    messageBuffer.remaining(),
//                                    StandardCharsets.UTF_8);
//                            String messForAllUsers = key.attachment() + " : " + messFromClient;
//                            messageBuffer.clear();
//                            messageChannel.register(selector, SelectionKey.OP_READ, key.attachment());
//
//                            for (SocketChannel channel : sockets.keySet()) {
//                                /*ByteBuffer channelBuffer = sockets.get(channel);
//                                channelBuffer.flip();
//
//                                String textFromClient = new String (channelBuffer.array(),
//                                        channelBuffer.position(),
//                                        channelBuffer.remaining(),
//                                        StandardCharsets.UTF_8);
//                                String textForAllUsers = key.attachment() + " : " + textFromClient;
//                                System.out.println(textForAllUsers);
//                                channelBuffer.clear();
//                                channel.write(ByteBuffer.wrap(messForAllUsers.getBytes(StandardCharsets.UTF_8)));
//                                channel.register(selector, SelectionKey.OP_READ, key.attachment());*/
//                                channel.write(ByteBuffer.wrap(messForAllUsers.getBytes(StandardCharsets.UTF_8)));
//                            }
//                        }
                    } finally {
                        keysIterator.remove();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private static void acceptConnectAndRegisterChannelToRead(Selector selector, ServerSocketChannel serverSocketChannel) throws IOException {
        System.out.println("Соединены с Клиентом");
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        sockets.put(clientChannel, ByteBuffer.allocate(2 << 10)); // нужно или нет ???
        clientChannel.register(selector, SelectionKey.OP_READ);
        // в цикле ждём пока клиент введёт своё имя
        ByteBuffer bufferForName = ByteBuffer.allocate(2 << 10);
        int byteFromClient = clientChannel.read(bufferForName);
/*// ВОТ ОТСЮДА
        while (byteFromClient <= 0) {
            clientChannel.read(bufferForName);
        }
// ДО СЮДА - может быть неправильно считываю всё имя*/
        System.out.println("кол-во байт в имени="+byteFromClient);
        bufferForName.flip();
        String userName = new String (bufferForName.array(), StandardCharsets.UTF_8);
        session.put(clientChannel, userName);
        sendEveryoneMessage("подключился пользователь");
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
                socketChannel.write(bufferForMessage);
                bufferForMessage.flip();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
