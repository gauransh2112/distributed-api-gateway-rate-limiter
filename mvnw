#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License me.
# ----------------------------------------------------------------------------

# OS specific support.  $var _must_ be set to true or false.
cygwin=false;
darwin=false;
mingw=false
case "`uname`" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true ;;
  MINGW*) mingw=true ;;
esac

## find the directory where the script is located
PRG="$0"
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
SAVED=`pwd`
CDPATH=
cd "`dirname "$PRG"`/" >/dev/null
MAVEN_PROJECTBASEDIR=`pwd`
cd "$SAVED" >/dev/null

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ -n "$JAVA_HOME" ] ; then
  if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
else
  JAVACMD="java"
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly." >&2
  echo "  We cannot execute $JAVACMD" >&2
  exit 1
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Could not find $WRAPPER_JAR, attempting to download..." >&2
  if [ -f "$WRAPPER_PROPERTIES" ]; then
    DOWNLOAD_URL=$(grep "^wrapperUrl=" "$WRAPPER_PROPERTIES" | cut -d'=' -f2)
  fi
  if [ -z "$DOWNLOAD_URL" ]; then
    DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
  fi
  mkdir -p "$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
  if command -v wget >/dev/null 2>&1; then
    wget -O "$WRAPPER_JAR" "$DOWNLOAD_URL"
  elif command -v curl >/dev/null 2>&1; then
    curl -o "$WRAPPER_JAR" "$DOWNLOAD_URL"
  else
    echo "Error: Could not find wget or curl to download $WRAPPER_JAR" >&2
    exit 1
  fi
fi

exec "$JAVACMD" \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"
