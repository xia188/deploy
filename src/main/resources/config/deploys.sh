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
mvn compile resources:resources jar:jar
# sed s/\${namespace}/${namespace}/g ../bootstrap.properties|sed s/\${service}/${service}/g >src/main/resources/bootstrap.properties
# mvn package -Dmaven.test.skip=true
# jscp target/cmp_${service}.jar tomcat@${ip}:/home/tomcat/ftpData/test/${namespace}
if [ $lines -gt 2 ] ; then
  svn up -r PREV
fi
# declare -A namespaces
[ ! -e "~/.bashrc" ] && source ~/.bashrc
for ((i = 0; i < ${#namespaceIps[@]}; ++i)); do

    namespaceIp=${namespaceIps[i]}
    namespace=$(echo ${namespaceIp} | cut -d '=' -f 1)
    ip=$(echo ${namespaceIp} | cut -d '=' -f 2)
    if [ ${#ip} -le 3 ]; then
        ip=10.7.128.$ip
    fi
    echo "deploy ${service} ${namespace} ${ip}"
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