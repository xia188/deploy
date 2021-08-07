package com.xlongwei.deploy;

import java.io.File;
import java.util.List;

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
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;

/**
 * jcron --cron "* * * * * *" --shell "pwd"
 */
public class Cron {

    @Parameter(names = { "--shell", "-s" }, description = "shell or ENV SSHSHELL")
    String shell = StrUtil.blankToDefault(System.getenv("SSHSHELL"), "sh auto.sh");

    @Parameter(names = { "--cron", "-c" }, description = "cron or ENV SSHCRON")
    String cron = StrUtil.blankToDefault(System.getenv("SSHCRON"), "3 */5 * * * *");

    @Parameter(names = { "--timeout", "-t" }, description = "timeout")
    long timeout = 120000;

    @Parameter(names = { "--help", "-h", "--info" }, description = "print Usage info")
    boolean help = false;

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
            if (CronUtil.getScheduler().getTaskTable().isEmpty()) {
                System.out.printf("cron is empty\n");
                stop.run();
            } else {
                RuntimeUtil.addShutdownHook(stop);
            }
        }
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
                        System.out.println(new String(line.getBytes(),
                                OS.isFamilyWindows() ? CharsetUtil.CHARSET_GBK : CharsetUtil.CHARSET_UTF_8));
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
}
