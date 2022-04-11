if [ $# -lt 3 ]; then
  echo "Usage: deploy.sh namespace service ip [update.tgz]"
  exit 0
fi
service=$2
namespace=$1
ip=$3
update=$4
if [ ${#ip} -le 3 ]; then
  ip=10.7.128.$ip
fi
echo `date +"%F %T"`
echo "deploy ${service} ${namespace} ${ip} ${update}"
# sh nacos.sh offline ${namespace} ${service} ${ip}
[ -e "./env.sh" ] && source ./env.sh
[ -e "~/.bashrc" ] && source ~/.bashrc
if [ -z ${update} ]; then
cd cmp_${service}
svn cleanup
lines=`svn up|wc -l`
mvn compile resources:resources jar:jar
# sed s/\${namespace}/${namespace}/g ../bootstrap.properties|sed s/\${service}/${service}/g >src/main/resources/bootstrap.properties
# mvn package -Dmaven.test.skip=true
if [ $lines -gt 2 ] ; then
  svn up -r PREV
fi
jscp target/cmp_${service}.jar tomcat@${ip}:/home/tomcat/code/cmp_${service}
cd ..
else
echo "deploy with update ${update}"
mkdir BOOT-INF && cd BOOT-INF
tar zxvf ../update.tgz
cd .. && jar uvf cmp_${service}.jar BOOT-INF
sleep 1 && rm -rf BOOT-INF
fi
jssh tomcat@${ip} "cd /home/tomcat/code;sh ${service}.sh ${service} ${namespace}"
echo `date +"%F %T"`