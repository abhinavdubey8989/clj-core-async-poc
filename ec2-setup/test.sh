#!/bin/bash

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
    label=${labels[$i]:-field_$i}  # fallback label if needed
    percent=$(echo "scale=2; ${cpu_values[$i]} * 100 / $total" | bc)
    echo "${label}=${percent}"
done