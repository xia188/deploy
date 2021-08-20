#!/bin/sh

daemon=false
appname=deploy
jarfile=target/$appname.jar
[ ! -e "$jarfile" ] && jarfile=$appname.jar
Survivor=1 Old=8 NewSize=$[Survivor*10] Xmx=$[NewSize+Old] #NewSize=Survivor*(1+1+8) Xmx=NewSize+Old
JVM_OPS="-Xmx${Xmx}m -Xms${Xmx}m -XX:NewSize=${NewSize}m -XX:MaxNewSize=${NewSize}m -XX:SurvivorRatio=8 -Xss228k"
JVM_OPS="$JVM_OPS -Djava.compiler=none"
ARGS="$ARGS --web --port 9881"
ARGS="$ARGS --lp.host none"
# ARGS="$ARGS --lp.host https://deploy.xlongwei.com --lp key deploy"
#JVM_OPS="$JVM_OPS -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n"
#ENV_OPS="$ENV_OPS PATH=/usr/java/jdk1.8.0_161/bin:$PATH"

usage(){
    echo "Usage: jcron.sh ( commands ... )"
    echo "commands: "
    echo "  status      check the running status"
    echo "  start       start $appname"
    echo "  stop        stop $appname"
    echo "  restart     stop && start"
    echo "  clean       clean target"
    echo "  jar         build $appname.jar"
    echo "  jars        copy dependencies to target"
    echo "  package     build $appname.jar and copy dependencies to target"
    echo "  rebuild     stop && build && start"
    echo "  refresh     stop && clean && build && jars && start"
    echo "  deploy      package all to one-fat $jarfile"
    echo "  redeploy    package all to one-fat $jarfile and restart"
}

status(){
    PIDS=`ps -ef | grep java | grep "$jarfile" |awk '{print $2}'`

	if [ -z "$PIDS" ]; then
	    echo "$appname is not running!"
	else
		for PID in $PIDS ; do
		    echo "$appname has pid: $PID!"
		done
	fi
}

stop(){
    PIDS=`ps -ef | grep java | grep "$jarfile" |awk '{print $2}'`

	if [ -z "$PIDS" ]; then
	    echo "$appname is not running!"
	else
		echo -e "Stopping $appname ..."
		for PID in $PIDS ; do
			echo -e "kill $PID"
		    kill $PID > /dev/null 2>&1
		done
	fi
}

wait(){
	PIDS=`ps -ef | grep java | grep "$jarfile" |awk '{print $2}'`

	if [ ! -z "$PIDS" ]; then
		COUNT=0 WAIT=9
		while [ $COUNT -lt $WAIT ]; do
			echo -e ".\c"
			sleep 1
			PIDS=`ps -ef | grep java | grep "$jarfile" |awk '{print $2}'`
			if [ -z "$PIDS" ]; then
				break
			fi
			let COUNT=COUNT+1
		done
		PIDS=`ps -ef | grep java | grep "$jarfile" |awk '{print $2}'`
		if [ ! -z "$PIDS" ]; then
			for PID in $PIDS ; do
				echo -e "kill -9 $PID"
				kill -9 $PID > /dev/null 2>&1
			done
		fi
	fi
}

clean(){
	mvn clean
}

jar(){
	mvn compile jar:jar
}

dependency(){
	mvn dependency:copy-dependencies -DoutputDirectory=target
}

deploy(){
	mvn package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
}

start(){
	echo "starting $appname ..."
	JVM_OPS="-server -Djava.awt.headless=true $JVM_OPS"
	if [ "$daemon" = "true" ]; then
        env $ENV_OPS setsid java $JVM_OPS -jar $jarfile $ARGS >> /dev/null 2>&1 &
	else
        env $ENV_OPS java $JVM_OPS -jar $jarfile $ARGS 2>&1
	fi
}

if [ $# -eq 0 ]; then 
    usage
else
	case $1 in
	status) status ;;
	start) start ;;
	stop) stop ;;
	restart) stop && wait && start ;;
	clean) clean ;;
	jar) jar ;;
	jars) dependency ;;
	package) jar && dependency ;;
	rebuild) stop && jar && start ;;
	refresh) stop && clean && jar && dependency && start ;;
	deploy) deploy ;;
	redeploy) stop && deploy && start ;;
	*) usage ;;
	esac
fi
