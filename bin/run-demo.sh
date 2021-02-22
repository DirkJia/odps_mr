#!/bin/sh

RUN_CLASS=com.fiberhome.odps.Task

#colour level
SETCOLOR_SUCCESS="echo -en \\033[1;32m"
SETCOLOR_FAILURE="echo -en \\033[1;31m"
SETCOLOR_NORMAL="echo -en \\033[0;39m"
SETCOLOR_WARN="echo -en \\033[1;31m"

#write success log
function LogSucMsg()
{
        time=SUCCESS[`date "+%Y-%m-%d %H:%M:%S"`]
        $SETCOLOR_SUCCESS
        echo "$time $*"
        $SETCOLOR_NORMAL
}

#write error log
function LogErrorMsg()
{
        time=ERROR[`date "+%Y-%m-%d %H:%M:%S"`]
        $SETCOLOR_FAILURE
        echo "$time $*"
        $SETCOLOR_NORMAL
}
#write warn log
function LogWarnMsg()
{
	time=warn[`date "+%Y-%m-%d %H:%M:%S"`]
        $SETCOLOR_WARN
        echo "$time $*"
        $SETCOLOR_NORMAL
}
#write normal log
function LogNormal()
{
        $SETCOLOR_NORMAL
        echo "$*"
}

JVM_OPTION="-Xms1024m -Xmx1024m -XX:+HeapDumpOnOutOfMemoryError -Djava.net.preferIPv4Stack=true"

bin=`dirname $0`
cd $bin/..
APPPATH=`pwd`

APP_LIB=$APPPATH/lib/
CONFPATH=$APPPATH/conf/

LogNormal "配置"$CONFPATH

library=($APP_LIB)
for lib in ${library[*]}
do
    JARS=`find $lib -name "*.jar" 2>&1`
    for JAR in $JARS
    do
        LIBPATH=$LIBPATH:$JAR
    done
done


# main class name
#安装脚本会改
exec java $JVM_OPTION -cp "$LIBPATH:$CONFPATH" $RUN_CLASS  &
result=$?
LogNormal $result
exit 0
#exit 0