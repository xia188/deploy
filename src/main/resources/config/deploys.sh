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

cd cmp_${service}
mvn clean compile resources:resources jar:jar
[ ! -e "~/.bashrc" ] && source ~/.bashrc
for ((i = 0; i < ${#namespaceIps[@]}; ++i)); do

    namespaceIp=${namespaceIps[i]}
    namespace=$(echo ${namespaceIp} | cut -d '=' -f 1)
    ip=$(echo ${namespaceIp} | cut -d '=' -f 2)
    if [ ${#ip} -le 3 ]; then
        ip=10.7.128.$ip
    fi
    echo "deploy ${service} ${namespace} ${ip}"
    jscp target/cmp_${service}.jar tomcat@${ip}:/home/tomcat/code/cmp_${service}
    jssh tomcat@${ip} "cd /home/tomcat/code;sh ${service}.sh ${service} ${namespace}"

done
cd ..
