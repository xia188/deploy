usage(){
  echo "Usage: deploy.sh service [command]"
  echo "    service = light4j | logserver | search"
  echo "    command = start | stop | status | restart | rebuild | redeploy"
  exit 0
}
command="$2"
light4j(){
    cd /soft/gitdata/light4j
    git pull
    sh start.sh $command
}
logserver(){
    cd /soft/gitdata/logserver
    git pull
    sh start.sh $command
}
search(){
    cd /soft/gitdata/light-search
    git pull
    sh start.sh $command
}
if [ $# -eq 0 ]; then 
    usage
else
	case $1 in
	light4j) light4j ;;
	logserver) logserver ;;
	search) search ;;
	*) usage ;;
	esac
fi
