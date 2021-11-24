usage() {
    echo "Usage: deploys.sh service"
    exit 0
}
if [ $# -ne 1 ]; then
    usage
fi
service=$1

case $service in
lifechange) namespaceIps=(
    dev=25
    dat=28
) ;;
logservice) namespaceIps=(
    dev=23
    dat=28
) ;;
order) namespaceIps=(
    dev=24
    dat=29
    dat=30
) ;;
person) namespaceIps=(
    dev=25
    dat=28
) ;;
*) usage ;;
esac

echo `date +"%F %T"`
echo "deploys ${service}"
cd cmp_${service}
svn cleanup
lines=`svn up|wc -l`
# 1，最小包，复制到ip，重启即可
mvn compile resources:resources jar:jar
#,2，统一完整包，复制到nas，其他ip复制文件并重启即可，需在启动脚本配置nacos系统属性
# mvn package -Dmaven.test.skip=true
# jscp target/cmp_${service}.jar tomcat@10.7.128.28:/home/tomcat/ftpData/test
if [ $lines -gt 2 ] ; then
  svn up -r PREV
fi
# declare -A namespaces
[ -e "./env.sh" ] && source ./env.sh
[ -e "~/.bashrc" ] && source ~/.bashrc
for ((i = 0; i < ${#namespaceIps[@]}; ++i)); do

    namespaceIp=${namespaceIps[i]}
    namespace=$(echo ${namespaceIp} | cut -d '=' -f 1)
    ip=$(echo ${namespaceIp} | cut -d '=' -f 2)
    if [ ${#ip} -le 3 ]; then
        ip=10.7.128.$ip
    fi
    echo "deploy ${service} ${namespace} ${ip}"
    # sh nacos.sh offline ${namespace} ${service} ${ip}
    # 3，各环境独立完整包，上传到nas/${namespace}子目录，然后复制并重启即可，nacos系统属性统一配置到bootstrap.properties
    # if [ -z "${namespaces[${namespace}]}" ]; then
    #     echo "package namespace=${namespace} service=${service}"
    #     sed s/\${namespace}/${namespace}/g ../bootstrap.properties|sed s/\${service}/${service}/g >src/main/resources/bootstrap.properties
    #     mvn package -Dmaven.test.skip=true
    #     namespaces["${namespace}"]="true"
    #     jscp target/cmp_${service}.jar tomcat@${ip}:/home/tomcat/ftpData/test/${namespace}
    # fi
    jscp target/cmp_${service}.jar tomcat@${ip}:/home/tomcat/code/cmp_${service}
    jssh tomcat@${ip} "cd /home/tomcat/code;sh ${service}.sh ${service} ${namespace}"
    # jssh tomcat@${ip} "cd /home/tomcat/code;cp ../ftpData/test/${namespace}/cmp_${service}.jar .;sh ${service}_start.sh"

done
cd ..
echo `date +"%F %T"`