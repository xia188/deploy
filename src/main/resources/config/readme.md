# 建立命令别名
vi ~/.bashrc
alias jssh='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Ssh'
alias jscp='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Scp'
alias jcron='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Cron'

# 配置环境变量，后面4个默认即可
export SSHPASS=passwd
export SSHDEPLOY=.
export SSHIDENTITY=identity_file
export SSHPORT=22
export SSHSHELL=sh auto.sh
export SSHCRON=3 */5 * * * *

# 提取脚本文件，建议在parent目录操作
jar xvf ${SSHDEPLOY:-.}/deploy.jar config/
cp config/* ./
rm -rf config/

# 测试命令jssh jscp
jssh -h
jscp -h

# 编辑deploy.sh，打开jscp注释，测试效果

# 编辑auto.sh，打开sh deploys.sh注释，测试效果
jcron
