#!/bin/bash

# This script ingests and filters content from files matching file extensions in a
# target directory (recursively traversing sub-directories) and outputs a file that contains all matching content
# The output is suitable for consumption by an LLM with a sufficiently large context window (e.g., Google AI Studio).
# @author Chris Phillipson
# @see https://gitingest.com

# Check if required arguments are provided
if [ $# -lt 2 ]; then
    echo "Usage: $0 <directory> <extensions>"
    echo "Example: $0 /path/to/dir '.java,.xml,.gradle'"
    exit 1
fi

directory="$1"
IFS=',' read -ra extensions <<< "$2"

# Check if directory exists
if [ ! -d "$directory" ]; then
    echo "Error: Directory '$directory' not found"
    exit 1
fi

# Create temporary file for output
output_file="ingested_$(date +%Y%m%d_%H%M%S).txt"

# Process each file extension
for ext in "${extensions[@]}"; do
    # Remove leading/trailing whitespace and ensure dot prefix
    ext=$(echo "$ext" | sed 's/^[[:space:]]*\*\?//' | sed 's/^[[:space:]]*\./\./')

    # Find files with the current extension
    while IFS= read -r -d '' file; do
        # Get absolute path
        abs_path=$(realpath "$file")
        dir_path=$(dirname "$abs_path")

        echo "filename: $(basename "$file")" >> "$output_file"
        echo "path: $dir_path" >> "$output_file"
        echo "contents-start: |" >> "$output_file"
        cat "$file" >> "$output_file"
        echo "contents-end" >> "$output_file"
        echo -e "\n\n\n" >> "$output_file"
    done < <(find "$directory" -type f -name "*$ext" -print0)
done

echo "Output saved to: $output_file"