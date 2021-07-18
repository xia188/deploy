package com.xlongwei.deploy;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.extra.ssh.Sftp;

/**
 * scp file user@host:/dir
 */
public class Scp {
    @Parameter(names = { "-pw" }, description = "password or ENV SSHPASS")
    String passwd = System.getenv("SSHPASS");

    @Parameter(names = { "-i" }, description = "identity_file or ENV SSHIDENTITY")
    String identityFile = System.getenv("SSHIDENTITY");

    @Parameter(names = { "-P" }, description = "port or ENV SSHPORT")
    int port = Convert.toInt(System.getenv("SSHPORT"), 22);

    @Parameter(description = "source ... target")
    List<String> paths;

    @Parameter(names = { "--debug", "-d" }, description = "Debug mode")
    boolean debug = false;

    @Parameter(names = { "--help", "-h", "--info" }, description = "print Usage info")
    boolean help = false;

    static Pattern userHostPath = Pattern.compile("(.+)@(.+):(.+)");

    public static void main(String[] args) {
        Scp main = new Scp();
        JCommander jCommander = JCommander.newBuilder().addObject(main).build();
        jCommander.parse(args);
        main.run(jCommander);
    }

    public void run(JCommander jCommander) {
        if (help || StrUtil.isAllBlank(passwd, identityFile) || paths == null || paths.size() <= 1) {
            jCommander.usage();
        } else {
            String target = paths.get(paths.size() - 1);
            List<String> sources = paths.subList(0, paths.size() - 1);
            if (debug) {
                System.out.printf("scp %s %s\n", String.join(" ", sources), target);
            }
            boolean upload = target.indexOf('@') > 0;
            if (upload) {
                upload(sources, target);
            } else {
                for (String source : sources) {
                    download(source, target);
                }
            }
        }
    }

    private void download(String source, String target) {
        Matcher matcher = userHostPath.matcher(source);
        if (matcher.matches() == false) {
            System.out.println("source should be like user@host:/path");
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
        String paths = matcher.group(3);
        if (debug) {
            if (identity) {
                System.out.printf("-pw %s -i %s -P %s scp %s@%s:%s %s\n", passwd, identityFile, port, sshUser, sshHost,
                        source, target);
            } else {
                System.out.printf("-pw %s -P %s scp %s@%s:%s %s\n", passwd, port, sshUser, sshHost, source, target);
            }
        }
        try (Sftp sftp = identity
                ? JschUtil.createSftp(JschUtil.getSession(sshHost, port, sshUser, identityFile,
                        StrUtil.isBlank(passwd) ? null : passwd.getBytes()))
                : JschUtil.createSftp(sshHost, port, sshUser, passwd)) {
            for (String path : paths.split("[,;]")) {
                String dest = (new File(target, FileNameUtil.getName(path))).getAbsolutePath();
                sftp.get(path, dest);
                if (debug) {
                    System.out.printf("get %s to %s success\n", source, dest);
                }
            }
            System.out.printf("scp succeeded\n");
        } catch (Exception e) {
            System.out.printf("scp failed: %s\n", e.getMessage());
        }
    }

    public void upload(List<String> sources, String target) {
        Matcher matcher = userHostPath.matcher(target);
        if (matcher.matches() == false) {
            System.out.println("target should be like user@host:/dir");
            return;
        }
        for (String source : sources) {
            File file = new File(source);
            if (!file.exists() || !file.isFile()) {
                System.out.printf("source %s not exist or not a file\n", source);
                return;
            }
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
        String sshTarget = matcher.group(3);
        if (debug) {
            if (identity) {
                System.out.printf("-pw %s -i %s -P %s scp %s %s@%s:%s\n", passwd, identityFile, port,
                        String.join(" ", sources), sshUser, sshHost, sshTarget);
            } else {
                System.out.printf("-pw %s -P %s scp %s %s@%s:%s\n", passwd, port, String.join(" ", sources), sshUser,
                        sshHost, sshTarget);
            }
        }
        try (Sftp sftp = identity
                ? JschUtil.createSftp(JschUtil.getSession(sshHost, port, sshUser, identityFile,
                        StrUtil.isBlank(passwd) ? null : passwd.getBytes()))
                : JschUtil.createSftp(sshHost, port, sshUser, passwd)) {
            for (String source : sources) {
                sftp.put(source, sshTarget);
                if (debug) {
                    System.out.printf("put %s to %s success\n", source, sshTarget);
                }
            }
            System.out.printf("scp succeeded\n");
        } catch (Exception e) {
            System.out.printf("scp failed: %s\n", e.getMessage());
        }
    }
}
