package com.xlongwei.deploy;

import java.io.ByteArrayOutputStream;
import java.io.File;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteStreamHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.OS;
import org.apache.commons.exec.PumpStreamHandler;

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

    public static void main(String[] args) {
        Cron main = new Cron();
        JCommander jCommander = JCommander.newBuilder().addObject(main).build();
        jCommander.parse(args);
        main.run(jCommander);
    }

    public void run(JCommander jCommander) {
        if (StrUtil.isAllBlank(shell)) {
            jCommander.usage();
        } else {
            CronUtil.schedule(cron, new ShellTask(shell));
            CronUtil.setMatchSecond(true);
            CronUtil.start();
            RuntimeUtil.addShutdownHook(() -> {
                CronUtil.stop();
                System.out.println("Cron stop");
            });
        }
    }

    public static class ShellTask implements Task {
        private CommandLine command;
        private volatile boolean running = false;

        public ShellTask(String shell) {
            this.command = CommandLine.parse(shell);
        }

        @Override
        public void execute() {
            System.out.printf("cron running=%s", running);
            if (running) {
                return;
            }
            try {
                running = true;
                Executor exe = new DefaultExecutor();
                exe.setWorkingDirectory(new File("."));
                exe.setExitValues(new int[] { 0, 1, 2 });

                ExecuteWatchdog watchdog = new ExecuteWatchdog(120000);
                exe.setWatchdog(watchdog);

                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ExecuteStreamHandler streamHandler = new PumpStreamHandler(baos);
                exe.setStreamHandler(streamHandler);

                int exitvalue = exe.execute(command);
                if (exe.isFailure(exitvalue) && watchdog.killedProcess()) {
                    System.out.println("timeout and killed by watchdog");
                }
                String str = baos.toString(OS.isFamilyWindows() ? CharsetUtil.GBK : CharsetUtil.UTF_8);
                System.out.printf("\n%s", str);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            running = false;
        }

    }
}
