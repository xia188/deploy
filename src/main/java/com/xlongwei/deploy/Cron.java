package com.xlongwei.deploy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteStreamHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.OS;
import org.apache.commons.exec.PumpStreamHandler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.server.HttpServerRequest;
import cn.hutool.http.server.HttpServerResponse;
import cn.hutool.http.server.SimpleServer;
import cn.hutool.http.server.action.Action;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * jcron --cron "* * * * * *" --shell "pwd"
 */
public class Cron {

    @Parameter(names = { "--shell", "-s" }, description = "shell or ENV SSHSHELL")
    String shell = StrUtil.blankToDefault(System.getenv("SSHSHELL"), "sh auto.sh");

    @Parameter(names = { "--cron", "-c" }, description = "cron or ENV SSHCRON")
    String cron = StrUtil.blankToDefault(System.getenv("SSHCRON"), "3 */5 * * * *");

    @Parameter(names = { "--timeout", "-t" }, description = "timeout")
    static long timeout = 120000;

    @Parameter(names = { "--help", "-h", "--info" }, description = "print Usage info")
    boolean help = false;

    @Parameter(names = { "--web", "-w", "--ui" }, description = "start web ui")
    boolean web = false;

    @Parameter(names = { "--port", "-p" }, description = "http port")
    int port = 9881;

    @Parameter(names = { "--lp.host" }, description = "long polling host, 'none' to disable")
    String host = "http://localhost:" + port;

    @Parameter(names = { "--lp.key" }, description = "long polling key")
    String key = "deploy";

    static ScheduledExecutorService scheduledExecutorService;

    public static void main(String[] args) {
        Cron main = new Cron();
        JCommander jCommander = JCommander.newBuilder().addObject(main).build();
        jCommander.parse(args);
        main.run(jCommander);
    }

    public void run(JCommander jCommander) {
        if (help || StrUtil.isAllBlank(shell)) {
            jCommander.usage();
        } else {
            System.out.printf("%s %s\n", cron, shell);
            if (!shell.contains("auto.sh") || new File("auto.sh").exists()) {
                CronUtil.schedule(cron, new ShellTask(shell, timeout));
            } else {
                System.out.printf("auto.sh not exist\n");
            }
            crontab();
            CronUtil.setMatchSecond(true);
            CronUtil.start();
            Runnable stop = () -> {
                CronUtil.stop();
                System.out.println("cron stop");
            };
            if (web) {
                web();
            }
            if (CronUtil.getScheduler().getTaskTable().isEmpty() && web == false) {
                System.out.printf("cron is empty, please execute the following two commands.\n");
                System.out.printf("jar xvf deploy.jar config\n");
                System.out.printf("cp config/* ./ && rm -rf config/\n");
                stop.run();
            } else {
                RuntimeUtil.addShutdownHook(stop);
                boolean longpolling = StrUtil.isNotBlank(host) && host.startsWith("http") && StrUtil.isNotBlank(key);
                if (longpolling || web) {
                    scheduledExecutorService = ThreadUtil.createScheduledExecutor(2);
                }
                if (longpolling) {
                    longpooling();
                }
            }
        }
    }

