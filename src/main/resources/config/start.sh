if [ $# -ne 2 ]; then
  echo "Usage: start.sh service namespace"
  exit 0
fi

source /etc/profile

service=$1
namespace=$2
nacos=10.7.128.11:8848,10.7.128.12:8848,10.7.128.13:8848
if [ "$namespace" == "pro" ]; then
  nacos=10.9.176.38:8848,10.9.176.39:8848,10.9.176.40:8848
fi

jar=cmp_${service}/cmp_${service}.jar
logback=/home/tomcat/ftpData/log/logback.xml

cp="cmp_${service}/cmp_${service}.jar"
for item in `ls cmp_${service}/*.jar`; do
  if [ -z $cp ]; then
    cp=$item
  else
    cp="$cp:$item"
  fi
done

PID=`ps -ef|grep cmp_${service}.jar|grep -v 'grep'|awk '{print $2}'`

if [ -z "$PID" ]
then
  echo "$service not running"
else
  echo "killing $service $PID"
  kill -9 $PID
fi

sleep 1

JVM_OPS="-server -Djava.awt.headless=true"
JVM_OPS="$JVM_OPS -Dcmp.service=${service} -Dlogging.config=${logback}"
JVM_OPS="$JVM_OPS -Dspring.cloud.nacos.config.server-addr=${nacos} -Dspring.cloud.nacos.config.namespace=service-namespace-${namespace} -Dspring.cloud.nacos.discovery.server-addr=${nacos} -Dspring.cloud.nacos.discovery.namespace=service-namespace-${namespace} -Dspring.profiles.active=${namespace}"

nohup java $JVM_OPS -cp $cp com.sinosoft.app.Application &>> /dev/null &
echo "starting ..."
