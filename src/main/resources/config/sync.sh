if [ $# -ne 2 ]; then
  echo "Usage: sync.sh service ip"
  exit 0
fi
service=$1
ip=$2
if [ ${#ip} -le 3 ]; then
  ip=10.7.128.$ip
fi
echo "sync $service $ip"
cd cmp_${service}
mvn clean compile resources:resources jar:jar
mvn dependency:copy-dependencies -DoutputDirectory=target
[ ! -e "~/.bashrc" ] && source ~/.bashrc
jscp --sync target tomcat@${ip}:/home/tomcat/code/cmp_${service}
cd ..
