#!/bin/bash

echo "Stopping SwiftScan server..."

screen -S swiftscan -X quit

echo "Server stopped."
