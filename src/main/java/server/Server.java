package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
/*
на 27,07 - выяснить :
 1 - чем отличается keys от selectedKeys
    1 - вроде как keys - неизменяемый, selectedKeys - только для удаления
 2 - конкретный key во множестве Selector: это зарегистрированное событие(OP_ACCEPT , OP_READ, OP_WRITE...)
 3 - почему при вводе у клиента новой строки, у неё остаётся хвост от старой? затирается ровно кол-во введенных символов (указатель, position)
    3 - решено с помощью position + remaining
 4 - Selector - сущность ПРОСЛУШИВАНИЯ каналов. Каналы регистрируют 'событие/состояние' в Selector во множество Set<SelectionKey>.
     'событие/состояние' - это SelectionKey. Т.е. Selector состоит из множества SelectorKey.
     SelectorKey может быть OP_ACCEPT, OP_CONNECT, OP_READ, OP_WRITE.
     В SelectorKey - это Канал + Селектор
     */
public class Server {
    public static void main(String[] args) {
        try {
            Selector selector = Selector.open(); //
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open(); // Канал сервера
            serverSocketChannel.bind(new InetSocketAddress(8000)); // связывание. Говорим на каком порту вещает Канал Сервера
            serverSocketChannel.configureBlocking(false); // все операции Канала станут неБлокирующими
// регистрируем селектор за опред.каналом +|+ и операцию, на которой регистрируемся (регистрируемся с Операцией_Соединения):
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);// иначе: Регистрируем событие входящего соединения
            ByteBuffer bufferForText = ByteBuffer.allocate(2 << 10);
            System.out.println("8000");

            while (true) {
                int countChannel = selector.select();//этот метод блокирует текущий поток, пока хотя бы один канал не будет готов к событиям, для которых он зарегистрирован
                if (countChannel == 0) {
                    continue;
                }
                Set<SelectionKey> setKeys = selector.selectedKeys();
                Iterator<SelectionKey> keysIterator = setKeys.iterator();
                while (keysIterator.hasNext()) {
                    SelectionKey key = keysIterator.next();
                    try {
                        if (key.channel() == serverSocketChannel) {
                            System.out.println("зашли в key.channel() == serverSocketChannel");
                            SocketChannel clientChannel = serverSocketChannel.accept();
                            clientChannel.configureBlocking(false);
                            clientChannel.register(selector, SelectionKey.OP_READ);
                        }
                        else if (key.isReadable()){
                            System.out.println("зашли в if isReadable");
                            ((SocketChannel) key.channel()).read(bufferForText);
                            bufferForText.flip();
                            String textForChat = new String(bufferForText.array(),
                                    bufferForText.position(),
                                    bufferForText.remaining(),
                                    StandardCharsets.UTF_8
                            );
                            System.out.println("lol " + textForChat);
                            bufferForText.clear();
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
