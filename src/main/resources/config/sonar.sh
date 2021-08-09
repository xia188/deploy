services=(
    logservice
    order
    person
)
echo "`date +%F` check sonar"
for ((i = 0; i < ${#services[@]}; ++i)); do

    service=${services[i]}
    begin=`date +%F -d -1day`
    end=`date +%F`
    lines=`svn log cmp_${service} -r {$begin}:{$end}|wc -l`
    # cd cmp_${service} && lines=`git log --since={$begin}|wc -l`
    if [ $lines -gt 2 ] ; then
        echo "`date +%T` $service do sonar"
        mvn compile test-compile sonar:sonar -Dsonar.host.url=http://127.0.0.1:9000 -f cmp_${service}/pom.xml
        echo "`date +%T` $service end sonar"
    else
        echo "`date +%T` $service no change"
    fi

done
