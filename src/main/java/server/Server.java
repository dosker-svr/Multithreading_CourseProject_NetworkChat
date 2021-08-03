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
    private static Map<SocketChannel, String> session = new HashMap<>(); // мапа для всех сессий
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
/*Что тут вижу:
    в 1ом цикле 'while (true)' доходим до 'while (keysIterator.hasNext())'.
    там 1ый if (key.isAcceptable) сработал, а потом снова итерация цикла 'while (true)'. и тут уже входим в 'else if(key.isReadable)'
    при вводе логина нужно уже быть readable. поэтому нужно сделать условие

    ещё проблема: не получаем в клиенте сообщение от сервера*/
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
                        if (key.isAcceptable()) { //key.channel() == serverSocketChannel
/*В этом if принимаем соединение от клиента  + регистрируем канал на чтение*/
                            acceptConnectAndRegisterChannelToRead(selector, serverSocketChannel);

                        } else if (key.isReadable() && !session.containsKey((SocketChannel) key.channel())) {
                                logIn((SocketChannel) key.channel());
                        } else if (key.isReadable() && session.containsKey((SocketChannel) key.channel())) {
                            /*в этом if принимаем сообщение*/
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
            sockets.put(clientChannel, ByteBuffer.allocate(2 << 10)); // нужно или нет ???
            clientChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void logIn(SocketChannel clientChannel) {
        // в цикле ждём пока клиент введёт своё имя
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
