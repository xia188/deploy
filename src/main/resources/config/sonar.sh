services=(
    logservice
    order
    person
)
for ((i = 0; i < ${#services[@]}; ++i)); do

    service=${services[i]}
    echo "`date +"%Y-%m-%d %H:%M:%S"` $service do sonar"
    mvn compile test-compile sonar:sonar -Dsonar.host.url=http://127.0.0.1:9000 -f cmp_${service}/pom.xml
    echo "`date +"%Y-%m-%d %H:%M:%S"` $service end sonar"

done
