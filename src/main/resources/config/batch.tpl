#!/bin/sh

source /etc/profile

nacos=10.7.128.11:8848,10.7.128.12:8848,10.7.128.13:8848
# dev sit uat vir pro
if [ "$namespace" == "pro" ]; then
  nacos=10.9.176.38:8848,10.9.176.39:8848,10.9.176.40:8848
fi

jar=cmp_${service}/cmp_${service}.jar
logback=log/logback.xml
mainClass=com.xlongwei.app.Application

cp="$jar"
# 完整包时上面就够了，最小包时还需要加上所有依赖jar
for item in `ls cmp_${service}/*.jar`; do
  if [ -z $cp ]; then
    cp=$item
  else
    cp="$cp:$item"
  fi
done

PID=`ps -ef|grep ${jar}|grep -v 'grep'|head -n 1|awk '{print $2}'`

if [ -z "$PID" ]
then
  echo "${service} not running"
else
  echo "killing ${service} $PID"
  kill $PID
  COUNT=0
  while [ $COUNT -lt 10 ]; do
      echo -e ".\c"
      sleep 1
      let COUNT+=1
      PID_EXIST=`ps -f -p $PID | grep -v PID`
      if [ -z "$PID_EXIST" ]; then
          break
      fi
  done
  if [ $COUNT -ge 10 ]; then
      PID_EXIST=`ps -f -p $PID | grep -v PID`
      if [ -n "$PID_EXIST" ]; then
          echo "kill -9 $PID"
          kill -9 $PID
      fi
  fi
fi

sleep 1

Survivor=8 Old=128 NewSize=$[Survivor*10] Xmx=$[NewSize+Old] #NewSize=Survivor*(1+1+8) Xmx=NewSize+Old
JVM_OPS="-server -Djava.awt.headless=true -Dspring.profiles.active=${namespace}"
# JVM_OPS="$JVM_OPS -Xmx${Xmx}m -Xms${Xmx}m -XX:NewSize=${NewSize}m -XX:MaxNewSize=${NewSize}m -XX:SurvivorRatio=8 -Xss228k"
# 全局配置日志比较好，改动之后可以所有应用快速生效，logback配置文件需放在nas目录，日志同时在本机和nas目录输出
# JVM_OPS="$JVM_OPS -Dcmp.service=${service} -Dip=${ip} -Dlogging.config=${logback}"
JVM_OPS="$JVM_OPS -Dspring.cloud.nacos.config.server-addr=${nacos} -Dspring.cloud.nacos.config.namespace=service-namespace-${namespace}"
JVM_OPS="$JVM_OPS -Dspring.cloud.nacos.discovery.server-addr=${nacos} -Dspring.cloud.nacos.discovery.namespace=service-namespace-${namespace}"
# JVM_OPS="$JVM_OPS -Dspring.cloud.nacos.username=${nacos_username} -Dspring.cloud.nacos.password=${nacos_password}"

echo "java $JVM_OPS -cp $cp ${mainClass}"
nohup java $JVM_OPS -cp $cp ${mainClass} &>> /dev/null &
echo "starting ..."
