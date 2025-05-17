

# this script will start/stop the component basis START_OR_STOP_FLAG 
#       - (0-> stop , 1-> start) , 
#       - this is the 1st arg
#       - any other value of this flag is invalid
# to start in attach or detach mode , pass ATTACH_MODE_FLAG
#       - (a-> attach , d-> detach) , 
#       - this is the 2nd arg
#       - any other value of this flag is invalid
# sample usage : 
#       - "./start_or_stop 1 a"  (will start in attach mode)
#       - "./start_or_stop 1" or "./start_or_stop 1 d"     (will start in detach mode)
#       - "./start_or_stop 0"     (will stop the container)



stop() {
    sudo docker compose down
    docker container prune -f
    echo "stopped $PWD ..."
}





start_detach() {
    echo "detach mode"
    sudo docker compose up -d
}


start_attach() {
    start_detach
    # echo "attach mode"
    # sudo docker compose up
}




START_OR_STOP_FLAG=$1
ATTACH_MODE_FLAG=$2

if [ -z "$START_OR_STOP_FLAG" ] ; then
    echo "Invalid value of start/stop flag"
    exit 0
elif [ "$START_OR_STOP_FLAG" = "0" ]; then
    stop
    exit 0
elif [ "$START_OR_STOP_FLAG" != "1" ]; then
    echo "Invalid value of start/stop flag"
    exit 0
fi


stop
if [ -z "$ATTACH_MODE_FLAG" ] || [ "$ATTACH_MODE_FLAG" = "d" ]; then
    start_detach
elif [ "$ATTACH_MODE_FLAG" = "a" ]; then
    start_attach
else
    echo "Invalid value of flag"
fi
