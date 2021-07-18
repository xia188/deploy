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
        echo "$service do deploy"
        # sh deploys.sh $service
    else
        echo "$service no change"
    fi

done
