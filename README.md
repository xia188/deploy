# deploy

#### 介绍
支持Windows环境使用shell脚本配置密码自动部署

#### 适用场景
无法使用expect、sshpass等命令，不方便配置id_rsa免密登录时。
支持指定多台主机批量部署，支持每5分钟检查更新并自动部署。

#### 使用说明
```
cd /d/workspace/parent #进入项目父目录
curl http://115.28.229.158/tool/windows/deploy.jar -o deploy.jar
jar xvf ${SSHDEPLOY:-.}/deploy.jar config/
cp config/* ./ && rm -rf config/
vi ~/.bashrc
alias jssh='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Ssh'
alias jscp='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Scp'
alias jcron='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Cron'
source ~/.bashrc && jscp -h
export SSHPASS=passwd
jcron
```

#### 手动部署
```
sh init.sh order 24
sh deploy.sh dev order 24
//如果是发布全量包，则从nas目录复制并重启，否则再次使用deploy.sh脚本也很快
sh restart.sh dev order 25
//批量发布支持多环境，多主机，auto.sh可以自动检查代码更新并自动发布应用
sh deploys.sh order
//修改pom.xml后，同步目录，sh sync.sh order 25
mvn clean compile resources:resources jar:jar
mvn dependency:copy-dependencies -DoutputDirectory=target
jscp --sync target tomcat@10.7.128.28:/home/tomcat/code/cmp_order
```

#### 密码管理
```
jssh -c //复制 echo $SSHPASS
jscp -c -pw pro //复制 echo ${SSHPASS_pro:-pro}
//使用pro密码同步文件目录
jscp -pw pro --sync target tomcat@10.9.176.54:/home/tomcat/code/cmp_order
//没有配置$SSHPASS_propass时，直接使用propass远程执行命令
jssh -pw propass tomcat@10.9.176.54 "cd /home/tomcat/code;sh start.sh order"
```

#### 自动部署
```
配置SSHPASS，修改init.sh、deploy.sh之后，手动部署成功，即可jcron启动自动部署
修改auto.sh，可以配置哪些服务需要自动部署
修改deploys.sh，可以配置各个服务需要部署到哪些namespace和ip
修改start.sh，可以配置统一日志logback.xml，nacos地址，jvm参数
sonar.sh，支持每天执行一次代码检查，搭一个sonarqube服务即可
```

#### 远程操作
```
//外网部署，或直接使用http://115.28.229.158:9881/
jcron --web
//内网部署：key是密钥，避免泄露
jcron --lp.host=http://115.28.229.158:9881/ --lp.key=xlongwei
```
[架构设计](http://115.28.229.158/tool/images/deploy/deploy.png)

#### light-start支持
[light-start](http://115.28.229.158:9881/specui.html)
```
sh jcron.sh cli
```