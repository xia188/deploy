package com.xlongwei.deploy;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.SftpProgressMonitor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.lang.Filter;
import cn.hutool.core.swing.clipboard.ClipboardUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.extra.ssh.Sftp;
import cn.hutool.extra.ssh.Sftp.Mode;

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

    @Parameter(names = { "--sync" }, description = "Sync mode: auto be true if --sync.test|time|ext is present")
    boolean sync = false;

    @Parameter(names = { "--sync.test" }, description = "Sync test mode: no sync files")
    boolean syncTest = false;

    @Parameter(names = { "--sync.time" }, description = "Sync time mode: default is size")
    boolean syncTime = false;

    @Parameter(names = { "--sync.ext" }, description = "Sync ext: comma(,) means all")
    List<String> syncExt = syncExtDefault;

    @Parameter(names = { "--help", "-h", "--info" }, description = "print Usage info")
    boolean help = false;

    @Parameter(names = { "--copy", "-c" }, description = "copy ENV SSHPASS[_{pw}]")
    boolean copy = false;

    static Pattern userHostPath = Pattern.compile("(.+)@(.+):(.+)");

    static List<String> syncExtDefault = Arrays.asList("jar");

    public static void main(String[] args) {
        Scp main = new Scp();
        JCommander jCommander = JCommander.newBuilder().addObject(main).build();
        jCommander.parse(args);
        main.run(jCommander);
    }

    public void run(JCommander jCommander) {
        if (StrUtil.isNotBlank(passwd)) {
            String sshpass = System.getenv("SSHPASS_" + passwd);
            if (StrUtil.isNotBlank(sshpass)) {
                passwd = sshpass;
            }
        }
        if (copy && StrUtil.isNotBlank(passwd)) {
            ClipboardUtil.setStr(passwd);
            System.out.println("passwd copied");
        } else if (help || StrUtil.isAllBlank(passwd, identityFile) || paths == null || paths.size() <= 1) {
            jCommander.usage();
        } else {
            String target = paths.get(paths.size() - 1);
            List<String> sources = paths.subList(0, paths.size() - 1);
            if (sync == false) {
                sync = syncTest || syncTime || syncExt != syncExtDefault;
            }
            if (debug) {
                if (sync) {
                    System.out.printf("scp --sync %s %s\n", String.join(" ", sources), target);
                } else {
                    System.out.printf("scp %s %s\n", String.join(" ", sources), target);
                }
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
                System.out.printf("identity_file %s not exist or not a file\n", identityFile);
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
                if (isDirectory(sftp, path) && sync) {
                    downloadSync(sftp, path, target);
                } else {
                    download(sftp, path, target);
                }
            }
            System.out.printf("scp succeeded\n");
        } catch (Exception e) {
            System.out.printf("scp failed: %s\n", e.getMessage());
        }
    }

    private boolean isDirectory(Sftp sftp, String path) {
        List<LsEntry> lsEntries = sftp.lsEntries(path);
        boolean isFile = lsEntries != null && lsEntries.size() == 1 && path.endsWith(lsEntries.get(0).getFilename());
        return isFile == false;
    }

    private Filter<ChannelSftp.LsEntry> entryFilter = new Filter<ChannelSftp.LsEntry>() {
        @Override
        public boolean accept(LsEntry entry) {
            return entry.getAttrs().isDir() || syncExt.isEmpty()
                    || syncExt.contains(FileNameUtil.getSuffix(entry.getFilename()).toLowerCase());
        }
    };

    private FileFilter fileFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.isDirectory() || syncExt.isEmpty()
                    || syncExt.contains(FileNameUtil.getSuffix(file.getName()).toLowerCase());
        }
    };

    private boolean get(Sftp sftp, String src, String dst) {
        try {
            sftp.getClient().get(src, dst, new ProgressMonitor());
            if (debug) {
                System.out.printf("get %s to %s success\n", src, dst);
            }
            return true;
        } catch (Exception e) {
            System.out.printf("get %s to %s failed\n", src, dst);
            return false;
        }
    }

    private boolean put(Sftp sftp, String src, String dst) {
        try {
            sftp.put(src, dst, new ProgressMonitor(), Mode.OVERWRITE);
            if (debug) {
                System.out.printf("put %s to %s success\n", src, dst);
            }
            return true;
        } catch (Exception e) {
            System.out.printf("put %s to %s failed\n", src, dst);
            return false;
        }
    }

    private void downloadSync(Sftp sftp, String path, String target) {
        Map<String, LsEntry> entries = sftp.lsEntries(path, entryFilter).stream()
                .collect(Collectors.toMap(LsEntry::getFilename, Function.identity()));
        File dir = new File(target);
        if (!dir.exists()) {
            if (debug) {
                System.out.printf("mkdir %s\n", target);
            }
            if (!syncTest) {
                dir.mkdirs();
            }
        }
        File[] listFiles = dir.listFiles(fileFilter);
        Map<String, File> files = listFiles == null ? Collections.emptyMap()
                : Arrays.stream(listFiles).collect(Collectors.toMap(File::getName, Function.identity()));
        Collection<String> names = CollUtil.union(entries.keySet(), files.keySet());
        for (String name : names) {
            File file = files.get(name);
            LsEntry entry = entries.get(name);
            if (entry == null) {
                if (debug || syncTest) {
                    if (file.isDirectory()) {
                        System.out.printf("skip rmdir %s/%s\n", target, name);
                    } else {
                        System.out.printf("sync rm %s/%s\n", target, name);
                    }
                }
                if (!syncTest) {
                    if (file.isFile()) {
                        FileUtil.del(file);
                    }
                }
            } else {
                if (entry.getAttrs().isDir()) {
                    File subDir = new File(dir, name);
                    if (subDir.exists() == false) {
                        if (debug) {
                            System.out.printf("mkdir %s\n", name);
                        }
                        if (!syncTest) {
                            subDir.mkdirs();
                        }
                    }
                    downloadSync(sftp, path + "/" + name, dir + "/" + name);
                } else {
                    if (file == null) {
                        if (debug || syncTest) {
                            System.out.printf("sync add %s to %s\n", name, target);
                        }
                        if (!syncTest) {
                            get(sftp, path + "/" + name, target + "/" + name);
                        }
                    } else {
                        boolean change = syncTime ? file.lastModified() / 1000 < entry.getAttrs().getMTime()
                                : file.length() != entry.getAttrs().getSize();
                        if (change) {
                            if (debug || syncTest) {
                                System.out.printf("sync update %s to %s\n", name, target);
                            }
                            if (!syncTest) {
                                get(sftp, path + "/" + name, target + "/" + name);
                            }
                        } else {
                            if (debug) {
                                System.out.printf("sync no change %s in %s\n", name, target);
                            }
                        }
                    }
                }
            }
        }
    }

    private void download(Sftp sftp, String path, String target) {
        List<LsEntry> lsEntries = sftp.lsEntries(path);
        boolean isFile = lsEntries != null && lsEntries.size() == 1 && path.endsWith(lsEntries.get(0).getFilename());
        if (isFile) {
            String dest = (new File(target, FileNameUtil.getName(path))).getAbsolutePath();
            get(sftp, path, dest);
        } else {
            File dir = new File(target, FileNameUtil.getName(path));
            if (!dir.exists()) {
                if (debug) {
                    System.out.printf("mkdir %s\n", dir.getAbsolutePath());
                }
                dir.mkdirs();
            }
            lsEntries.forEach(entry -> {
                if (entry.getAttrs().isDir()) {
                    download(sftp, entry.getLongname(), dir.getAbsolutePath() + "/" + entry.getFilename());
                } else {
                    String dest = (new File(dir, FileNameUtil.getName(entry.getFilename()))).getAbsolutePath();
                    get(sftp, path + "/" + entry.getFilename(), dest);
                }
            });
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
            if (!file.exists()) {
                System.out.printf("source %s not exist\n", source);
                return;
            }
        }
        boolean identity = false;
        if (identityFile != null && !identityFile.isEmpty()) {
            File file = new File(identityFile);
            if (!file.exists() || !file.isFile()) {
                System.out.printf("identity_file %s not exist or not a file\n", identityFile);
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
                if (new File(source).isDirectory() && sync) {
                    uploadSync(sftp, source, sshTarget);
                } else {
                    upload(sftp, source, sshTarget);
                }
            }
            System.out.printf("scp succeeded\n");
        } catch (Exception e) {
            System.out.printf("scp failed: %s\n", e.getMessage());
        }
    }

    private void uploadSync(Sftp sftp, String source, String sshTarget) {
        File dir = new File(source);
        File[] listFiles = dir.listFiles(fileFilter);
        Map<String, LsEntry> entries = sftp.lsEntries(sshTarget, entryFilter).stream()
                .collect(Collectors.toMap(LsEntry::getFilename, Function.identity()));
        Map<String, File> files = listFiles == null ? Collections.emptyMap()
                : Arrays.stream(listFiles).collect(Collectors.toMap(File::getName, Function.identity()));
        Collection<String> names = CollUtil.union(entries.keySet(), files.keySet());
        for (String name : names) {
            File file = files.get(name);
            LsEntry entry = entries.get(name);
            if (file == null) {
                if (debug || syncTest) {
                    if (entry.getAttrs().isDir()) {
                        System.out.printf("skip rmdir %s/%s\n", sshTarget, name);
                    } else {
                        System.out.printf("sync rm %s/%s\n", sshTarget, name);
                    }
                }
                if (!syncTest) {
                    if (!entry.getAttrs().isDir()) {
                        sftp.delFile(sshTarget + "/" + name);
                    }
                }
            } else {
                if (file.isDirectory()) {
                    if (!sftp.exist(sshTarget + "/" + name)) {
                        if (debug) {
                            System.out.printf("sync mkdir %s\n", name);
                        }
                        if (!syncTest) {
                            sftp.mkdir(sshTarget + "/" + name);
                        }
                    }
                    uploadSync(sftp, file.getAbsolutePath(), sshTarget + "/" + name);
                } else {
                    if (entry == null) {
                        if (debug || syncTest) {
                            System.out.printf("sync add %s to %s\n", name, sshTarget);
                        }
                        if (!syncTest) {
                            put(sftp, file.getAbsolutePath(), sshTarget);
                        }
                    } else {
                        boolean change = syncTime ? file.lastModified() / 1000 > entry.getAttrs().getMTime()
                                : file.length() != entry.getAttrs().getSize();
                        if (change) {
                            if (debug || syncTest) {
                                System.out.printf("sync update %s to %s\n", name, sshTarget);
                            }
                            if (!syncTest) {
                                put(sftp, file.getAbsolutePath(), sshTarget);
                            }
                        } else {
                            if (debug) {
                                System.out.printf("sync no change %s in %s\n", name, sshTarget);
                            }
                        }
                    }
                }
            }
        }
    }

    private void upload(Sftp sftp, String source, String sshTarget) {
        File file = new File(source);
        if (file.isFile()) {
            put(sftp, source, sshTarget);
        } else {
            File[] listFiles = file.listFiles();
            if (listFiles != null && listFiles.length > 0) {
                sshTarget = sshTarget + "/" + file.getName();
                if (!sftp.exist(sshTarget)) {
                    if (debug) {
                        System.out.printf("mkdir %s\n", file.getName());
                    }
                    sftp.mkdir(sshTarget);
                }
                for (File listFile : listFiles) {
                    upload(sftp, listFile.getAbsolutePath(), sshTarget);
                }
            }
        }
    }

    class ProgressMonitor implements SftpProgressMonitor {
        private long max = 0, count = 0;
        private String src, dest;

        @Override
        public void init(int op, String src, String dest, long max) {
            this.src = src;
            this.dest = dest;
            this.max = max;
        }

        @Override
        public boolean count(long count) {
            this.count += count;
            if (debug || RandomUtil.randomBoolean()) {
                System.out.printf("%s ==> %s == %s/%s = %s%%\n", this.src, this.dest, this.count, this.max,
                        this.count * 100 / this.max);
            }
            return true;
        }

        @Override
        public void end() {

        }

    }
}
