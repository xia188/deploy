### deploy
支持通过shell脚本自动部署项目

#### 使用说明

- 编译打包：sh jcron.sh deploy
- 启动项目：sh jcron.sh start，支持定时执行auto.sh自动部署
- 配置别名：vi ~/.bashrc，jscp支持上传文件，jssh支持远程执行

>alias jssh='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Ssh'

>alias jscp='java -cp ${SSHDEPLOY:-.}/deploy.jar com.xlongwei.deploy.Scp'

#### 手动部署

- sh init.sh order 24，初始化部署
- sh deploy.sh dev order 24，增量部署
- sh deploys.sh order，批量部署

#### 自动部署

- vi auto.sh，配置哪些服务需要自动部署
- vi deploys.sh，配置各服务需要部署到哪些namespace和ip
- vi sonar.sh，每天执行一次代码检查，需搭一个sonarqube服务

#### 远程部署
- jcron --lp.host=http://115.28.229.158:9881/ --lp.key=deploy，lp.key是密钥不能泄露
- 访问[lp.host](http://115.28.229.158:9881/)，key密钥，deploy部署服务（内网执行deploy.sh），deploys批量部署（内网执行deploys.sh）
