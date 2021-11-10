if [ $# -ne 3 ]; then
  echo "Usage: restart.sh namespace service ip"
  exit 0
fi
service=$2
namespace=$1
ip=$3
if [ ${#ip} -le 3 ]; then
  ip=10.7.128.$ip
fi
echo "restart ${service} ${namespace} ${ip}"
[ ! -e "~/.bashrc" ] && source ~/.bashrc
jssh tomcat@${ip} "cd /home/tomcat/code;cp ../ftpData/test/${namespace}/cmp_${service}.jar .;sh ${service}.sh ${service} ${namespace}"