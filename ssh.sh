cp="target/classes"
mvn compile dependency:copy-dependencies -DoutputDirectory=target
for item in `ls target/*.jar`; do
  if [ -z $cp ]; then
    cp=$item
  else
    # windows=; linux=:
    cp="$cp;$item"
  fi
done
# echo $cp
java -cp $cp com.xlongwei.deploy.Ssh -h
# java -cp $cp com.xlongwei.deploy.Ssh tomcat@10.7.128.28 "ls -l"
# java -cp $cp com.xlongwei.deploy.Ssh --pw passwd
# env SSHPASS=passwd java -cp $cp com.xlongwei.deploy.Ssh
