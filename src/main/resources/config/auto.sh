services=(
    logservice
    order
    person
)
for ((i = 0; i < ${#services[@]}; ++i)); do

    service=${services[i]}
    lines=`svn up cmp_${service}|wc -l`
    # cd cmp_${service} && lines=`git pull origin master|wc -l` && cd ..
    if [ $lines -gt 2 ] ; then
        echo "`date +"%Y-%m-%d %H:%M.%S"` $service do deploy"
        # sh deploys.sh $service
        echo "`date +"%Y-%m-%d %H:%M.%S"` $service end deploy"
    else
        echo "`date +"%Y-%m-%d %H:%M.%S"` $service no change"
    fi

done
