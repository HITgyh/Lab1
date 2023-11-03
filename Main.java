
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//改动
//改动2
//改动3
//改动4
public class Main {
    private static Set<String> forbidSet = new HashSet<>();//屏蔽的网站
    private static Set<String> forbidUser = new HashSet<>();//屏蔽的用户

    private static boolean isForbidden(String site) {
        return forbidSet.contains(site);
    }//判断是否屏蔽

    static {
        //根据需求设置屏蔽的网站和用户
        //forbidSet.add("http://today.hit.edu.cn/");
        //forbidUser.add("127.0.0.1");
    }


    private static Map<String, String> parse(String header)//解析header
    {
        if (header.length() == 0) {
            return new HashMap<>();
        }

        String[] lines = header.split("\\n");
        String method = null;String visitAddr = null;String httpVersion = null;String hostName = null;String portString = null;

        for (String line : lines) {
            if ((line.contains("GET") || line.contains("POST") || line.contains("CONNECT")) && method == null) //CONNECT是https
            {
                String[] ls1 = line.split("\\s");
                method = ls1[0];
                visitAddr = ls1[1];
                httpVersion = ls1[2];

                if (visitAddr.contains("http://") || visitAddr.contains("https://"))
                {
                    String[] ls2 = visitAddr.split(":");//判断是否有端口号
                    if (ls2.length >= 3) {
                        portString = ls2[2];
                    }
                } else {
                    String[] ls3 = visitAddr.split(":");//判断是否有端口号
                    if (ls3.length >= 2) {
                        portString = ls3[1];
                    }
                }
            } else if (line.contains("Host: ") && hostName == null) {
                String[] ls4 = line.split("\\s");//获取host
                hostName = ls4[1];
                int mindex = hostName.indexOf(':');
                if (mindex != -1) {
                    hostName = hostName.substring(0, mindex);
                }
            }
        }

        Map<String, String> map = new HashMap<>();//将解析的信息放入map中
        map.put("method", method);
        map.put("visitAddr", visitAddr);
        map.put("httpVersion", httpVersion);
        map.put("host", hostName);
        if (portString == null) {
            map.put("port", "80");//如果没有端口号，默认端口号为80
        } else {
            map.put("port", portString);
        }
        return map;
    }


