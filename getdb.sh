#!/bin/bash
wget https://www.fuzzwork.co.uk/dump/sqlite-latest.sqlite.bz2
bzip2 -d sqlite-latest.sqlite.bz2
cat drops.sql | sqlite3 sqlite-latest.sqlite
