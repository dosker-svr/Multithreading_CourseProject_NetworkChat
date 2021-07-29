package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/* разбор nio :
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
    private static Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>(); // мапа всех подключенных каналов
    private static volatile List<String> listUsers = new ArrayList<>();

    public static void main(String[] args) {
        try {
            Selector selector = Selector.open(); //
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open(); // Канал сервера
            serverSocketChannel.bind(new InetSocketAddress(8000)); // связывание. Говорим на каком порту вещает Канал Сервера
            serverSocketChannel.configureBlocking(false); // все операции Канала станут неБлокирующими
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
                        if (key.channel() == serverSocketChannel) {
/*В этом if принимаем соединение от клиента  + регистрируем канал на чтение*/
                            System.out.println("Соединены с Клиентом");
                            SocketChannel clientChannel = serverSocketChannel.accept();
                            clientChannel.configureBlocking(false);
                            sockets.put(clientChannel, ByteBuffer.allocate(2 << 10));
                            clientChannel.register(selector, SelectionKey.OP_READ);

                        } else if (key.isReadable() && (key.attachment() == null)) {
/*в этом if читаем Имя Юзера и добавляем к SelectionKey вложение в виде его Имени. Потом тащим это Имя везде*/
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
                            listUsers.add(userName);
                            clientBuffer.clear();
                            //clientBuffer.flip();

                        } else if (key.isReadable() && (key.attachment() != null)) {
/*в этом if принимаем сообщение и регистрируем SelectionKey на запись*/
                            System.out.println("Прнимаем сообщение от Юзера");
                            SocketChannel clientChannel = (SocketChannel) key.channel();
                            ByteBuffer channelBuffer = sockets.get(clientChannel);
                            int countBytes = clientChannel.read(channelBuffer); //Удаленный хост принудительно разорвал существующее подключение
                            /*String textFromClient = new String(channelBuffer.array(),
                                    channelBuffer.position(),
                                    channelBuffer.remaining(),
                                    StandardCharsets.UTF_8
                            );
                            System.out.println(key.attachment().toString() + " : " + textFromClient);*/
                            /*if (channelBuffer.get(channelBuffer.position() - 1) == '\n') {
                                System.out.println("регистрируем OP_WRITE");
                                clientChannel.register(selector, SelectionKey.OP_WRITE, key.attachment());
                            }*/
                            //System.out.println("позиция buffer=" + (channelBuffer.position()));
                            //System.out.println("что в buffer.getChar=" + (channelBuffer.getChar(channelBuffer.position()-1)));
                            //System.out.println("что в buffer.get=" + (channelBuffer.get(channelBuffer.position()-3)));
                            //System.out.println("что в buffer.getChar()=" + (channelBuffer.getChar()));

                            clientChannel.register(selector, SelectionKey.OP_WRITE, key.attachment());

                            if (countBytes == -1) {
                                System.out.println("Юзер вышел из списка");
                                sockets.remove(clientChannel);
                                clientChannel.close();
                            }


                        } else if (key.isWritable()) {
                            System.out.println("Отправляем сообщения всем Юзерам");
                            for (SocketChannel channel : sockets.keySet()) {
                                ByteBuffer channelBuffer = sockets.get(channel);
                                //channelBuffer.flip();
                                channelBuffer.clear();
                                String textFromClient = new String (channelBuffer.array(),
                                        channelBuffer.position(),
                                        channelBuffer.remaining(),
                                        StandardCharsets.UTF_8);
                                String textForAllUsers = key.attachment() + " : " + textFromClient;
                                System.out.println(textForAllUsers);
                                channelBuffer.flip();
                                channel.write(ByteBuffer.wrap(textForAllUsers.getBytes(StandardCharsets.UTF_8)));

                                channel.register(selector, SelectionKey.OP_READ, key.attachment());
                                /*if (!channelBuffer.hasRemaining()) {
                                    channelBuffer.compact(); // buffer.clear();
                                    channel.register(selector, SelectionKey.OP_READ, key.attachment());
                                }*/
                            }


                            /*SocketChannel clientChannel = (SocketChannel) key.channel();
                            clientChannel.write(sockets.get(clientChannel));*/
                        }
                    } finally {
                        keysIterator.remove();
                    }
                }


                /*while(true) {
                    int bytes = clientChannel.read(bufferForText);
                    bufferForText.flip();
                    System.out.println("кол-во байт="+bytes);
                    System.out.println();
                    String textForChat = new String(bufferForText.array(), StandardCharsets.UTF_8);
                    System.out.println("lol " + textForChat);

                    bufferForText.clear();
                }*/

                /*int countChannel = selector.select();// количество каналов зарегистрированных на этом селекторе, готовых к зарег.Опарации
                System.out.println("countChannel="+countChannel);

                if (countChannel == 0) {
                    try {
                        Thread.sleep(2_000);
                        System.out.println("спим,ждём подключений");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }*/

                /*int bytes = clientChannel.read(bufferForText);
                System.out.println("кол-во байт="+bytes);
                System.out.println();
                String textForChat = new String(bufferForText.array(), 0, bytes, StandardCharsets.UTF_8);
                System.out.println("lol " + textForChat);*/

                /*Set<SelectionKey> setKeys = selector.keys();// множество ключей(ключ - желание канала выполнить зарегистрированную операцию)
                System.out.println(setKeys.size());
                Iterator<SelectionKey> keysIterator = setKeys.keysIterator();// берём канал, готовый произвоить какую-то операцию
                while (keysIterator.hasNext()) {
                    SocketChannel clientChannel = null;
                    SelectionKey key = (SelectionKey) keysIterator.next();
                    //keysIterator.remove();
                    if (key.isAcceptable()) {
                        clientChannel = serverSocketChannel.accept();
                        clientChannel.configureBlocking(false);
                        clientChannel.register(selector, SelectionKey.OP_READ); // SelectionKey.OP_WRITE |
                    }
                    if (key.isReadable()) {
                        ByteBuffer bufferForText = ByteBuffer.allocate(2 << 10);
                        int bytes = clientChannel.read(bufferForText);
                        String textForChat = new String(bufferForText.array(), 0, bytes, StandardCharsets.UTF_8);
                        System.out.println("lol " + textForChat);
                    }
                }*/

                /*while (keysIterator.hasNext()) {
                    SelectionKey key = keysIterator.next();

                    if (key.channel() == serverSocketChannel) {
                        System.out.println("key.channel() == serverSocketChannel");
                        SocketChannel clientChannel2 = serverSocketChannel.accept(); // получение Канала от входящего сокета
                        clientChannel.configureBlocking(false);
                        clientChannel.register(selector, SelectionKey.OP_READ);

                        System.out.println(clientChannel2.isConnected());

                        ///////////////////////
                        int countBytes = clientChannel.read(bufferForText);
                        if (countBytes == -1) {
                            break;
                        }
                        String textForChat = new String(bufferForText.array(), 0, countBytes, StandardCharsets.UTF_8);
                        System.out.println("lol " + textForChat);
                        bufferForText.clear();

                        ByteBuffer bufferForChat = ByteBuffer.wrap(textForChat.getBytes(StandardCharsets.UTF_8));
                        clientChannel.write(bufferForChat);
                        ////////////////
                    } else {
                        System.out.println("666");
                    }
                }*/
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