    public static void main(String[] args) throws IOException {
        //创建服务器
        int port = 8080;
        ServerSocket server = new ServerSocket(port);
        System.out.println("waiting for connection...");

        //创建线程池
        ExecutorService threadPool = Executors.newFixedThreadPool(100);

        //循环监听
        while (true) {
            Socket socket = server.accept();
            boolean pass = true;
            if (forbidUser.contains(socket.getInetAddress().getHostAddress())) {
                pass = false;
            }
            boolean finalPass = pass;
            new Thread(() -> {
                try {
                    InputStreamReader r = new InputStreamReader(socket.getInputStream());
                    BufferedReader br = new BufferedReader(r);
                    String readLine = br.readLine();
                    String host;

                    StringBuilder header = new StringBuilder();

                    while (readLine != null && !readLine.equals("")) {
                        header.append(readLine).append("\n");
                        readLine = br.readLine();
                    }

                    if (!finalPass) {
                        System.out.println("From a forbidden user.");
                        PrintWriter pw = new PrintWriter(socket.getOutputStream());
                        pw.println("You are a forbidden user!");
                        pw.close();

                        socket.close();
                        return;
                    }

                    //jiexi header
                    Map<String, String> map = parse(header.toString());

                    host = map.get("host");

                    String portString = map.getOrDefault("port", "80");

                    int visitPort = Integer.parseInt(portString);

                    String visitAddr = map.get("visitAddr");

                    String method = map.getOrDefault("method", "GET");

                    //屏蔽网站
                    if (visitAddr != null && isForbidden(visitAddr)) {
                        PrintWriter pw = new PrintWriter(socket.getOutputStream());
                        pw.println("You can not visit " + visitAddr + "!");
                        pw.close();
                    } else {

                        File cacheFile = new File(visitAddr.replace('/', 'g') + ".mycache");
                        boolean useCache = false;   // 标记是否用cache

                        String lastModified = "Thu, 01 Jul 1970 20:00:00 GMT";

                        // 如果cache存在，且不为空，则获取lastModified
                        if (cacheFile.exists() && cacheFile.length() != 0)
                        {
                            Calendar cal = Calendar.getInstance();
                            long time = cacheFile.lastModified();
                            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
                            cal.setTimeInMillis(time);
                            cal.set(Calendar.HOUR, -7);
                            cal.setTimeZone(TimeZone.getTimeZone("GMT"));
                            lastModified = formatter.format(cal.getTime());

                        }

                        Socket connectRemoteSocket = new Socket(host, visitPort);  // 连接远程服务器

                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connectRemoteSocket.getOutputStream()));// 连接远程服务器的输出流

                        StringBuffer requestBuffer = new StringBuffer();// 构造请求报文

                        requestBuffer.append(method).append(" ").append(visitAddr)
                                .append(" HTTP/1.1").append("\r\n")
                                .append("HOST: ").append(host).append("\n")
                                .append("Accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\n")
                                .append("Accept-Encoding:gzip, deflate, sdch\n")
                                .append("Accept-Language:zh-CN,zh;q=0.8\n")
                                .append("If-Modified-Since: ").append(lastModified).append("\n")
                                .append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36 Edg/111.0.1661.62\n")
                                .append("Encoding:UTF-8\n")
                                .append("Connection:keep-alive" + "\n")
                                .append("\n");// 请求报文构造完毕

                        writer.write(requestBuffer.toString());// 将请求报文写入输出流
                        writer.flush();// 将请求报文发送出去

                        OutputStream outToBrowser = socket.getOutputStream();// 从socket中获取输出流
                        FileOutputStream fileOutputStream = new FileOutputStream(new File(visitAddr.replace('/', 'g') + ".mycache"));// 用于写入cache的输出流
                        BufferedInputStream remoteInputStream = new BufferedInputStream(connectRemoteSocket.getInputStream());// 从远程服务器获取输入流

                        byte[] tempBytes = new byte[20];// 用于缓存的字节数组
                        int len = remoteInputStream.read(tempBytes);// 读取远程服务器的输入流
                        String res = new String(tempBytes, 0, len);// 将读取到的字节转换为字符串
                        System.out.println(res);// 打印响应报文

                        if (res.contains("304")) {
                            useCache = true;    // 用缓存
                        } else {
                            outToBrowser.write(tempBytes);// 将响应报文写入输出流
                            fileOutputStream.write(tempBytes);// 将响应报文写入cache
                        }
                        if (useCache) {
                            FileInputStream fileInputStream = new FileInputStream(cacheFile);// 从cache中读取文件
                            int bufferLength = 1;// 缓存大小
                            byte[] buffer = new byte[bufferLength];// 缓存字节数组
                            int count;

                            // 读取文件并写入输出流
                            while (true) {
                                count = fileInputStream.read(buffer);
                                System.out.println("Reading>.... From file>..." + count);
                                if (count == -1) {
                                    break;
                                }
                                outToBrowser.write(buffer);
                            }
                            outToBrowser.flush();// 刷新输出流
                        }

                        int bufferLength = 1;

                        // 读取远程服务器的输入流并写入输出流
                        byte[] buffer = new byte[bufferLength];
                        int count;
                        while (true) {
                            count = remoteInputStream.read(buffer);
                            if (count == -1) {
                                break;
                            }
                            if (!useCache) {
                                outToBrowser.write(buffer);
                                fileOutputStream.write(buffer);
                            }
                        }
                        fileOutputStream.flush();// 刷新输出流
                        fileOutputStream.close();// 关闭输出流

                        outToBrowser.flush();// 刷新输出流
                        connectRemoteSocket.close();// 关闭远程socket

                    }
                    socket.close();//关闭socket
                } catch (IOException e) {

                }
            }).start();
        }
    }


}

