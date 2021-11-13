#!/bin/sh

usage(){
    echo "Usage: batch.sh ( commands ... )"
    echo "commands: "
    echo "  namespaces                              batch start for namespaces"
    echo "  namespace {namespace} {ip}              batch start for namespace services"
    echo "  service {namespace} {ip} {service}      gen start for namespace ip service"
}
deploy="/home/tomcat/code" # 此目录放置cmp_{service}.jar {service}_start.sh，其实单独一个start.sh {service} {namespace}也够用了
shell="/nas/shell" # 此目录放置batch.sh batch.tpl，本机batch.sh namespaces可远程调用batch.sh namespace {namespace} {ip}批量生成启动脚本
ipPrefix="10.7.128." # 支持28=》10.7.128.28
namespace="$2"
ip="$3"
service="$4"
# 支持jssh jscp命令
[ ! -e "~/.bashrc" ] && source ~/.bashrc

namespaces=(
    dev=23,24
    dat=28,29
)

namespaces(){
    for ((i = 0; i < ${#namespaces[@]}; ++i)); do
        namespace=$(echo ${namespaces[i]} | cut -d '=' -f 1)
        ips=$(echo ${namespaces[i]} | cut -d '=' -f 2)
        echo "$namespace=$ips"
        ips=($(echo ${ips} | sed "s/,/\n/g"))
        for ip in ${ips[*]}; do
            if [ ${#ip} -le 3 ]; then
                ip="${ipPrefix}$ip"
            fi
            echo "jssh tomcat@$ip \"cd /nas/shell; sh batch.sh namespace $namespace $ip\""
            # jssh 远程执行batch.sh namespace {namespace} {ip}，以下为本地测试
            sh batch.sh namespace $namespace $ip
        done
    done
}

namespace(){
    # check namespace
    if [ -z ${namespace} ]; then
        echo "namespace=dev|dat|uat|vir"
        exit
    fi
    echo "namespace=$namespace"
    # check ip
    if [ -z ${ip} ]; then
        echo "ip=28|${ipPrefix}30"
        exit
    fi
    if [ ${#ip} -le 3 ]; then
        ip="${ipPrefix}$ip"
    fi
    echo "ip=${ip}"
    # 本地测试可创建目录mkdir -p /home/tomcat/code，并复制两个cmp_{service}.jar过来
    for file in `ls ${deploy}/cmp_*.jar`; do
        service=`echo ${file/*_}|cut -d '.' -f 1`
        echo "batch.sh service $namespace $ip $service"
        sh batch.sh service $namespace $ip $service
    done
}

service(){
    if [ -z ${namespace} ]; then
        echo "namespace=dev|dat|uat|vir"
        exit
    elif [ -z $ip ]; then
        echo "ip=28|${ipPrefix}30"
        exit
    elif [ -z $service ]; then
        echo "service=order|person"
        exit
    fi
    echo "$namespace $ip $service"
    sed s/\${namespace}/${namespace}/g batch.tpl | sed s/\${ip}/${ip}/g | sed s/\${service}/${service}/g > ${deploy}/${service}_start.sh
}

if [ $# -eq 0 ]; then 
    usage
else
	case $1 in
	namespaces) namespaces ;;
	namespace) namespace ;;
	service) service ;;
	*) usage ;;
	esac
fi