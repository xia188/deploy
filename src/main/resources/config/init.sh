if [ $# -ne 2 ]; then
  echo "Usage: init.sh service ip"
  exit 0
fi
service=$1
ip=$2
if [ ${#ip} -le 3 ]; then
  ip=10.7.128.$ip
fi
echo "init $service $ip"
cd cmp_${service}
mvn clean compile resources:resources jar:jar
mvn dependency:copy-dependencies -DoutputDirectory=target
[ -e "./env.sh" ] && source ./env.sh
[ -e "~/.bashrc" ] && source ~/.bashrc
rm -f target.tgz && tar zcvf target.tgz target
jssh tomcat@${ip} "cd /home/tomcat/code;rm -rf cmp_${service};rm -f cmp_${service}.tgz"
jscp target.tgz ../start.sh tomcat@${ip}:/home/tomcat/code
jssh tomcat@${ip} "cd /home/tomcat/code;tar zxvf target.tgz;mv target cmp_${service};mv target.tgz cmp_${service}.tgz;mv start.sh ${service}.sh"
# rm -f target.tgz
cd ..
