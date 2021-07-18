package com.xlongwei.deploy;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.jcraft.jsch.Session;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;

/**
 * ssh user@host command
 */
public class Ssh {
    @Parameter(names = { "-pw" }, description = "password or ENV SSHPASS")
    String passwd = System.getenv("SSHPASS");

    @Parameter(names = { "-i" }, description = "identity_file or ENV SSHIDENTITY")
    String identityFile = System.getenv("SSHIDENTITY");

    @Parameter(names = { "-P" }, description = "port or ENV SSHPORT")
    int port = Convert.toInt(System.getenv("SSHPORT"), 22);

    @Parameter(names = { "--debug", "-d" }, description = "Debug mode")
    boolean debug = false;

    @Parameter(names = { "--help", "-h", "--info" }, description = "print Usage info")
    boolean help = false;

    @Parameter(description = "destination command")
    List<String> args;

    static Pattern userHostPath = Pattern.compile("(.+)@(.+)");

    public static void main(String[] args) {
        Ssh main = new Ssh();
        JCommander jCommander = JCommander.newBuilder().addObject(main).build();
        jCommander.parse(args);
        main.run(jCommander);
    }

    public void run(JCommander jCommander) {
        if (help || StrUtil.isAllBlank(passwd, identityFile) || args == null || args.size() != 2) {
            jCommander.usage();
        } else {
            String destination = args.get(0);
            String command = args.get(1);
            Matcher matcher = userHostPath.matcher(destination);
            if (matcher.matches() == false) {
                System.out.println("destination should be like user@host");
                return;
            }
            boolean identity = false;
            if (identityFile != null && !identityFile.isEmpty()) {
                File file = new File(identityFile);
                if (!file.exists() || !file.isFile()) {
                    System.out.printf("identity_file %s not exists or not a file\n", identityFile);
                    return;
                }
                identity = true;
            }
            String sshUser = matcher.group(1);
            String sshHost = matcher.group(2);
            if (debug) {
                if (identity) {
                    System.out.printf("-pw %s -i %s -p %s ssh %s@%s %s\n", passwd, identityFile, port, sshUser, sshHost,
                            "\"" + command + "\"");
                } else {
                    System.out.printf("-pw %s -p %s ssh %s@%s %s\n", passwd, port, sshUser, sshHost,
                            "\"" + command + "\"");
                }
            }
            Session session = identity
                    ? JschUtil.createSession(sshHost, port, sshUser, identityFile,
                            StrUtil.isBlank(passwd) ? null : passwd.getBytes())
                    : JschUtil.createSession(sshHost, port, sshUser, passwd);
            try {
                String exec = JschUtil.exec(session, command, CharsetUtil.CHARSET_UTF_8);
                System.out.println(exec);
                System.out.printf("ssh succeeded\n");
            } catch (Exception e) {
                System.out.printf("ssh failed: %s\n", e.getMessage());
            } finally {
                JschUtil.close(session);
            }
        }
    }
}
