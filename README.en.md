# deploy

#### Description
windows git-bash auto deploy tool

#### Instructions
```
cd /d/workspace/parent
curl https://t.xlongwei.com/windows/deploy.jar -o deploy.jar
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
