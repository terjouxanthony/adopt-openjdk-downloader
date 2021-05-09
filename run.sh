#!/bin/bash

MAVEN_OPTS="-Xmx30m" mvn -q compile exec:java -Dexec.args="hello world"
