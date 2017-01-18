package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Rogier on 16-12-16 in Enschede.
 */
public class ServerPlayer implements Runnable, Player {
    private Socket socket;
    private String name;
    private Lobby lobby;
    private boolean connected;
    private BufferedReader in;
    private PrintWriter out;
    private ServerEvents state;
    private ClientMessage lastMessage;
    private Lock lock;
    private Condition moveMessageReceived;
    private Timer timer;


    public ServerPlayer(Socket socket, BufferedReader in, Lobby lobby) {
        this.socket = socket;
        this.lobby = lobby;
        this.in = in;
        this.connected = true;
        try {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            connected = false;
            e.printStackTrace();
        }
        this.lock = new ReentrantLock();
        moveMessageReceived = lock.newCondition();
        state = ServerEvents.DISCONNECTED;
        timer = new Timer();
    }

    public void run() {
        try {
            while (isConnected()) {
                String userInput;
                try {
                    while ((userInput = in.readLine()) != null && isConnected()) {
                        //                    System.out.println(userInput);
                        lastMessage = new ClientMessage(userInput);
                        switch (lastMessage.getAction()) {
                            case CONNECT:
                                this.connect();
                                break;
                            case JOIN:
                                this.join();
                                break;
                            case START:
                                if (state != ServerEvents.GAME) {
                                    throw new WrongMessageException();
                                }
                                state = ServerEvents.STARTED;
                                break;
                            case MOVE:
                                if (state == ServerEvents.MOVE_DENIED) {

                                } else if (state == ServerEvents.MAKE_MOVE) {
                                    lock.lock();
                                    moveMessageReceived.signal();
                                    lock.unlock();
                                }
                                break;
                            case RESTART:
                                state = ServerEvents.STARTED;
                                break;
                            case EXIT_GAME:
                                state = ServerEvents.LOBBY;
                                break;
                            case DISCONNECT:
                                connected = false;
                                break;
                        }
                    }
                } catch (IOException e) {
                    connected = false;
                } catch (WrongMessageException e) {
                    System.out.println("Wrong message received");
                    this.sendError("missing keys", "invalid keys used");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            lobby.disconnectPlayer(this);
            socket.close();
            System.out.println("Closed");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connect() throws WrongMessageException {
        if (state != ServerEvents.DISCONNECTED) {
            throw new WrongMessageException();
        }


        this.name = lastMessage.getName();
        boolean validName = lobby.addPlayerToLobby(this);
        if (validName) {
            this.sendLobbyStatus();
            state = ServerEvents.LOBBY;
            System.out.println("Player connected: " + lastMessage.getName() + "/" + this.getInetAddress().toString());
        } else {
            this.sendError("lobby entry denied", "name or IP is already used or invalid characters");
            try {
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void join() throws WrongMessageException {
        if (state != ServerEvents.LOBBY) {
            throw new WrongMessageException();
        }
        int roomNumber = lastMessage.getLobbyNumber();
        String opponent = lobby.getOpponentName(roomNumber);
        boolean validGame = lobby.addPlayerToRoom(this, roomNumber);
        if (validGame) {
            System.out.println(this.name + " added to a game");
            this.sendGameStatus(opponent);
            state = ServerEvents.GAME;
        } else {
            this.sendError("game full", "the game is full at the moment");
        }
    }


    private boolean isConnected() throws IOException {
        return connected && socket.getInetAddress().isReachable(500);
    }

    public String getName() {
        return name;
    }

    public void sendLobbyStatus() {
        String json = ServerMessage.sendLobbyStatus(lobby.getFreeRooms());
        this.sendMessageToClient(json);
    }

    public void sendGameStatus(String opponent) {
        String json = ServerMessage.sendGameStatus(opponent);
        this.sendMessageToClient(json);
    }

    private void sendError(String reason, String message) {
        String json = ServerMessage.sendError(reason, message);
        this.sendMessageToClient(json);
    }

    public void sendMoveRequest() {
        String json = ServerMessage.sendMakeMove();
        this.sendMessageToClient(json);
    }

    public void sendOpponentMoved(String move) {
        String json = ServerMessage.sendOpponentMoved(move);
        this.sendMessageToClient(json);
    }

    public void sendGameStarted(String opponentName) {
        String json = ServerMessage.sendGameStarted(opponentName);
        this.sendMessageToClient(json);
    }

    private void sendMessageToClient(String json) {
        this.out.println(json);
        this.out.flush();
    }

    public String requestMove() {
        lock.lock();
        state = ServerEvents.MAKE_MOVE;
        this.sendMoveRequest();
//        timer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                state = ServerEvents.MOVE_DENIED;
//            }
//        },15000);
        try {
            moveMessageReceived.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        lock.unlock();
        return lastMessage.getMove();
    }

    public String moveDenied() {
        lock.lock();
        this.sendError("move denied", "the move was not permitted");
        this.sendMoveRequest();

        try {
            moveMessageReceived.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        lock.unlock();
        return lastMessage.getMove();
    }

    public void announceWinner(String winner, String[] winningMove) {
        String json = ServerMessage.sendGameOver(winner, winningMove);
        this.out.println(json);
        this.out.flush();
    }

    public InetAddress getInetAddress() {
        return socket.getInetAddress();
    }

    public boolean wantsToStart() {
        return state == ServerEvents.STARTED;
    }

}
