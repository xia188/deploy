package com.xlongwei.deploy;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.log.StaticLog;

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
                StaticLog.info("Cron stop");
            });
        }
    }

    public static class ShellTask implements Task {
        private String shell;

        public ShellTask(String shell) {
            this.shell = shell;
        }

        @Override
        public void execute() {
            try {
                String str = RuntimeUtil.execForStr(shell);
                StaticLog.info("\n{}", str);
            } catch (Exception e) {
                StaticLog.info(e.getMessage());
            }
        }

    }
}
