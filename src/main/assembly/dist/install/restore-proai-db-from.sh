#!/bin/sh

# Restores an PostGreSQL PrOAI database from one previously 
# exported with pg_dump (with the option -a for data only and -t for binary 
# format).   

sudo -u postgres pg_restore -U postgres -d proai_db -F t $1
