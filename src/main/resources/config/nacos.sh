#!/bin/sh

host=http://localhost:8848
declare -A services=(["logservice"]="8030" ["order"]="8017" ["person"]="8025")
ipPrefix="10.7.128." # 支持28=》10.7.128.28
namespace="$2"
service="$3"
ip="$4"
port="" # 服务和端口通常是固定的，直接用services列出全部

usage(){
    echo "Usage: nacos.sh ( commands ... )"
    echo "commands: "
    echo "  namespaces                          list namespaces"
    echo "  services {namespace}                list namespace services"
    echo "  instances {namespace} {service}     list namespace service instances"
    echo "  online {namespace} {service} {ip}   online namespace service of ip"
    echo "  offline {namespace} {service} {ip}  offline namespace service of ip"
}

namespaces(){
    curl ${host}/nacos/v1/console/namespaces
}

namespace(){
    if [ -z ${namespace} ]; then
        echo "namespace=dev|dat|uat|vir|public"
        exit
    else
        if [ "${namespace}" == "public" ]; then
            namespace="";
        else
            namespace="service-namespace-${namespace}"
        fi
    fi
}

service(){
    namespace
    port=${services[$service]}
    if [ -z "${service}" -o -z "${port}" ]; then
        echo "service=${!services[@]}"
        exit
    else
        service="service-${service}"
    fi
}

ip(){
    service
    if [ -z ${ip} ]; then
        echo "ip=28|${ipPrefix}30"
        exit
    fi
    if [ ${#ip} -le 3 ]; then
        ip="${ipPrefix}$ip"
    fi
}

services(){
    namespace
    echo "namespace=${namespace}"
    curl ${host}/nacos/v1/ns/service/list?pageNo=1\&pageSize=100\&namespaceId=${namespace}
}

instances(){
    service
    echo "namespace=${namespace} service=${service}"
    curl ${host}/nacos/v1/ns/instance/list?namespaceId=${namespace}\&serviceName=${service}
}

instance(){
    ip
    echo "namespace=${namespace} service=${service} ip=${ip}:${port} enabled=${enabled}"
    curl -X PUT ${host}/nacos/v1/ns/instance -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" -d "serviceName=${service}&ip=${ip}&port=${port}&enabled=${enabled}&namespaceId=${namespace}"
}

online(){
    enabled=true
    instance
}

offline(){
    enabled=false
    instance
}

if [ $# -eq 0 ]; then 
    usage
else
	case $1 in
	namespaces) namespaces ;;
	services) services ;;
	instances) instances ;;
	online) online ;;
	offline) offline ;;
	*) usage ;;
	esac
fi
