package com.xlongwei.deploy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.net.multipart.UploadFile;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.Method;
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

    @Parameter(names = { "--debug", "-d" }, description = "Debug mode")
    static boolean debug = false;

    @Parameter(names = { "--web", "-w", "--ui" }, description = "start web ui")
    boolean web = false;

    @Parameter(names = { "--port", "-p" }, description = "web http port")
    int port = 9881;

    @Parameter(names = { "--lp.host" }, description = "long polling host, ex: http://localhost:9881")
    static String host = "";

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
                if (scheduledExecutorService != null) {
                    scheduledExecutorService.shutdown();
                }
                CronUtil.stop();
                System.out.println("cron stop");
            };
            if (web) {
                web();
            }
            boolean longpolling = StrUtil.isNotBlank(host) && host.startsWith("http") && StrUtil.isNotBlank(key);
            if (CronUtil.getScheduler().getTaskTable().isEmpty() && web == false && longpolling == false) {
                System.out.printf("cron is empty, please execute the following two commands.\n");
                System.out.printf("jar xvf deploy.jar config\n");
                System.out.printf("cp config/* ./ && rm -rf config/\n");
                stop.run();
            } else {
                RuntimeUtil.addShutdownHook(stop);
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
            try {
                // 客户端超时时间要大于长轮询约定的超时时间
                if (debug) {
                    System.out.printf("long pooling start\n");
                }
                HttpResponse execute = HttpRequest.get(url).timeout(31000).execute();
                int status = execute.getStatus();
                if (debug) {
                    System.out.printf("long pooling status = %s\n", status);
                }
                if (HttpStatus.HTTP_NOT_MODIFIED == status) {
                    // will try again
                } else if (HttpStatus.HTTP_OK == status) {
                    String body = execute.body();
                    if (debug) {
                        System.out.printf("long pooling get json = %s\n", body);
                    }
                    JSONObject json = JSONUtil.parseObj(body);
                    String deploy = json.getStr("deploy");
                    String deploys = json.getStr("deploys");
                    boolean test = json.getBool("test", Boolean.TRUE);
                    String tag = json.getStr("tag");
                    String update = json.getStr("update");
                    if (StrUtil.isNotBlank(update)) {
                        FileUtil.writeBytes(Base64.decode(update), new File("update.tgz"));
                    }
                    String result = ShellAction.deploy(deploy, deploys, test);
                    execute = HttpRequest.post(host + "/deploy?key=" + tag).form("result", result).execute();
                    if (debug) {
                        System.out.printf("long pooling result = %s\n", execute.getStatus());
                    }
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                ThreadUtil.safeSleep(3000);
            }
        }, 1, 1, TimeUnit.MILLISECONDS);
    }

    private void web() {
        SimpleServer server = new SimpleServer(port);
        server.addAction("/", new HtmlAction());
        server.addAction("/deploy", new ShellAction());
        server.addAction("/codegen", new CliAction());
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

        @Override
        public void doAction(HttpServerRequest request, HttpServerResponse response) throws IOException {
            String path = StrUtil.trimToEmpty(request.getPath());
            if(path.startsWith("/files")) {
            	File file = new File(path.substring(1));
            	if(file.exists()) {
            		if(file.isFile()) {
            			response.write(file);
            		}else {
            			response.write(dir(file));
            		}
            		return;
            	}
            }else {
            	String resource = "webapp" + (path.length() <= 1 ? "/index.html" : path);
            	try (InputStream in = ResourceUtil.getResourceObj(resource).getStream()) {
                    response.write(in);
                    return;
                } catch (Exception e) {
                	System.out.println(e.getMessage());
                }
            }
        	response.send404("404 Not Found !");
        }

		private String dir(File dir) {
			StringBuilder html = new StringBuilder("<!DOCTYPE html><body><h1>/files/</h1><hr><pre>");
			String sep = StrUtil.repeat(' ', 60);
			for (File file : dir.listFiles()) {
				if(file.isDirectory()) continue;
				html.append("\n<a href=\"/files/").append(file.getName()).append("\">").append(file.getName()).append("</a>");
				html.append(sep).delete(html.length() - file.getName().length(),html.length()).append(DateUtil.date(file.lastModified()));
				html.append(sep).append(file.length());
			}
			html.append("</pre><hr></body></html>");
			return html.toString();
		}
    }

    public static class CliAction implements Action {

		@Override
		public synchronized void doAction(HttpServerRequest request, HttpServerResponse response) throws IOException {
			String framework = StrUtil.trimToEmpty(request.getParam("framework"));
			String config = StrUtil.trimToEmpty(request.getParam("config"));
			String model = StrUtil.trimToEmpty(request.getParam("model"));
			String release = StrUtil.trimToEmpty(request.getParam("release"));
			String message = "framework不能为空，config和configFile不能同时为空";
			
			String cliJar = release.startsWith("2") ? "files/codegen-cli-2.1.32.jar" : "files/codegen-cli-1.6.47.jar";
			String output = IdUtil.fastSimpleUUID();
			String zip = "files/" + output + ".zip";
			File configFile = new File(output + ".config.json"), modelFile = new File(output + ".yaml");
			try {
				//处理文件上传：获取config文本，对model进行base64转码
				if (request.isMultipart()) {
					UploadFile upload = request.getMultipart().getFile("configFile");
					if (upload != null) {
						config = IoUtil.readUtf8(upload.getFileInputStream());
						if (!(config.startsWith("{") || config.startsWith("["))) {
							config = Base64.encode(config);
						}
					}
					upload = request.getMultipart().getFile("modelFile");
					if (upload != null) {
						model = IoUtil.readUtf8(upload.getFileInputStream());
						if (!(model.startsWith("{") || model.startsWith("["))) {
							model = Base64.encode(model);
						}
					}
				}
				//处理非网址的情形
				if (StrUtil.isNotBlank(config) && !config.startsWith("http")) {
					if (!(config.startsWith("{") || config.startsWith("["))) {
						config = Base64.decodeStr(config);
						if (!(config.startsWith("{") || config.startsWith("["))) {
							configFile = new File(output + ".config.yaml");
						}
					}
					FileUtil.writeUtf8String(config, configFile);
					config = configFile.getName();
				}
				if (StrUtil.isNotBlank(model) && !model.startsWith("http")) {
					if (model.startsWith("{") || model.startsWith("[")) {
						// json格式可以不用base64转码
						modelFile = new File(output + ".json");
					} else {
						model = Base64.decodeStr(model);
						if (model.startsWith("{") || model.startsWith("[")) {
							modelFile = new File(output + ".json");
						} else if (model.startsWith("schema")) {
							modelFile = new File(output + ".grqphqls");
						} else {
							modelFile = new File(output + ".yaml");
						}
					}
					FileUtil.writeUtf8String(model, modelFile);
					model = modelFile.getName();
				}
				//生成项目，打包，删除目录
				if (StrUtil.isAllNotBlank(framework, config)) {
					String shell = String.format("java -jar %s -f %s -c %s -m %s -o %s", cliJar, framework, config, model, output);
					new ShellTask(shell, timeout).execute();
					new ShellTask("jar cf " + zip + " " + output, timeout).execute();
					File outputFile = new File(output), zipFile = new File(zip);
					FileUtil.del(outputFile);
					System.out.println(output + "=" + outputFile.exists() + " " + zip + "=" + zipFile.exists());
					message = zipFile.exists() ? zip : "生成失败";
				} else if (modelFile.exists()) {
					modelFile.renameTo(new File("files/" + modelFile.getName()));
					message = "?spec=/files/" + modelFile.getName();
				}
			} catch (Exception e) {
				message = e.getMessage();
			}
			FileUtil.del(configFile);
			FileUtil.del(modelFile);
			response.write(new JSONObject().set("message", message).toString());
		}
    	
    }
    
    public static class ShellAction implements Action {
        private Cache<String, LongPollingObject<String>> map = CacheUtil.newLFUCache(100,
                TimeUnit.SECONDS.toMillis(30));
        private String prefix = "uuid.";

        @Override
        public void doAction(HttpServerRequest request, HttpServerResponse response) throws IOException {
            String key = request.getParam("key");
            String result = request.getParam("result");
            String deploy = request.getParam("deploy");
            String deploys = request.getParam("deploys");
            boolean test = "true".equals(request.getParam("test"));
            UploadFile update = !Method.POST.name().equals(request.getMethod()) || StrUtil.isNotBlank(result) ? null
                    : request.getMultipart().getFile("update");
            boolean hasUpdateTgz = update != null && update.getFileName().endsWith(".tgz");
            if (hasUpdateTgz) {
                if (StrUtil.isNotBlank(deploy)) {
                    deploy += " update.tgz";
                } else if (StrUtil.isNotBlank(deploys)) {
                    deploys += " update.tgz";
                }
            }
            if (StrUtil.isNotBlank(key)) {
                LongPollingObject<String> queue = map.get(key);
                if (queue == null) {
                    synchronized (map) {
                        queue = map.get(key);
                        if (queue == null) {
                            queue = new LongPollingObject<>();
                            map.put(key, queue);
                        }
                    }
                }
                String json = null;
                if (request.isGetMethod()) {
                    try {
                        json = queue.get(29, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        System.out.println(e.getMessage());
                    }
                    if (json == null) {
                        response.send(HttpStatus.HTTP_NOT_MODIFIED);
                    } else {
                        if (key.startsWith(prefix)) {
                            map.remove(key);
                        }
                        response.write(json);
                    }
                } else {
                    if (StrUtil.isNotBlank(result)) {
                        json = new JSONObject().set("result", result).toString();
                        queue.put(json);
                        response.write("ok");
                    } else {
                        String tag = prefix + IdUtil.fastSimpleUUID();
                        if (hasUpdateTgz) {
                            json = new JSONObject().set("deploy", deploy).set("deploys", deploys).set("test", test)
                                    .set("tag", tag).set("update", Base64.encode(update.getFileInputStream()))
                                    .toString();
                        } else {
                            json = new JSONObject().set("deploy", deploy).set("deploys", deploys).set("test", test)
                                    .set("tag", tag).toString();
                        }
                        try {
                            queue.put(json, 29, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            System.out.println(e.getMessage());
                        }
                        response.write(tag);
                    }
                }
            } else {
                if (StrUtil.isNotBlank(host)) {
                    test = true;// 使用长轮询绕路时，可用lp.key自我保护
                }
                if (hasUpdateTgz) {
                    FileUtil.writeFromStream(update.getFileInputStream(), new File("update.tgz"), true);
                }
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
            new File(".", "update.tgz").delete();
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

    public static class LongPollingObject<T> {
        private long timestamp;
        private Lock lock = new ReentrantLock();
        private Condition notEmpty = lock.newCondition();
        private Condition notFull = lock.newCondition();
        private T object;

        public void put(T object) {
            this.timestamp = System.currentTimeMillis();
            this.object = object;
            lock.lock();
            try {
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        public void put(T object, long timeout, TimeUnit unit) throws InterruptedException {
            long nanos = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (this.object != null) {
                    if (nanos <= 0)
                        return;
                    nanos = notFull.awaitNanos(nanos);
                }
                this.timestamp = System.currentTimeMillis();
                this.object = object;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        public T get(long timeout, TimeUnit unit) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                if (object != null && timestamp + unit.toMillis(timeout) > System.currentTimeMillis()) {
                    return object;
                }
                long nanos = unit.toNanos(timeout);
                while (object == null) {
                    if (nanos <= 0)
                        return null;
                    nanos = notEmpty.awaitNanos(nanos);
                }
                notFull.signal();
                return object;
            } finally {
                lock.unlock();
                object = null;
            }
        }

    }
}