    // https://mp.weixin.qq.com/s/YjvL0sUTGHxR3GJFqrP8qg
    private void longpooling() {
        System.out.println("enable long polling...");
        String url = host + "/deploy?key=" + key;
        scheduledExecutorService.scheduleWithFixedDelay(() -> {
            // 客户端超时时间要大于长轮询约定的超时时间
            HttpResponse execute = HttpRequest.get(url).timeout(40000).execute();
            int status = execute.getStatus();
            // System.out.printf("long pooling status = %s\n", status);
            if (HttpStatus.HTTP_NOT_MODIFIED == status) {
                // will try again
            } else if (HttpStatus.HTTP_OK == status) {
                String body = execute.body();
                System.out.printf("long pooling body = %s\n", body);
                JSONObject json = JSONUtil.parseObj(body);
                String deploy = json.getStr("deploy");
                String deploys = json.getStr("deploys");
                boolean test = json.getBool("test", Boolean.TRUE);
                String result = ShellAction.deploy(deploy, deploys, test);
                HttpRequest.post(url + ".result").form("result", result).execute();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void web() {
        SimpleServer server = new SimpleServer(port);
        server.addAction("/", new HtmlAction("webapp/index.html"));
        server.addAction("/deploy", new ShellAction());
        server.start();
        System.out.printf("web started at http://localhost:%s/\n", port);
    }

    private void crontab() {
        File crontab = new File("crontab");
        if (crontab.exists()) {
            List<String> lines = FileUtil.readUtf8Lines(crontab);
            lines.forEach(line -> {
                if (!line.startsWith("#")) {
                    int spacePos = 0, spaceSplit = 6, spaceFound = 0;
                    for (int i = 0, len = line.length(); i < len; i++) {
                        if (Character.isWhitespace(line.charAt(i))) {
                            spaceFound++;
                            if (spaceFound >= spaceSplit) {
                                spacePos = i;
                                break;
                            }
                        }
                    }
                    if (spaceFound >= spaceSplit) {
                        String cron = line.substring(0, spacePos);
                        String shell = line.substring(spacePos + 1);
                        System.out.printf("%s %s\n", cron, shell);
                        CronUtil.schedule(cron, new ShellTask(shell, timeout));
                    } else {
                        System.out.printf("bad crontab %s\n", line);
                    }
                }
            });
        } else {
            String cron = "6 6 6 * * *";
            String shell = "sh sonar.sh";
            System.out.printf("%s %s\n", cron, shell);
            if (new File("sonar.sh").exists()) {
                CronUtil.schedule(cron, new ShellTask(shell, timeout));
            } else {
                System.out.printf("sonar.sh not exist\n");
            }
        }
    }

    public static class ShellTask implements Task {
        private String shell;
        private long timeout;
        private CommandLine command;
        public List<String> outputs = null;

        public ShellTask(String shell, long timeout) {
            this.shell = shell;
            this.timeout = timeout;
            this.command = CommandLine.parse(shell);
        }

        @Override
        public void execute() {
            try {
                System.out.println(shell);
                Executor exe = new DefaultExecutor();

                ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
                exe.setWatchdog(watchdog);

                LogOutputStream outAndErr = new LogOutputStream() {
                    @Override
                    protected void processLine(String line, int logLevel) {
                        String output = new String(line.getBytes(),
                                OS.isFamilyWindows() ? CharsetUtil.CHARSET_GBK : CharsetUtil.CHARSET_UTF_8);
                        System.out.println(output);
                        if (outputs != null) {
                            outputs.add(output);
                        }
                    }
                };
                ExecuteStreamHandler streamHandler = new PumpStreamHandler(outAndErr);
                exe.setStreamHandler(streamHandler);

                long s = System.currentTimeMillis();
                int exitvalue = exe.execute(command);
                streamHandler.stop();
                outAndErr.close();
                if (exe.isFailure(exitvalue) && watchdog.killedProcess()) {
                    System.out.println("timeout and killed by watchdog");
                } else {
                    System.out.printf("exec succeeded millis= %s ms\n", (System.currentTimeMillis() - s));
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

    }

    public static class HtmlAction implements Action {
        private String resource;

        public HtmlAction(String resource) {
            this.resource = resource;
        }

        @Override
        public void doAction(HttpServerRequest request, HttpServerResponse response) throws IOException {
            response.setContentType("text/html");
            response.write(utf8String(resource));
        }

        public static String utf8String(String resource) {
            try (InputStream in = ResourceUtil.getResourceObj(resource).getStream()) {
                return IoUtil.readUtf8(in);
            } catch (Exception e) {
                return e.getMessage();
            }
        }
    }

    public static class ShellAction implements Action {
        private Map<String, SynchronousQueue<String>> map = new HashMap<>();

        @Override
        public void doAction(HttpServerRequest request, HttpServerResponse response) throws IOException {
            String key = request.getParam("key");
            String result = request.getParam("result");
            String deploy = request.getParam("deploy");
            String deploys = request.getParam("deploys");
            boolean test = "true".equals(request.getParam("test"));
            if (StrUtil.isNotBlank(key)) {
                SynchronousQueue<String> queue = null;
                synchronized (map) {
                    queue = map.get(key);
                    if (queue == null) {
                        queue = new SynchronousQueue<>();
                        map.put(key, queue);
                    }
                }
                String json = null;
                if (request.isGetMethod()) {
                    try {
                        json = queue.poll(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (json == null) {
                        response.send(HttpStatus.HTTP_NOT_MODIFIED);
                    } else {
                        response.write(json);
                    }
                } else {
                    if (StrUtil.isNotBlank(result)) {
                        json = new JSONObject().set("result", result).toString();
                    } else {
                        json = new JSONObject().set("deploy", deploy).set("deploys", deploys).set("test", test)
                                .toString();
                    }
                    queue.offer(json);
                    scheduledExecutorService.schedule(() -> {
                        map.get(key).poll();
                    }, 30000, TimeUnit.SECONDS);
                    response.write(json);
                }
            } else {
                result = deploy(deploy, deploys, test);
                response.write(result);
            }
        }

        public static String deploy(String deploy, String deploys, boolean test) {
            String shell = null;
            if (StrUtil.isNotBlank(deploy)) {
                shell = test ? "cat deploy.sh" : "sh deploy.sh " + deploy;
            } else if (StrUtil.isNotBlank(deploys)) {
                if (deploys.contains(" ")) {
                    String[] split = deploys.split("[ ]");
                    List<String> namespaceIps = namespaceIps(split[1]);
                    if (namespaceIps.size() > 0) {
                        File file = new File(".", "deploys.sh");
                        if (file.exists()) {
                            List<String> lines = FileUtil.readUtf8Lines(file);
                            deploysTmp(lines, namespaceIps);
                        } else {
                            test = true;
                        }
                        shell = test ? "cat deploys_tmp.sh" : ("sh deploys_tmp.sh " + split[0]);
                    }
                } else {
                    shell = test ? "cat deploys.sh" : ("sh deploys.sh " + deploys);
                }
            }
            if (StrUtil.isBlank(shell) || shell.contains(";")) {
                shell = test ? "cat deploy.sh" : "sh deploy.sh";
            }
            ShellTask task = new ShellTask(shell, timeout);
            task.outputs = new LinkedList<>();
            task.execute();
            String result = String.join(StrUtil.CRLF, task.outputs);
            new File(".", "deploys_tmp.sh").delete();
            return result;
        }

        public static void deploysTmp(List<String> lines, List<String> namespaceIps) {
            List<String> tmps = new ArrayList<>();
            lines.forEach(line -> {
                if (StrUtil.isNotBlank(line)) {
                    if (line.startsWith("for ")) {
                        tmps.add("namespaceIps=(");
                        namespaceIps.forEach(namespaceIp -> {
                            tmps.add(namespaceIp);
                        });
                        tmps.add(")");
                    }
                }
                tmps.add(line);
            });
            FileUtil.writeUtf8Lines(tmps, new File(".", "deploys_tmp.sh"));
        }

        public static List<String> namespaceIps(String str) {
            List<String> namespaceIps = new ArrayList<>();
            if (StrUtil.isNotBlank(str)) {
                String[] parts = str.split("[;]");
                for (String part : parts) {
                    String[] pair = part.split("[=]");
                    if (pair == null || pair.length != 2) {
                        continue;
                    }
                    String namespace = pair[0];
                    int dash = pair[1].indexOf('-'), comma = pair[1].indexOf(',');
                    if (dash == -1 && comma == -1) {
                        namespaceIps.add(namespace + "=" + pair[1]);
                    } else if (dash > 0) {
                        int dot = pair[1].lastIndexOf('.', dash);
                        String prefix = dot == -1 ? "" : pair[1].substring(0, dot + 1);
                        int start = Integer.parseInt(pair[1].substring(prefix.length(), dash)),
                                end = Integer.parseInt(pair[1].substring(dash + 1));
                        for (int i = start; i <= end; i++) {
                            String ip = prefix + i;
                            namespaceIps.add(namespace + "=" + ip);
                        }
                    } else {
                        int dot = pair[1].lastIndexOf('.', dash);
                        String prefix = dot == -1 ? "" : pair[1].substring(0, dot + 1);
                        String[] ips = pair[1].split("[,]");
                        for (String ip : ips) {
                            ip = prefix + ip;
                            namespaceIps.add(namespace + "=" + ip);
                        }
                    }
                }
            }
            return namespaceIps;
        }
    }
}
