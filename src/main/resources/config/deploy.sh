if [ $# -ne 3 ]; then
  echo "Usage: deploy.sh namespace service ip"
  exit 0
fi
service=$2
namespace=$1
ip=$3
if [ ${#ip} -le 3 ]; then
  ip=10.7.128.$ip
fi
echo "deploy ${service} ${namespace} ${ip}"
cd cmp_${service}
svn cleanup
lines=`svn up|wc -l`
mvn clean compile resources:resources jar:jar
if [ $lines -gt 2 ] ; then
  svn up -r PREV
fi
[ ! -e "~/.bashrc" ] && source ~/.bashrc
jscp target/cmp_${service}.jar tomcat@${ip}:/home/tomcat/code/cmp_${service}
jssh tomcat@${ip} "cd /home/tomcat/code;sh ${service}.sh ${service} ${namespace}"
cd ..
