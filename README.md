# deploy

#### 介绍
支持Windows环境使用shell脚本配置密码自动部署

#### 适用场景
无法使用expect、sshpass等命令，不方便配置id_rsa免密登录时。
支持指定多台主机批量部署，支持每5分钟检查更新并自动部署。

#### 使用说明
```
cd /d/workspace/parent #进入项目父目录
curl https://t.xlongwei.com/windows/deploy.jar
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
