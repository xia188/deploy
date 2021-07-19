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
```
# 自动部署
```
jcron
```
