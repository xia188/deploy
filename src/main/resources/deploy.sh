usage(){
  echo "Usage: deploy.sh service"
  echo "    service = light4j, logserver, search"
  exit 0
}
light4j(){
    cd /soft/gitdata/light4j
    git pull
    sh start.sh rebuild
}
logserver(){
    cd /soft/gitdata/logserver
    git pull
    sh start.sh rebuild
}
search(){
    cd /soft/gitdata/light-search
    git pull
    sh start.sh rebuild
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
