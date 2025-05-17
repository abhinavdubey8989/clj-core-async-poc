# aim : scrape avg-CPU-load & send it to statsd ,
#     : breaking it down into various categories: last 1 min , last 5 min , last 15 min
# sample usage : "./<this-script-name>.sh &"
#              : "&" sign at the end will run this script in background
# this script is to be run as a cron
# however due to systemd restrictions in docker containers , we are running it as infinite for loop

# Source the environment file
source env_file.env

# statsd config
STATSD_HOST=$STATSD_HOST
STATSD_PORT=$STATSD_PORT

# metric prefix
METRIC_PREFIX=$METRIC_PREFIX

# sleep b/w 2 iteration of while loop
SLEEP_TIME=$SLEEP_INTERVAL_IN_SEC

send_metrics_to_statsd() {
    local counter=$1
    local metric_suffix=$2
    local final_metric="$METRIC_PREFIX.$metric_suffix"

    # print to console
    echo "$final_metric = $counter"

    # send metric command
    # echo "$final_metric:$counter|c" | nc -w 1 -u $STATSD_HOST $STATSD_PORT
}

get_cores() {
    cpu_core_count=$(grep -c ^processor /proc/cpuinfo)
    echo "$cpu_core_count"
}

emit_load_avg_metrics() {
    local cores=$1

    # get 1, 5, 15 min load avg
    read load1 load5 load15 <<<$(uptime | awk -F'load average: ' '{print $2}' | tr -d ',')

    # divide by number of cores
    norm1=$(echo "scale=2; $load1 / $cores" | bc)
    norm5=$(echo "scale=2; $load5 / $cores" | bc)
    norm15=$(echo "scale=2; $load15 / $cores" | bc)

    send_metrics_to_statsd $norm1 "load_avg.one"
    send_metrics_to_statsd $norm5 "load_avg.five"
    send_metrics_to_statsd $norm15 "load_avg.fifteen"
}

emit_cpu_metrics() {
    # Read 'cpu' line, remove 'cpu' label
    cpu_values=($(grep '^cpu ' /proc/stat | cut -d ' ' -f2-))

    # Known labels (up to 10 fields)
    labels=(user nice system idle iowait irq softirq steal guest guest_nice)

    # Compute total
    total=0
    for val in "${cpu_values[@]}"; do
        total=$((total + val))
    done

    # Print label=value format
    for i in "${!cpu_values[@]}"; do
        label=${labels[$i]:-field_$i} # fallback label if needed
        percent=$(echo "scale=2; ${cpu_values[$i]} * 100 / $total" | bc)
        send_metrics_to_statsd $percent "cpu.$label"
    done
}

main() {

    cpu_core_count=$(get_cores)

    # send metric forever
    while true; do

        emit_load_avg_metrics $cpu_core_count
        emit_cpu_metrics

        # sleep
        sleep $SLEEP_TIME
    done
}

# call main method
main
