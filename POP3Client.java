import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.util.*;


public class POP3Client
{
    private Socket socket;
    private boolean debug = false;
    private BufferedReader reader;
    private PrintWriter writer;

    private static final int DEFAULT_PORT = 110;

    public boolean isDebug()
    {
        return debug;
    }

    public void setDebug(boolean debug)
    {
        this.debug = debug;
    }

    public int connect(String host, int port) throws IOException
    {
        socket = new Socket();
        try
        {
            socket.connect(new InetSocketAddress(host, port));
        } catch (IOException ex)
        {
            return 0;
        }
        reader = new BufferedReader(new
                InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
        if (debug)
        {
            System.out.println("Connected to the host");
        }
        readResponseLine();
        return 1;
    }

    public int connect(String host) throws IOException
    {
        return connect(host, DEFAULT_PORT);
    }

    public boolean isConnected()
    {
        return socket != null && socket.isConnected();
    }

    public void disconnect() throws IOException
    {
        if (!isConnected())
            throw new IllegalStateException("Not connected to a host");
        socket.close();
        reader = null;
        writer = null;
        if (debug)
            System.out.println("Disconnected from the host");
    }

    protected String readResponseLine() throws IOException
    {
        String response;
        try
        {
            response = reader.readLine();
        } catch (Exception ex)
        {
            return "Server has returned an error";
        }

        if (debug)
        {
            System.out.println("DEBUG [in] : " + response);
        }
        if (response.startsWith("-ERR"))
        {
            //throw new RuntimeException("Server has returned an error: " + response.replaceFirst("-ERR ", ""));
            return "Server has returned an error: " + response.replaceFirst("-ERR ", "");
        }

        return response;
    }

    private String sendCommand(String command) throws IOException
    {
        if (debug)
        {
            System.out.println("DEBUG [out]: " + command);
        }
        writer.println(command);
        return readResponseLine();
    }

    private int login(String username, String password) throws IOException
    {
        if (sendCommand("USER " + username).startsWith("Server has returned an error")
                || sendCommand("PASS " + password).startsWith("Server has returned an error"))
            return 0;
        else
            return 1;
    }

    private void logout() throws IOException
    {
        sendCommand("QUIT");
    }

    private int getNumberOfNewMessages() throws IOException
    {
        String response = sendCommand("STAT");
        if (response.startsWith("Server has returned an error"))
            return -1;

        String[] values = response.split(" ");
        if (debug)
            System.out.println(response);
        return Integer.parseInt(values[1]);
    }

    public class Message
    {

        private final Map<String, List<String>> headers;

        private final String body;

        private Message(Map<String, List<String>> headers, String body)
        {
            this.headers = Collections.unmodifiableMap(headers);
            this.body = body;
        }

        public Map<String, List<String>> getHeaders() {
            return headers;
        }

        public String getBody() {
            return body;
        }

    }

    private Message getMessage(int i) throws IOException
    {
        String response = sendCommand("RETR " + i);
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        String headerName = null;
        while ((response = readResponseLine()).length() != 0)
        {
            if (response.startsWith("\t"))
            {
                continue; //no process of multiline headers
            }
            int colonPosition = response.indexOf(":");
            headerName = response.substring(0, colonPosition);
            String headerValue;
            if (headerName.length() > colonPosition)
            {
                headerValue = response.substring(colonPosition + 2);
            } else {
                headerValue = "";
            }
            List<String> headerValues = headers.get(headerName);
            if (headerValues == null)
            {
                headerValues = new ArrayList<String>();
                headers.put(headerName, headerValues);
            }
            headerValues.add(headerValue);
        }
        // process body
        StringBuilder bodyBuilder = new StringBuilder();
        while (!(response = readResponseLine()).equals("."))
        {
            bodyBuilder.append(response + "\n");
        }
        return new Message(headers, bodyBuilder.toString());
    }

    public List<Message> getMessages() throws IOException
    {
        int numOfMessages = getNumberOfNewMessages();
        List<Message> messageList = new ArrayList<Message>();
        for (int i = 1; i <= numOfMessages; i++) {
            messageList.add(getMessage(i));
        }
        return messageList;
    }

    private int getList() throws IOException
    {

        String response = sendCommand("LIST");
        if (response.startsWith("Server has returned an error"))
            return -1;
        if (debug)
            System.out.println(response);
        String[] values = response.split(" ");
        System.out.println(values[1] + " " + values[2]);
        while (!(response = readResponseLine()).equals("."))
            System.out.println(response);
        return 1;
    }

    //STAT command, returns -1 upon error
    public int cmdStat(POP3Client client)
    {
        int temp;
        try
        {
            temp = client.getNumberOfNewMessages();
        } catch (IOException ex)
        {
            return -1;
        }
        if (temp == -1)
        {
            return -1;
        } else
        {
            System.out.println("Number of new emails: " + temp);
            return 0;
        }
    }

    // LIST command, returns -1 if a wrong integer was entered, -2 if connection was lost
    public int cmdList(POP3Client client, BufferedReader lr)
    {
        int temp;
        System.out.println("Enter message number (0 for no arguments): ");
        try
        {
            temp = Integer.parseInt(lr.readLine());

        if (temp == 0)
        {
            if (client.getList() == -1)
            {
                return -2;
            }
        } else
        {
            System.out.println(client.sendCommand("LIST " + temp));
            return 0;
        }
        } catch (NumberFormatException ex)
        {
            return -1;
        } catch (IOException ex)
        {
            return -2;
        }
        return 0;
    }

    //RETR command, returns -1 if a message with this number does not exist, -2 if a wrong integer was entered, -3 if connection to server was lost
    public int cmdRetr(POP3Client client, BufferedReader lr)
    {
        int temp;
        System.out.println("Enter message number: ");
        try
        {
            temp = Integer.parseInt(lr.readLine());
            int x = client.getNumberOfNewMessages();
            if (temp > x || temp < 1)
            {
                return -1;
            }
            System.out.println(client.getMessage(temp).getBody());
        }
        catch (NumberFormatException ex)
        {
            return -2;
        } catch (IOException ex)
        {
            return -3;
        }
        return 0;
    }

    //DELE command, returns -1 if a message with this number does not exist, -2 if a wrong integer was entered, -3 if connection to server was lost
    public int cmdDele(POP3Client client, BufferedReader lr)
    {
        int temp, x;
        System.out.println("Enter message number: ");
        try
        {
            temp = Integer.parseInt(lr.readLine());
            x = client.getNumberOfNewMessages();
            if (temp > x || temp < 0)
            {
                return -1;
            }
            if (client.sendCommand("DELE " + temp).startsWith("Server has returned an error"))
            {
                return -3;
            }
        } catch (NumberFormatException ex)
        {
            return -2;
        } catch (IOException ex)
        {
            return -3;
        }
        return 0;
    }

    // Noop command, returns -1 if the server returned an error
    public int cmdNoop(POP3Client client)
    {
        try
        {
            if (client.sendCommand("NOOP").startsWith("Server has returned an error"))
            {
                return -1;
            }
        } catch (IOException ex)
        {
            return -1;
        }
        return 0;
    }

    // RSET command, return -1 if the server returned an error
    public int cmdRset(POP3Client client)
    {
        try
        {
            if (client.sendCommand("RSET").startsWith("Server has returned an error"))
            {
                return -1;
            }
        }catch (IOException ex)
        {
            return -1;
        }
        return 0;
    }

    public static void main(String[] args) throws IOException
    {
        POP3Client client = new POP3Client();
        client.setDebug(false); // <----------------- set this to true to see debugging info
        BufferedReader lr = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Enter host: ");
        if (client.connect(lr.readLine()) == 0)
        {
            System.out.println("Failed to connect to host, exiting");
        } else
        {
            System.out.println("Enter login and password: ");
            if (client.login(lr.readLine(), lr.readLine()) == 0)
            {
                System.out.println("Failed to log in, exiting");
            }
            else
            {
                int x;
                int temp = 0;
                do
                {
                    x = 0;
                    System.out.println("[1] for STAT\n" +
                            "[2] for LIST\n" +
                            "[3] for RETR\n" +
                            "[4] for DELE\n" +
                            "[5] for NOOP\n" +
                            "[6] for RSET\n" +
                            "[9] for QUIT\n");
                    try
                    {
                        x = Integer.parseInt(lr.readLine());
                        if (x > 10 || x < 0)
                            System.out.println("A wrong integer was entered, try again\n");
                    } catch (NumberFormatException ex)
                    {
                        System.out.println("A wrong integer was entered, try again\n");
                        x = -1;
                    }

                    switch(x)
                    {
                        case 1: // STAT
                            if (client.cmdStat(client) == -1)
                            {
                                System.out.println("The server has returned and error, exiting...");
                                x = 9;
                            }
                            break;

                        case 2: // LIST
                            temp = client.cmdList(client, lr);
                            if (temp == -1)
                            {
                                System.out.println("A wrong integer was entered\n");
                            } else if (temp == -2)
                            {
                                System.out.println("The server has returned and error, exiting...");
                                x = 9;
                            }
                            break;

                        case 3: // RETR [msg]
                            temp = client.cmdRetr(client, lr);
                            if (temp == -1)
                            {
                                System.out.println("A message with this number does not exist\n");
                            } else if (temp == -2)
                            {
                                System.out.println("A wrong integer was entered\n");
                            } else if (temp == -3)
                            {
                                System.out.println("The server has returned and error, exiting...");
                                x = 9;
                            }
                            break;

                        case 4: // DELE [msg]
                            temp = client.cmdDele(client, lr);
                            if (temp == -1)
                            {
                                System.out.println("A message with this number does not exist\n");
                            } else if (temp == -2)
                            {
                                System.out.println("A wrong integer was entered\n");
                            } else if (temp == -3)
                            {
                                System.out.println("The server has returned and error, exiting...");
                                x = 9;
                            }
                            break;

                        case 5: // NOOP
                            temp = client.cmdNoop(client);
                            if (temp == -1)
                            {
                                System.out.println("The server has returned and error, exiting...");
                                x = 9;
                            }
                            break;

                        case 6: // RSET
                            temp = client.cmdRset(client);
                            if (temp == -1)
                            {
                                System.out.println("The server has returned and error, exiting...");
                                x = 9;
                            }
                            break;
                    }

                } while (x != 9);
                client.logout();
                client.disconnect();
            }
        }
    }
}
