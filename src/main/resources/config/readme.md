# 建立命令别名
```
vi ~/.bashrc
alias jssh='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Ssh'
alias jscp='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Scp'
alias jcron='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Cron'
```
# 下载[deploy.jar](https://t.xlongwei.com/windows/deploy.jar)，配置环境变量，SSHPASS、SSHDEPLOY必须
```
export SSHPASS=passwd
export SSHDEPLOY=.
export SSHIDENTITY=identity_file
export SSHPORT=22
export SSHSHELL="sh auto.sh"
export SSHCRON="3 */5 * * * *"
```
# 提取脚本文件，建议在parent目录操作
```
jar xvf ${SSHDEPLOY:-.}/deploy.jar config/
cp config/* ./
rm -rf config/
```
# 测试命令jssh jscp
```
jssh -h
jscp -h
```
# 初始化，手动部署
```
sh init.sh order 24
sh deploy.sh dev order 24
sh deploys.sh order
```
# 修改pom.xml后，同步目录
```
mvn clean compile resources:resources jar:jar
mvn dependency:copy-dependencies -DoutputDirectory=target
jscp --sync target tomcat@10.7.128.28:/home/tomcat/code/cmp_order
```
# 自动部署
```
jcron
```
# 发布完整包时
```
不需要init初始化
可选计算bootstrap.properties（并注释application.properties里面的spring.cloud.nacos.discovery.namespace），更建议启动脚本提供此类配置
deploys.sh注释部分支持不同namespace打不同的包，上传到公共的nas目录，然后远程执行脚本复制完整包并重启应用即可
```
